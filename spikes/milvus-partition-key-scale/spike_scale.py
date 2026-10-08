#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
P3-T0 · Milvus partition-key 万级规模 spike（SaaS Phase 3，承接 Phase 2 P2-T0 遗留 + 清单 S-2/S-3/T-2）。

背景：P2-T0（spikes/milvus-partition-key/spike.py）已在 10 行 toy 规模上证明 partition-key
等值过滤跨租户 0 泄漏 + LangChain4j JSON 谓词等价正确，但**toy 规模下 partition 剪枝无统计意义**
（§2 明示"partition 剪枝的收益要随分区数与数据量放大才显现"），并把万级复测推迟到本任务。

本脚本把规模放大到默认 10000 行 / 1024 维 / 3 租户，实测三条路径的检索时延分布，为 Phase 3
清单三项决策给实证：

  S-3 万级时延阈值 P95 —— partition-key 剪枝相对普通标量全扫是否有实质收益，据此定 P95 门禁阈值；
  S-2 现网集合迁移策略 —— 复刻 LangChain4j `metadata` JSON 单列模型（现网 knowledge_chunks），
        对比 partition-key 集合，评估"双写 / 停机重灌 / 延后"三条迁移路径的成本；
  T-2 partition-key schema 与 io.milvus SDK 兼容 —— 用 SDK 原生 collection schema
        （tenant_id Int64 isPartitionKey=true + metadata JSON + ACL 标量列 + FloatVector 1024）
        建集合，验证目标态 schema 可建可查可与 LangChain4j 现网集合在同库共存。

设计约束：
  - 全程 `spikescale_*` 前缀临时集合，结束自动 drop（`--keep` 保留），**绝不触碰生产 knowledge_chunks**；
  - 默认只连本机 localhost:19530，可 `--uri` 覆盖（如 CI 用 Testcontainers 映射端口）；
  - 向量：为可复现且无外部依赖，默认用固定随机种子生成 1024 维归一向量（本 spike 测的是
    **检索基础设施时延与剪枝效率**，不是召回质量——召回质量已在 P2-T0 用真实 bge-m3 证明）；
    `--use-ollama` 可选切到 bge-m3 真实向量（每查询 batch 调 Ollama，慢但更贴近现网）。

用法：
  python3 spike_scale.py                 # 默认 10000 行 / 3 租户 / 64 分区 / 200 查询
  python3 spike_scale.py --rows 30000 --queries 300
  python3 spike_scale.py --keep          # 保留集合供人工复查
  python3 spike_scale.py --uri http://localhost:55xxx --use-ollama
"""
import argparse
import json
import math
import random
import statistics
import time
import urllib.request

DEFAULT_URI = "http://localhost:19530"
DB = "default"
DIM = 1024
OLLAMA = "http://localhost:11434"
EMBED_MODEL = "bge-m3"

# 三张对照集合（同库共存，验证 T-2）
PK_SET = "spikescale_pk"       # tenant_id Int64 isPartitionKey=true —— POOL 目标态
NOPK_SET = "spikescale_nopk"   # tenant_id Int64 普通标量 —— 全扫对照（剪枝收益基线）
JSON_SET = "spikescale_json"   # metadata 单 JSON 列 —— 复刻现网 LangChain4j 模型
TENANT_IDS = [1, 2, 3]         # default / acme / beta


def _post(uri, path, payload, timeout=180):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(uri + path, data=data,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def milvus(uri, path, payload, timeout=180):
    r = _post(uri, path, payload, timeout)
    if r.get("code") != 0:
        raise RuntimeError("Milvus %s -> %s" % (path, json.dumps(r)[:400]))
    return r


def drop(uri, name):
    try:
        milvus(uri, "/v2/vectordb/collections/drop", {"collectionName": name})
    except RuntimeError:
        pass


def unit_vec(rng):
    """固定 RNG 生成 1024 维 L2 归一随机向量（COSINE 语义下与文本向量同构，专测时延）。"""
    v = [rng.gauss(0.0, 1.0) for _ in range(DIM)]
    n = math.sqrt(sum(x * x for x in v)) or 1.0
    return [x / n for x in v]


def build_dataset(rng, total_rows, per_tenant):
    """返回 rows：[{id, tenant_id, category, vector}]，三租户均分，租户内 3 类别。"""
    rows = []
    _id = 0
    for ti, t in enumerate(per_tenant):
        for _ in range(t):
            _id += 1
            rows.append({
                "id": _id,
                "tenant_id": ti,               # 逻辑租户编号 0/1/2
                "category": "cat_%d" % (_id % 3),
                "vector": unit_vec(rng),
            })
    return rows


def create_collection(uri, name, mode, num_partitions):
    """mode: 'pk'（tenant_id 作 partition-key）/ 'nopk'（普通标量）/ 'json'（metadata 单列）。"""
    fields = [
        {"fieldName": "id", "dataType": "Int64", "isPrimary": True, "autoID": False},
        {"fieldName": "vector", "dataType": "FloatVector", "elementTypeParams": {"dim": str(DIM)}},
        {"fieldName": "category", "dataType": "VarChar", "elementTypeParams": {"max_length": "32"}},
    ]
    if mode == "pk":
        fields.append({"fieldName": "tenant_id", "dataType": "Int64", "isPartitionKey": True})
    elif mode == "nopk":
        fields.append({"fieldName": "tenant_id", "dataType": "Int64"})
    else:  # json —— 复刻 LangChain4j：租户塞进 metadata 单 JSON 列
        fields.append({"fieldName": "metadata", "dataType": "JSON"})
    idx = [{"fieldName": "vector", "indexName": "vector", "metricType": "COSINE",
            "indexType": "IVF_FLAT", "params": {"nlist": "128"}}]
    body = {
        "dbName": DB,
        "collectionName": name,
        "consistencyLevel": "Bounded",
        "schema": {"autoID": False, "enableDynamic": True, "fields": fields,
                   "numPartitions": num_partitions if mode == "pk" else 0},
        "indexParams": idx,
    }
    t0 = time.perf_counter()
    milvus(uri, "/v2/vectordb/collections/create", body)
    create_ms = (time.perf_counter() - t0) * 1000
    milvus(uri, "/v2/vectordb/collections/load", {"collectionName": name})
    return create_ms


def insert_rows(uri, name, mode, rows, batch=200):
    """分批插入，返回 (总耗时ms, 行数)。"""
    t0 = time.perf_counter()
    for s in range(0, len(rows), batch):
        chunk = rows[s:s + batch]
        data = []
        for r in chunk:
            if mode == "json":
                data.append({"id": r["id"], "vector": r["vector"], "category": r["category"],
                             "metadata": {"tenant_id": r["tenant_id"], "category": r["category"]}})
            else:
                data.append({"id": r["id"], "vector": r["vector"], "category": r["category"],
                             "tenant_id": r["tenant_id"]})
        milvus(uri, "/v2/vectordb/entities/insert", {"collectionName": name, "data": data})
    return (time.perf_counter() - t0) * 1000, len(rows)


def search(uri, name, query_vec, expr, limit=10, out_fields=("id", "tenant_id", "category")):
    r = milvus(uri, "/v2/vectordb/entities/search", {
        "collectionName": name, "data": [query_vec], "annsField": "vector",
        "filter": expr, "limit": limit, "outputFields": list(out_fields),
    }, timeout=120)
    return r.get("data", [])


def percentile(sorted_vals, p):
    if not sorted_vals:
        return float("nan")
    k = (len(sorted_vals) - 1) * p
    lo = math.floor(k)
    hi = math.ceil(k)
    if lo == hi:
        return sorted_vals[int(k)]
    return sorted_vals[lo] * (hi - k) + sorted_vals[hi] * (k - lo)


def latency_report(samples):
    s = sorted(samples)
    return {
        "n": len(s),
        "p50_ms": round(percentile(s, 0.5), 2),
        "p95_ms": round(percentile(s, 0.95), 2),
        "p99_ms": round(percentile(s, 0.99), 2),
        "mean_ms": round(statistics.mean(s), 2),
        "max_ms": round(s[-1], 2),
        "min_ms": round(s[0], 2),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--uri", default=DEFAULT_URI)
    ap.add_argument("--rows", type=int, default=10000, help="总行数（均分到各租户）")
    ap.add_argument("--tenants", type=int, default=3)
    ap.add_argument("--partitions", type=int, default=64, help="partition-key 分区数")
    ap.add_argument("--queries", type=int, default=200, help="每张集合的检索查询次数")
    ap.add_argument("--limit", type=int, default=10)
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--use-ollama", action="store_true", help="查询向量走真实 bge-m3")
    args = ap.parse_args()

    rng = random.Random(20260930)  # 固定种子，可复现
    total = args.rows
    per = [total // args.tenants] * args.tenants
    for i in range(total - sum(per)):
        per[i] += 1

    out = {
        "meta": {
            "uri": args.uri, "dim": DIM, "total_rows": total, "tenants": args.tenants,
            "rows_per_tenant": per, "num_partitions": args.partitions,
            "queries": args.queries, "limit": args.limit, "use_ollama": args.use_ollama,
            "index": "IVF_FLAT nlist=128 metric=COSINE", "random_seed": 20260930,
        }
    }

    print(">> 构建数据集：%d 行 × %d 维，%d 租户 %s" %
          (total, DIM, args.tenants, per))
    ds_t0 = time.perf_counter()
    rows = build_dataset(rng, total, per)
    out["meta"]["build_dataset_ms"] = round((time.perf_counter() - ds_t0) * 1000, 1)

    # 建三张对照集合 + 灌数据
    stages = {}
    for name, mode in ((PK_SET, "pk"), (NOPK_SET, "nopk"), (JSON_SET, "json")):
        drop(args.uri, name)
        create_ms = create_collection(args.uri, name, mode, args.partitions)
        ins_ms, n = insert_rows(args.uri, name, mode, rows)
        time.sleep(0.5)
        stages[name] = {"create_ms": round(create_ms, 1),
                        "insert_ms": round(ins_ms, 1),
                        "rows": n,
                        "insert_rows_per_sec": round(n / (ins_ms / 1000), 1)}
        print("   %-20s create=%.0fms insert=%.0fms (%.0f rows/s)" %
              (name, create_ms, ins_ms, n / (ins_ms / 1000)))
    out["ingest"] = stages
    time.sleep(3)  # 等 Bounded 一致性收敛

    # 查询向量集合
    if args.use_ollama:
        probe_texts = ["求根公式与判别式", "光合作用在哪里发生", "红楼梦的作者",
                       "TCP 三次握手", "牛顿第二定律", "叶绿体类囊体薄膜"]
        qvecs = []
        for i in range(args.queries):
            txt = probe_texts[i % len(probe_texts)]
            rr = _post(OLLAMA, "/api/embed", {"model": EMBED_MODEL, "input": [txt]}, timeout=120)
            qvecs.append(rr["embeddings"][0])
    else:
        qvecs = [unit_vec(random.Random(1000 + i)) for i in range(args.queries)]

    # 预热（每张集合先跑 20 条不计入统计，消除冷启动 / 首次 load 抖动）
    for name in (PK_SET, NOPK_SET, JSON_SET):
        for i in range(min(20, len(qvecs))):
            if name == JSON_SET:
                expr = 'metadata["tenant_id"] == 0'
                of = ("id", "metadata", "category")
            else:
                expr = "tenant_id == 0"
                of = ("id", "tenant_id", "category")
            search(args.uri, name, qvecs[i], expr, args.limit, of)

    # 正式测时延：轮转租户查询，记录跨租户泄漏
    lat = {PK_SET: [], NOPK_SET: [], JSON_SET: []}
    leak = {PK_SET: 0, NOPK_SET: 0, JSON_SET: 0}
    hits = {PK_SET: 0, NOPK_SET: 0, JSON_SET: 0}
    for i in range(args.queries):
        q = qvecs[i]
        t = i % args.tenants
        for name in (PK_SET, NOPK_SET, JSON_SET):
            if name == JSON_SET:
                expr = 'metadata["tenant_id"] == %d' % t
                of = ("id", "metadata", "category")
            else:
                expr = "tenant_id == %d" % t
                of = ("id", "tenant_id", "category")
            t0 = time.perf_counter()
            res = search(args.uri, name, q, expr, args.limit, of)
            dt = (time.perf_counter() - t0) * 1000
            lat[name].append(dt)
            hits[name] += len(res)
            for h in res:
                got = h.get("tenant_id")
                if got is None and isinstance(h.get("metadata"), dict):
                    got = h["metadata"].get("tenant_id")
                if got is not None and got != t:
                    leak[name] += 1
        if (i + 1) % 50 == 0:
            print("   检索进度 %d/%d" % (i + 1, args.queries))

    out["latency"] = {name: latency_report(lat[name]) for name in lat}
    out["cross_tenant_leak"] = leak
    out["avg_hits"] = {name: round(hits[name] / args.queries, 2) for name in hits}

    # 剪枝收益：partition-key 相对全扫（nopk）的时延改善比
    pk95 = out["latency"][PK_SET]["p95_ms"]
    nopk95 = out["latency"][NOPK_SET]["p95_ms"]
    json95 = out["latency"][JSON_SET]["p95_ms"]
    out["pruning_gain"] = {
        "pk_vs_nopk_p95_pct": round((nopk95 - pk95) / nopk95 * 100, 1) if nopk95 else None,
        "pk_vs_json_p95_pct": round((json95 - pk95) / json95 * 100, 1) if json95 else None,
        "note": "正值=partition-key 更快；收益随 num_partitions 与租户数、剪掉的空分区比例放大。",
    }

    if not args.keep:
        for name in (PK_SET, NOPK_SET, JSON_SET):
            drop(args.uri, name)
        print(">> 已清理 spikescale_* 集合")
    else:
        print(">> --keep：保留 %s/%s/%s" % (PK_SET, NOPK_SET, JSON_SET))

    print(json.dumps(out, ensure_ascii=False, indent=2))
    with open("results.json", "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print(">> 写出 results.json")


if __name__ == "__main__":
    main()
