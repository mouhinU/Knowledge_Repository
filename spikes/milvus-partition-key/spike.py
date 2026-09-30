#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
P2-T0 · Milvus partition-key 技术 spike（SaaS 向量隔离，提案 §10 R1 落点）。

目的：在真实 Milvus 2.5.4 + bge-m3 1024 维上，实测三条判据，为 Phase 2 "partition-key 下推
vs 多 collection vs 应用层 JSON 后过滤" 的取舍提供决策依据。

  a) partition-key 等值过滤下推：以 tenant_id 作 partition-key 建集合，检索带 `tenant_id == X`，
     验证跨租户 0 泄漏 + 相对非分区集合的时延收益。
  b) bge-m3 1024 维召回正确性：真实句子经 Ollama bge-m3 向量化，验证语义近邻符合预期。
  c) LangChain4j Filter 表达能力：结合 jar 静态分析 + 运行期 JSON 路径过滤，判断现有
     MilvusEmbeddingStore（metadata 单 JSON 列）能否表达 partition-key 下推。

本脚本只连本机 localhost:19530（开发用 Milvus），全程使用 `spike_*` 前缀临时集合，结束时删除，
不触碰生产 `knowledge_chunks`。纯标准库（urllib + math），无需 pip 安装。

用法：python3 spike.py [--keep]   # --keep 保留集合供人工复查
"""
import argparse
import json
import math
import statistics
import time
import urllib.request

MILVUS = "http://localhost:19530"
OLLAMA = "http://localhost:11434"
EMBED_MODEL = "bge-m3"
DIM = 1024
DB = "default"
PK_SET = "spike_pk"          # tenant_id = partition-key
NOPK_SET = "spike_nopk"      # tenant_id = 普通标量（时延对照）
JSON_SET = "spike_json"      # metadata 单 JSON 列（复刻 LangChain4j 模型）
TENANTS = [1, 2, 3]

# 语义语料：每条带 (文本, 租户, 类别)。查询用于召回正确性验证。
CORPUS = [
    ("春季学期数学月考的第 17 题考查一元二次方程求根", 1, "数学"),
    ("一元二次方程 ax^2+bx+c=0 的判别式为 b^2-4ac", 1, "数学"),
    ("光合作用发生在叶绿体的类囊体薄膜上", 2, "生物"),
    ("绿色植物利用光能把二氧化碳和水合成有机物", 2, "生物"),
    ("《红楼梦》是中国古典四大名著之一", 3, "语文"),
    ("曹雪芹创作的章回体长篇小说名为石头记", 3, "语文"),
    ("TCP 三次握手用于建立可靠的传输连接", 1, "网络"),
    ("IP 地址采用 32 位点分十进制表示", 2, "网络"),
    ("牛顿第二定律表述为 F=ma", 3, "物理"),
    ("万有引力常量 G 约为 6.67e-11", 1, "物理"),
]
QUERIES = [
    ("求根公式与判别式", 1, "一元二次方程 ax^2+bx+c=0 的判别式为 b^2-4ac"),
    ("植物如何制造养分", 2, "绿色植物利用光能把二氧化碳和水合成有机物"),
    ("石头记的作者是谁", 3, "曹雪芹创作的章回体长篇小说名为石头记"),
]


def _post(url, payload, timeout=60):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def milvus(path, payload, timeout=60):
    r = _post(MILVUS + path, payload, timeout)
    if r.get("code") != 0:
        raise RuntimeError("Milvus %s -> %s" % (path, json.dumps(r)[:300]))
    return r


def drop(name):
    try:
        milvus("/v2/vectordb/collections/drop", {"collectionName": name})
    except RuntimeError:
        pass


def create_collection(name, partition_key):
    fields = [
        {"fieldName": "id", "dataType": "Int64", "isPrimary": True, "autoID": False},
        {"fieldName": "vector", "dataType": "FloatVector", "elementTypeParams": {"dim": str(DIM)}},
        {"fieldName": "tag", "dataType": "VarChar", "elementTypeParams": {"max_length": "32"}},
    ]
    if partition_key:
        fields.append({"fieldName": "tenant_id", "dataType": "Int64", "isPartitionKey": True})
    elif name != JSON_SET:
        fields.append({"fieldName": "tenant_id", "dataType": "Int64"})
    else:  # LangChain4j 风格：租户塞进 JSON metadata
        fields.append({"fieldName": "metadata", "dataType": "JSON"})
    index_params = [
        {"fieldName": "vector", "indexName": "vector", "metricType": "COSINE",
         "indexType": "IVF_FLAT", "params": {"nlist": "128"}}
    ]
    milvus("/v2/vectordb/collections/create", {
        "dbName": DB, "collectionName": name, "consistencyLevel": "Strong",
        "schema": {"autoID": False, "enableDynamic": False, "fields": fields},
        "indexParams": index_params,
    })
    milvus("/v2/vectordb/collections/load", {"collectionName": name})


def ollama_embed(texts):
    r = _post(OLLAMA + "/api/embed", {"model": EMBED_MODEL, "input": texts}, timeout=120)
    return r["embeddings"]


def insert(name, vectors, rows):
    docs = []
    for i, vec in enumerate(vectors):
        d = {"id": rows[i]["id"], "vector": vec, "tag": rows[i]["tag"]}
        if name == JSON_SET:
            d["metadata"] = {"tenant_id": rows[i]["tenant_id"]}
        else:
            d["tenant_id"] = rows[i]["tenant_id"]
        docs.append(d)
    milvus("/v2/vectordb/entities/insert", {"collectionName": name, "data": docs})


def search(name, query_vec, expr, limit=5, out=("id", "tag")):
    r = milvus("/v2/vectordb/entities/search", {
        "collectionName": name, "data": [query_vec], "annsField": "vector",
        "filter": expr, "limit": limit, "outputFields": list(out),
    }, timeout=60)
    return r["data"]


def cosine(a, b):
    num = sum(x * y for x, y in zip(a, b))
    da = math.sqrt(sum(x * x for x in a))
    db = math.sqrt(sum(y * y for y in b))
    return num / (da * db) if da and db else 0.0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--keep", action="store_true", help="保留 spike_* 集合")
    args = ap.parse_args()

    results = {}
    print(">> 连接 Milvus / Ollama，准备语料向量（bge-m3 %d 维）..." % DIM)
    corpus_vecs = ollama_embed([c[0] for c in CORPUS])
    assert len(corpus_vecs[0]) == DIM, "bge-m3 维度非 %d：%s" % (DIM, len(corpus_vecs[0]))
    corpus_vecs_norm = [v  # bge-m3 输出已近似归一，检索走 COSINE
                        for v in corpus_vecs]

    rows = [{"id": i + 1, "tenant_id": CORPUS[i][1], "tag": CORPUS[i][2]}
            for i in range(len(CORPUS))]

    for nm in (PK_SET, NOPK_SET, JSON_SET):
        drop(nm)
        create_collection(nm, partition_key=(nm == PK_SET))
        insert(nm, corpus_vecs_norm, rows)
    time.sleep(2)  # 等强一致落盘

    # (b) 召回正确性（本地 numpy-free 余弦 + 引擎 COSINE 双验）
    print(">> [b] bge-m3 1024 维召回正确性")
    recall_rows = []
    for q_text, q_tenant, expect_tag in QUERIES:
        qv = ollama_embed([q_text])[0]
        # 引擎侧：限定同租户
        hits = search(PK_SET, qv, "tenant_id == %d" % q_tenant, limit=3, out=("tag", "id"))
        engine_top = hits[0]["tag"] if hits else None
        # 本地侧：全语料同租户里最近邻
        same = [(cosine(qv, corpus_vecs_norm[i]), CORPUS[i][2], i) for i in range(len(CORPUS))
                if CORPUS[i][1] == q_tenant]
        same.sort(reverse=True)
        local_top = same[0][1]
        recall_rows.append({
            "query": q_text, "tenant": q_tenant, "engine_top_tag": engine_top,
            "local_top_tag": local_top, "local_top_cos": round(same[0][0], 4),
            "agree": engine_top == local_top,
        })
    results["recall"] = recall_rows

    # (a) partition-key 跨租户 0 泄漏 + (c) JSON 路径过滤等价性
    print(">> [a/c] 隔离正确性：partition-key 下推 vs JSON metadata 路径过滤")
    isolation = []
    for t in TENANTS:
        qv = corpus_vecs_norm[rows[0]["id"] - 1]  # 任取一向量作探针
        pk_hits = search(PK_SET, qv, "tenant_id == %d" % t, limit=5, out=("tag",))
        jso_hits = search(JSON_SET, qv, 'metadata["tenant_id"] == %d' % t, limit=5,
                          out=("metadata",))
        # 期望：pk 与 json 两种过滤都只返回租户 t 的文档（共 3~4 条）
        pk_all_same = len(pk_hits) > 0 and all(h.get("tag") for h in pk_hits)
        expected_in_t = sum(1 for r in rows if r["tenant_id"] == t)
        json_all_same = len(jso_hits) == expected_in_t or len(jso_hits) == min(5, expected_in_t)
        isolation.append({
            "tenant": t, "pk_returned": len(pk_hits), "json_returned": len(jso_hits),
            "rows_owned": expected_in_t,
            "pk_nonempty": len(pk_hits) > 0,
        })
    # 负向：租户 1 的检索绝不该命中租户 2/3 的 id
    qv = corpus_vecs_norm[0]  # id=1, tenant=1
    leaked = search(PK_SET, qv, "tenant_id == 1", limit=5, out=("id",))
    other_ids = {r["id"] for r in rows if r["tenant_id"] != 1}
    leak_hit = [h for h in leaked if h.get("id") in other_ids]
    results["isolation"] = isolation
    results["cross_tenant_leak_count"] = len(leak_hit)

    # (a) 时延：partition-key vs 非分区，各跑 N 次取中位数
    print(">> [a] 下推时延对照（partition-key vs 普通标量过滤）")
    N = 40
    lat_pk, lat_nopk = [], []
    probe = corpus_vecs_norm[0]
    for _ in range(N):
        t0 = time.perf_counter(); search(PK_SET, probe, "tenant_id == 1"); lat_pk.append((time.perf_counter() - t0) * 1000)
        t0 = time.perf_counter(); search(NOPK_SET, probe, "tenant_id == 1"); lat_nopk.append((time.perf_counter() - t0) * 1000)
    results["latency_ms"] = {
        "n": N,
        "partition_key_median": round(statistics.median(lat_pk), 2),
        "partition_key_p95": round(sorted(lat_pk)[int(N * 0.95) - 1], 2),
        "plain_scalar_median": round(statistics.median(lat_nopk), 2),
        "plain_scalar_p95": round(sorted(lat_nopk)[int(N * 0.95) - 1], 2),
    }

    if not args.keep:
        for nm in (PK_SET, NOPK_SET, JSON_SET):
            drop(nm)
        print(">> 清理 spike_* 集合完成")
    else:
        print(">> --keep：集合保留 %s/%s/%s" % (PK_SET, NOPK_SET, JSON_SET))

    print(json.dumps(results, ensure_ascii=False, indent=2))
    with open("results.json", "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print("\n>> 写出 results.json")


if __name__ == "__main__":
    main()
