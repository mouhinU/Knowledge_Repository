/* ==========================================================================
   AI 出卷页面脚本 (ai-exam.js)
   职责：出卷工作台（题型分布方案 + 生成试卷 一体化）+ 出卷历史。
   工作流：Step1 生成/调整/平衡方案 → 通过 validatePlan 校验后解锁 Step2 →
   Step2 走多 Agent 流水线生成试卷；页面顶部 exam-stepper 展示当前所处节点。
   依赖 common.js 提供的：API / KR / esc / toast / renderMarkdown / renderPager /
   distributeInt / KR.fmtDateTime / KR.formatElapsed / KR.formatTime
   @author Knowledge-Repository
   @date 2026-09-16
   ========================================================================== */
(function () {
    'use strict';

    KR.initLayout('ai-exam');

    /* ---------- 状态 ---------- */
    let _lastExamRequest = null;
    let _examThinkingVisible = false;
    let _examAgentTimes = {};
    let _examTimerInterval = null;
    let _examActiveTimers = new Set();
    let _examStreamState = {};  // 逐 token 流式：记录每个 agent 的 output/thinking 是否已开始（用于首块清空占位）
    let _planStreamState = {};  // 题型分布方案 / 校验：逐 token 流式状态（首块清空占位）
    const _examAgentNodeMap = {
        researcher: 'research', scoring: 'scoring', writer: 'writing',
        answer: 'answer', reviewer: 'review', calibrator: 'calibrate', deduplicator: 'dedup'
    };
    let currentExamPlan = null;
    let _validationPassed = false;  // Node 2 校验是否已通过
    let _validationSessionId = null;  // 当前校验 SSE 会话 ID

    /* ---------- 节点详情弹窗 ---------- */
    const _nodeDetails = {};
    const _nodeLabels = {
        research: '知识检索', scoring: '分值校验与评估', writing: '试卷编写',
        answer: '答案生成', calibrate: '难度校准', review: '内容审核', dedup: '查重去重'
    };
    function storeNodeDetail(nodeKey, field, value) {
        if (!_nodeDetails[nodeKey]) _nodeDetails[nodeKey] = {};
        _nodeDetails[nodeKey][field] = value;
    }
    function showNodeModal(nodeKey) {
        const d = _nodeDetails[nodeKey] || {};
        document.getElementById('node-modal-name').textContent = _nodeLabels[nodeKey] || nodeKey;
        const badge = document.getElementById('node-modal-badge');
        if (d.endTime) { badge.textContent = '已完成'; badge.className = 'node-modal-badge done'; }
        else if (d.startTime) { badge.textContent = '执行中'; badge.className = 'node-modal-badge running'; }
        else { badge.textContent = '等待中'; badge.className = 'node-modal-badge pending'; }
        document.getElementById('node-modal-start').textContent = KR.formatTime(d.startTime);
        document.getElementById('node-modal-end').textContent = d.endTime ? KR.formatTime(d.endTime) : '-';
        document.getElementById('node-modal-elapsed').textContent = d.startTime ? KR.formatElapsed((d.endTime || Date.now()) - d.startTime) : '-';
        document.getElementById('node-modal-input').textContent = d.materials || '暂无数据';
        document.getElementById('node-modal-thinking').textContent = d.message || '暂无数据';
        document.getElementById('node-modal-output').textContent = d.output || '暂无数据';
        document.getElementById('node-modal-overlay').classList.add('active');
    }
    function closeNodeModal() { document.getElementById('node-modal-overlay').classList.remove('active'); }
    function bindNodeClick(elementId, nodeKey) {
        const el = document.getElementById(elementId);
        if (el) el.addEventListener('click', () => showNodeModal(nodeKey));
    }
    ['research', 'scoring', 'writing', 'answer', 'calibrate', 'review', 'dedup'].forEach(n => {
        bindNodeClick('exam-flow-' + n, n);
    });
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeNodeModal(); });

    /* ---------- 题型分布方案 ---------- */
    function planEsc(s) {
        return (s == null ? '' : String(s)).replace(/[&<>"]/g, c => (
            { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
    }

    const _planStageMeta = {
        'dist-classify': { title: '阶段①　题型分类' },
        'dist-count': { title: '阶段②　题型数量' },
        'dist-score': { title: '阶段③　每题分数设计' },
        'dist-evaluate': { title: '阶段④　合理性评估' },
        'exam-plan-validator': { title: '方案校验　AI 解读' }
    };

    async function generateExamPlan() {
        const topic = document.getElementById('exam-topic').value.trim();
        if (!topic) { toast('请先输入考试主题', 'error'); return; }
        // 新一轮生成：清空上一轮 step-2 done 标记，按钮回到门控状态
        setStepState(2, 'pending', '需先完成方案');
        const gBtn = document.getElementById('exam-generate-btn');
        if (gBtn) { gBtn.disabled = true; gBtn.textContent = '生成试卷'; gBtn.title = '请先生成并确认题型分布方案'; }
        const btn = document.getElementById('exam-plan-generate-btn');
        btn.disabled = true;
        const orig = btn.textContent;
        btn.textContent = '生成中...';

        resetPlanTrace();
        document.getElementById('exam-plan-trace-box').style.display = 'block';

        const sessionId = 'dist-' + Date.now() + '-' + Math.random().toString(36).substring(2, 10);
        const body = {
            sessionId: sessionId,
            topic: topic,
            difficulty: document.getElementById('exam-difficulty').value,
            schoolLevel: document.getElementById('exam-school-level').value || null,
            userId: document.getElementById('exam-user').value,
            departmentId: 'dept-root',
            roles: 'ADMIN',
            admin: document.getElementById('exam-admin').checked,
            category: document.getElementById('exam-category').value || null
        };

        const es = new EventSource(API + '/api/agent/exam/progress/' + sessionId);
        const finish = () => { try { es.close(); } catch (_) {} btn.disabled = false; btn.textContent = orig; };

        es.addEventListener('AGENT_OUTPUT', (e) => {
            let d; try { d = JSON.parse(e.data); } catch (_) { return; }
            if (!d.agentName) return;
            if (d.agentStatus === 'running') {
                upsertPlanTraceStage(d.agentName, { input: d.materials, thinking: d.message, running: true });
            } else if (d.agentStatus === 'done') {
                upsertPlanTraceStage(d.agentName, { output: d.output, running: false });
            }
        });
        es.addEventListener('AGENT_TOKEN', (e) => {
            let d; try { d = JSON.parse(e.data); } catch (_) { return; }
            if (!d.agentName || !d.delta) return;
            appendPlanToken(d.agentName, d.kind, d.delta);
        });
        es.addEventListener('COMPLETED', (e) => {
            let d; try { d = JSON.parse(e.data); } catch (_) { d = {}; }
            let plan = null;
            try { plan = JSON.parse(d.distributionPlan); } catch (_) { plan = null; }
            if (plan) {
                plan.manualAdjusted = false;   // 大模型新生成的方案，视为未手动调整
                currentExamPlan = plan;
                _validationPassed = false;  // 新方案需要重新校验
                collectPlan();
                renderExamPlan(currentExamPlan);
                toast('题型分布方案已生成，可在表格中调整', 'success');
            } else {
                toast('方案解析失败，请重试', 'error');
            }
            refreshStepGate();
            finish();
        });
        es.addEventListener('ERROR', (e) => {
            let d; try { d = JSON.parse(e.data); } catch (_) { d = {}; }
            toast('方案生成失败: ' + (d.errorMessage || '未知错误'), 'error');
            finish();
        });
        es.onerror = () => { /* 浏览器自动重连；终态由 COMPLETED/ERROR 关闭 */ };

        try {
            const res = await fetch(API + '/api/agent/exam/distribution-stream', {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
            });
            if (!res.ok) {
                const err = await res.json().catch(() => ({}));
                throw new Error(err.error || '启动生成失败');
            }
        } catch (e) {
            toast('方案生成失败: ' + e.message, 'error');
            finish();
        }
    }

    /** 单卡片渲染正文的最大字符数，超出后仅显示尾部（避免超长 <pre> 触发 layout 卡顿）。 */
    const _PST_MAX_RENDER = 20000;

    /** 保证卡片存在并返回 st（累积文本 / pending 队列 / rAF id / peek 起点）。 */
    function _pstEnsureState(agent, boxId) {
        let st = _planStreamState[agent];
        if (!st) {
            st = _planStreamState[agent] = {
                boxId: boxId || 'exam-plan-trace',
                text: { thinking: '', output: '' },
                peek: '',
                started: { thinking: false, output: false },
                rafId: null
            };
        } else if (boxId) {
            st.boxId = boxId;
        }
        return st;
    }

    /** 从累积文本生成"最近 80 字"的单行预览。 */
    function _pstPeek(text) {
        if (!text) return '';
        const tail = text.length > 80 ? '…' + text.slice(-79) : text;
        return tail.replace(/\s+/g, ' ');
    }

    /** 只写卡片 header 的 peek；body 由 _pstFlushBody 按需更新。 */
    function _pstScheduleFlush(agent) {
        const st = _planStreamState[agent];
        if (!st || st.rafId) return;
        st.rafId = requestAnimationFrame(() => {
            st.rafId = null;
            const card = document.getElementById('plan-stage-' + agent);
            if (!card) return;
            const peekEl = card.querySelector('.pst-peek');
            if (peekEl) peekEl.textContent = st.peek || '';
            if (card.classList.contains('open')) {
                _pstFlushBody(card, agent);
            }
        });
    }

    /** 打开状态下把 st.text 一次性同步进 DOM body（非 += 拼接）。 */
    function _pstFlushBody(card, agent) {
        const st = _planStreamState[agent];
        if (!st) return;
        const tb = card.querySelector('.pst-think-body');
        const ob = card.querySelector('.pst-out-body');
        if (tb) tb.textContent = st.text.thinking.length > _PST_MAX_RENDER
            ? '…(前 ' + (st.text.thinking.length - _PST_MAX_RENDER) + ' 字省略)\n' + st.text.thinking.slice(-_PST_MAX_RENDER)
            : st.text.thinking;
        if (ob) ob.textContent = st.text.output.length > _PST_MAX_RENDER
            ? '…(前 ' + (st.text.output.length - _PST_MAX_RENDER) + ' 字省略)\n' + st.text.output.slice(-_PST_MAX_RENDER)
            : st.text.output;
    }

    function resetPlanTrace() {
        ['exam-plan-trace', 'exam-val-trace'].forEach(id => {
            const box = document.getElementById(id);
            if (box) box.innerHTML = '';
        });
        Object.keys(_planStreamState).forEach(k => {
            const st = _planStreamState[k];
            if (st && st.rafId) cancelAnimationFrame(st.rafId);
            delete _planStreamState[k];
        });
        const vToggle = document.getElementById('exam-val-trace-box');
        if (vToggle) vToggle.style.display = 'none';
    }
    function upsertPlanTraceStage(agent, patch, boxId) {
        const box = document.getElementById(boxId || 'exam-plan-trace');
        if (!box) return;
        const meta = _planStageMeta[agent] || { title: agent };
        let card = document.getElementById('plan-stage-' + agent);
        if (!card) {
            card = document.createElement('div');
            card.id = 'plan-stage-' + agent;
            card.className = 'pst-card';
            card.innerHTML =
                '<div class="pst-header">'
                + '<span class="pst-caret">▸</span>'
                + '<span class="pst-dot"></span>'
                + '<strong class="pst-title">' + planEsc(meta.title) + '</strong>'
                + '<span class="pst-peek"></span>'
                + '<span class="pst-status">进行中…</span>'
                + '</div>'
                + '<div class="pst-body">'
                + '<div class="pst-sec pst-sec-input"><div class="pst-label">📥 输入</div>'
                + '<div class="pst-input-body"></div></div>'
                + '<div class="pst-sec pst-sec-think"><div class="pst-label">💭 思考</div>'
                + '<div class="pst-think-body"></div></div>'
                + '<div class="pst-sec pst-sec-out"><div class="pst-label">📤 输出</div>'
                + '<div class="pst-out-body"></div></div>'
                + '</div>';
            card.querySelector('.pst-header').addEventListener('click', () => {
                const open = card.classList.toggle('open');
                if (open) _pstFlushBody(card, agent);
            });
            box.appendChild(card);
        }
        const st = _pstEnsureState(agent, boxId);
        if (patch.input != null && patch.input !== '') {
            card.classList.add('has-input');
            const b = card.querySelector('.pst-input-body');
            if (b) b.textContent = patch.input;
        }
        if (patch.thinking != null && patch.thinking !== '' && !st.started.thinking) {
            st.text.thinking = patch.thinking;
            st.peek = '💭 ' + _pstPeek(st.text.thinking);
            card.classList.add('has-think');
        }
        if (patch.output != null) {
            st.text.output = String(patch.output);
            st.peek = '📤 ' + _pstPeek(st.text.output);
            card.classList.add('has-out');
        }
        if (patch.running === false) {
            const dot = card.querySelector('.pst-dot');
            if (dot) dot.classList.add('done');
            const status = card.querySelector('.pst-status');
            if (status) status.textContent = '已完成 ✓';
        }
        _pstScheduleFlush(agent);
        return card;
    }
    /** 逐 token 追加：写入 st.text 并排一次 rAF flush；卡片折叠时不触碰 body DOM。 */
    function appendPlanToken(agent, kind, delta, boxId) {
        if (!delta) return;
        const k = (kind === 'thinking') ? 'thinking' : 'output';
        const card = document.getElementById('plan-stage-' + agent)
            || upsertPlanTraceStage(agent, { running: true }, boxId);
        if (!card) return;
        const st = _pstEnsureState(agent, boxId);
        if (!st.started[k]) { st.text[k] = ''; st.started[k] = true; }
        st.text[k] += delta;
        st.peek = (k === 'thinking' ? '💭 ' : '📤 ') + _pstPeek(st.text[k]);
        card.classList.add(k === 'thinking' ? 'has-think' : 'has-out');
        _pstScheduleFlush(agent);
    }
    /** 重跑某阶段前重置：确保卡片存在、清空累积文本、状态回到"进行中"、保持折叠。 */
    function beginPlanStage(agent, boxId) {
        const card = upsertPlanTraceStage(agent, { running: true }, boxId);
        if (!card) return;
        const prev = _planStreamState[agent];
        if (prev && prev.rafId) cancelAnimationFrame(prev.rafId);
        _planStreamState[agent] = {
            boxId: boxId || 'exam-plan-trace',
            text: { thinking: '', output: '' },
            peek: '',
            started: { thinking: false, output: false },
            rafId: null
        };
        const tb = card.querySelector('.pst-think-body'); if (tb) tb.textContent = '';
        const ob = card.querySelector('.pst-out-body'); if (ob) ob.textContent = '';
        const pk = card.querySelector('.pst-peek'); if (pk) pk.textContent = '';
        card.classList.remove('has-think', 'has-out', 'open');
        const dot = card.querySelector('.pst-dot'); if (dot) dot.classList.remove('done', 'failed');
        const status = card.querySelector('.pst-status'); if (status) status.textContent = '进行中…';
    }
    /**
     * 渲染"自动平衡分值"节点的三态卡片（输入 · 思考 · 输出）。
     * 后端 ScoreRuleEngine.balancePlan 返回 Markdown 段落：### 输入 / ### 思考 / ### 输出。
     * 若后端未提供分段（旧数据 / 边界情况），退化为纯文本展示于输出区。
     * 使用与其它阶段一致的可收缩 .pst-card 结构，默认折叠，头部展示状态徽标。
     */
    function appendBalanceTrace(trace, boxId, toggleId) {
        const box = document.getElementById(boxId || 'exam-plan-trace');
        if (!box) return;
        const id = 'plan-stage-balance';
        let card = document.getElementById(id);
        if (!card) {
            card = document.createElement('div');
            card.id = id;
            card.className = 'pst-card';
            card.innerHTML =
                '<div class="pst-header">'
                + '<span class="pst-caret">▸</span>'
                + '<span class="pst-dot done"></span>'
                + '<strong class="pst-title">自动平衡分值</strong>'
                + '<span class="pst-peek"></span>'
                + '<span class="pst-status" style="color:#16a34a">后端确定性计算 · 已完成 ✓</span>'
                + '</div>'
                + '<div class="pst-body">'
                + '<div class="pst-sec pst-sec-input"><div class="pst-label">📥 输入</div>'
                + '<div class="pst-input-body"></div></div>'
                + '<div class="pst-sec pst-sec-think"><div class="pst-label">💭 思考</div>'
                + '<div class="pst-think-body"></div></div>'
                + '<div class="pst-sec pst-sec-out"><div class="pst-label">📤 输出</div>'
                + '<div class="pst-out-body"></div></div>'
                + '</div>';
            card.querySelector('.pst-header').addEventListener('click', () => card.classList.toggle('open'));
            box.appendChild(card);
        }
        const sections = splitTraceSections(trace);
        const hasSections = sections.input || sections.thinking || sections.output;
        if (sections.input) {
            card.classList.add('has-input');
            card.querySelector('.pst-input-body').textContent = sections.input;
        }
        if (sections.thinking) {
            card.classList.add('has-think');
            card.querySelector('.pst-think-body').textContent = sections.thinking;
        }
        if (sections.output) {
            card.classList.add('has-out');
            card.querySelector('.pst-out-body').textContent = sections.output;
        }
        if (!hasSections && trace) {
            card.classList.add('has-out');
            card.querySelector('.pst-out-body').textContent = trace;
        }
        const peekEl = card.querySelector('.pst-peek');
        if (peekEl) peekEl.textContent = '📤 ' + _pstPeek(sections.output || trace || '');
        const traceBox = document.getElementById(toggleId || 'exam-plan-trace-box');
        if (traceBox) traceBox.style.display = 'block';
    }

    /**
     * 解析 ScoreRuleEngine 的 trace Markdown 分段。
     * 输入示例：
     *   ### 输入
     *   目标满分：100 分
     *   · 单选：…
     *   ### 思考
     *   …
     *   ### 输出
     *   …
     */
    function splitTraceSections(trace) {
        const out = { input: '', thinking: '', output: '' };
        if (!trace) return out;
        const lines = String(trace).split(/\r?\n/);
        let cur = null;
        const buf = { input: [], thinking: [], output: [] };
        for (const ln of lines) {
            const m = /^###[ \t]+(\S+)[ \t]*$/.exec(ln);
            if (m) {
                const key = m[1].trim();
                if (key === '输入') cur = 'input';
                else if (key === '思考') cur = 'thinking';
                else if (key === '输出') cur = 'output';
                else cur = null;
                continue;
            }
            if (cur) buf[cur].push(ln);
        }
        out.input = buf.input.join('\n').trim();
        out.thinking = buf.thinking.join('\n').trim();
        out.output = buf.output.join('\n').trim();
        return out;
    }

    function renderExamPlan(plan) {
        const wrap = document.getElementById('exam-plan-wrap');
        if (!wrap) return;
        if (!plan || !plan.types || !plan.types.length) { wrap.innerHTML = ''; return; }
        let rows = '';
        plan.types.forEach((t, ti) => {
            const chips = renderScoreChips(t, ti);
            rows += '<tr>'
                + '<td style="padding:8px 10px;border-bottom:1px solid #eef1f5;vertical-align:top">'
                + '<input class="form-input plan-type-label" data-ti="' + ti + '" value="' + planEsc(t.label) + '" style="width:100%;padding:6px 8px;font-size:13px">'
                + '<div style="font-size:11px;color:#94a3b8;margin-top:3px">' + planEsc(kernelName(t.key)) + '</div>'
                + (t.reason ? '<div style="font-size:11px;color:#64748b;margin-top:3px" title="' + planEsc(t.reason) + '">' + planEsc(t.reason.slice(0, 40)) + (t.reason.length > 40 ? '…' : '') + '</div>' : '')
                + '</td>'
                + '<td style="padding:8px 10px;border-bottom:1px solid #eef1f5;text-align:center;vertical-align:top">'
                + '<input type="number" class="form-input plan-count" data-ti="' + ti + '" min="0" max="99" value="' + (t.count || 0) + '" style="width:64px;text-align:center;padding:6px 4px" onchange="onPlanCountChange(' + ti + ')">'
                + '</td>'
                + '<td style="padding:8px 10px;border-bottom:1px solid #eef1f5;vertical-align:top">'
                + '<div id="plan-chips-' + ti + '" style="display:flex;flex-wrap:wrap;gap:6px">' + chips + '</div>'
                + '</td>'
                + '<td style="padding:8px 10px;border-bottom:1px solid #eef1f5;text-align:center;vertical-align:top">'
                + '<span class="plan-subtotal" id="plan-sub-' + ti + '" style="font-weight:600;color:#475569">' + typeSubtotal(t) + '</span> 分'
                + '</td>'
                + '<td style="padding:8px 6px;border-bottom:1px solid #eef1f5;text-align:center;vertical-align:top">'
                + '<button class="btn btn-sm" onclick="evenSpreadType(' + ti + ')" title="该题型内均分" style="padding:2px 8px;font-size:12px">均分</button>'
                + '</td></tr>';
        });
        let notesHtml = '';
        if (plan.evaluationNotes && plan.evaluationNotes.length) {
            notesHtml = '<div style="margin-top:12px;padding:10px 12px;background:#fffbeb;border:1px solid #fde68a;border-radius:8px">'
                + '<div style="font-size:12px;font-weight:600;color:#92400e;margin-bottom:6px">合理性评估建议</div>'
                + '<ul style="margin:0;padding-left:18px;font-size:12px;color:#78350f;line-height:1.6">'
                + plan.evaluationNotes.map(n => '<li>' + planEsc(n) + '</li>').join('') + '</ul></div>';
        }
        wrap.innerHTML =
            '<div style="overflow-x:auto;border:1px solid #e5e9f0;border-radius:10px">'
            + '<table style="width:100%;border-collapse:collapse;font-size:13px;min-width:560px">'
            + '<thead><tr style="background:#f8fafc;text-align:left">'
            + '<th style="padding:8px 10px;font-size:12px;color:#64748b;font-weight:600;width:24%">题型（名称可改）</th>'
            + '<th style="padding:8px 10px;font-size:12px;color:#64748b;font-weight:600;width:8%;text-align:center">题量</th>'
            + '<th style="padding:8px 10px;font-size:12px;color:#64748b;font-weight:600;width:52%">每题分值（点击可改）</th>'
            + '<th style="padding:8px 10px;font-size:12px;color:#64748b;font-weight:600;width:8%;text-align:center">小计</th>'
            + '<th style="padding:8px 6px;font-size:12px;color:#64748b;font-weight:600;width:8%;text-align:center">操作</th>'
            + '</tr></thead><tbody>' + rows + '</tbody></table></div>'
            + '<div style="margin-top:8px;display:flex;gap:8px;align-items:center">'
            + '<button class="btn btn-sm" onclick="evenSpreadAll()" style="padding:4px 10px;font-size:12px;background:#f8fafc;border:1px solid #e2e8f0">全部按题型均分</button>'
            + '<span style="font-size:12px;color:#94a3b8">合卷：' + (plan.combined ? '是（' + planEsc((plan.subjects || []).join('、')) + '）' : '否')
            + ' · 学段：' + planEsc(levelName(plan.schoolLevel)) + ' · 满分：' + (plan.totalFullMark || 0) + ' 分</span>'
            + '</div>' + notesHtml;
        updatePlanSummary();
    }

    function renderScoreChips(t, ti) {
        const pq = t.perQuestion || [];
        let out = '';
        for (let qi = 0; qi < pq.length; qi++) {
            const v = pq[qi] == null ? 0 : pq[qi];
            out += '<span style="display:inline-flex;align-items:center;gap:2px;background:#f1f5f9;border:1px solid #e2e8f0;border-radius:6px;padding:1px 2px 1px 6px;font-size:12px;color:#64748b">'
                + (qi + 1) + '.'
                + '<input type="number" class="plan-score" data-ti="' + ti + '" data-qi="' + qi + '" min="0" max="100" value="' + v + '" style="width:42px;border:none;background:transparent;text-align:center;font-size:13px;font-weight:600;color:#334155;padding:2px" oninput="updatePlanSummary(); markPlanManual()">'
                + '分</span>';
        }
        if (!pq.length) out = '<span style="font-size:12px;color:#cbd5e1">题量为 0</span>';
        return out;
    }
    function kernelName(key) {
        return { SINGLE_CHOICE: '单选', MULTI_CHOICE: '多选', TRUE_FALSE: '判断', FILL_BLANK: '填空', SHORT_ANSWER: '简答', ESSAY: '论述' }[key] || key;
    }
    function levelName(code) {
        return { PRIMARY: '小学', JUNIOR: '初中', SENIOR: '高中', UNKNOWN: '自动' }[code] || (code || '自动');
    }
    function typeSubtotal(t) { return (t.perQuestion || []).reduce((a, b) => a + (b || 0), 0); }
    function kernelWeight(key) {
        return { SINGLE_CHOICE: 2, MULTI_CHOICE: 3, TRUE_FALSE: 2, FILL_BLANK: 3, SHORT_ANSWER: 6, ESSAY: 10 }[key] || 3;
    }

    function readPlanFromDom() {
        if (!currentExamPlan) return;
        document.querySelectorAll('.plan-type-label').forEach(el => {
            const ti = +el.dataset.ti;
            if (currentExamPlan.types[ti]) currentExamPlan.types[ti].label = el.value;
        });
        document.querySelectorAll('.plan-count').forEach(el => {
            const ti = +el.dataset.ti;
            if (currentExamPlan.types[ti]) currentExamPlan.types[ti].count = parseInt(el.value) || 0;
        });
        document.querySelectorAll('.plan-score').forEach(el => {
            const ti = +el.dataset.ti, qi = +el.dataset.qi;
            if (currentExamPlan.types[ti] && currentExamPlan.types[ti].perQuestion) {
                currentExamPlan.types[ti].perQuestion[qi] = parseInt(el.value) || 0;
            }
        });
    }
    function collectPlan() {
        readPlanFromDom();
        if (!currentExamPlan) return;
        currentExamPlan.types.forEach(t => {
            const c = Math.max(0, t.count || 0);
            t.count = c;
            if (c === 0) { t.perQuestion = []; return; }
            let pq = t.perQuestion || [];
            if (pq.length > c) pq = pq.slice(0, c);
            while (pq.length < c) pq.push(pq.length ? pq[pq.length - 1] : 1);
            t.perQuestion = pq;
        });
    }
    /** 用户任何形式的手动改动（题量 / 分值 / 均匀分布）都记为 manualAdjusted=true。 */
    function markPlanManual() {
        if (!currentExamPlan) return;
        currentExamPlan.manualAdjusted = true;
    }
    function planAllocated() {
        if (!currentExamPlan) return 0;
        return currentExamPlan.types.reduce((s, t) => s + typeSubtotal(t), 0);
    }

    /* ---------- 出卷工作流：两步节点与门控（方案校验通过才允许进入「生成试卷」） ---------- */
    /**
     * 快速检查当前方案是否可作为下一步的输入。返回 null 表示通过；否则返回不通过原因。
     * 校验维度：方案存在、有题型、题量之和 > 0、满分 > 0、每题分数之和 > 0 且严格等于满分。
     */
    function checkPlanReady() {
        if (!currentExamPlan) return '尚未生成题型分布方案';
        if (!currentExamPlan.types || !currentExamPlan.types.length) return '方案缺少题型';
        const totalCount = currentExamPlan.types.reduce((s, t) => s + Math.max(0, t.count || 0), 0);
        if (totalCount <= 0) return '题量合计为 0，请至少设置一种题型的题量';
        const target = currentExamPlan.totalFullMark || 0;
        if (target <= 0) return '满分未设置';
        const allocated = planAllocated();
        if (allocated <= 0) return '分值合计为 0';
        if (allocated !== target) return '分值合计 ' + allocated + ' ≠ 满分 ' + target + '，请点自动平衡或手工调整';
        return null;
    }

    /**
     * 自动平衡的前置检查（比 checkPlanReady 宽松）。
     * <p>只要方案存在、有题型、题量合计 > 0 且满分 > 0 即可平衡——
     * "分值合计 ≠ 满分" 正是平衡要修复的目标，绝不能拿它当拦截条件，
     * 否则用户手动调分后反而点不动「自动平衡」。</p>
     * @returns {string|null} null 表示可平衡，否则返回不可平衡原因
     */
    function checkPlanBalanceable() {
        if (!currentExamPlan) return '尚未生成题型分布方案';
        if (!currentExamPlan.types || !currentExamPlan.types.length) return '方案缺少题型';
        const totalCount = currentExamPlan.types.reduce((s, t) => s + Math.max(0, t.count || 0), 0);
        if (totalCount <= 0) return '题量合计为 0，请至少设置一种题型的题量';
        const target = currentExamPlan.totalFullMark || 0;
        if (target <= 0) return '满分未设置，无法按满分平衡';
        return null;
    }

    /**
     * Node 2：调用后端校验方案（异步 SSE），展示校验结果。
     */
    async function validatePlan() {
        const reason = checkPlanReady();
        if (reason) { toast('方案未就绪: ' + reason, 'error'); refreshStepGate(); return; }
        collectPlan();

        const btn = document.getElementById('exam-validate-btn');
        const resultEl = document.getElementById('exam-validate-result');
        btn.disabled = true;
        btn.textContent = '校验中...';
        resultEl.style.display = 'block';
        resultEl.style.background = '#fef3c7';
        resultEl.style.color = '#92400e';
        resultEl.innerHTML = '<span style="opacity:.7">正在校验方案…</span>';
        setStepState(2, 'active', '校验中…');

        const traceBox = document.getElementById('exam-val-trace-box');
        if (traceBox) { traceBox.style.display = 'block'; }
        beginPlanStage('exam-plan-validator', 'exam-val-trace');

        _validationSessionId = 'validate-' + Date.now() + '-' + Math.random().toString(36).substring(2, 10);
        const eventSource = new EventSource(API + '/api/agent/exam/progress/' + _validationSessionId);
        let sseReady = false;
        eventSource.onopen = () => { sseReady = true; };

        eventSource.addEventListener('AGENT_OUTPUT', (e) => {
            const data = JSON.parse(e.data);
            if (data.agentStatus === 'done') {
                _validationPassed = true;
                resultEl.style.background = '#dcfce7';
                resultEl.style.color = '#166534';
                resultEl.innerHTML = renderMarkdown(data.output || '校验通过');
                upsertPlanTraceStage('exam-plan-validator', { running: false }, 'exam-val-trace');
                setStepState(2, 'done', '校验已通过');
                refreshStepGate();
                eventSource.close();
                btn.disabled = false;
                btn.textContent = '校验方案';
            } else if (data.agentStatus === 'failed') {
                _validationPassed = false;
                resultEl.style.background = '#fee2e2';
                resultEl.style.color = '#991b1b';
                resultEl.innerHTML = renderMarkdown(data.output || '校验未通过');
                upsertPlanTraceStage('exam-plan-validator', { running: false }, 'exam-val-trace');
                setStepState(2, 'active', '校验未通过');
                const balanceBtn = document.getElementById('exam-balance-btn');
                if (balanceBtn) balanceBtn.disabled = false;
                refreshStepGate();
                eventSource.close();
                btn.disabled = false;
                btn.textContent = '校验方案';
            } else if (data.agentStatus === 'running') {
                if (data.message) {
                    resultEl.innerHTML = '<span style="opacity:.7">' + esc(data.message) + '</span>';
                }
                upsertPlanTraceStage('exam-plan-validator', { input: data.materials, thinking: data.message, running: true }, 'exam-val-trace');
            }
        });

        eventSource.addEventListener('AGENT_TOKEN', (e) => {
            let d; try { d = JSON.parse(e.data); } catch (_) { return; }
            if (!d.agentName || !d.delta) return;
            appendPlanToken(d.agentName, d.kind, d.delta, 'exam-val-trace');
        });

        eventSource.addEventListener('ERROR', (e) => {
            eventSource.close();
            btn.disabled = false;
            btn.textContent = '校验方案';
            _validationPassed = false;
            const data = JSON.parse(e.data);
            resultEl.style.background = '#fee2e2';
            resultEl.style.color = '#991b1b';
            resultEl.innerHTML = '<strong>校验失败：</strong>' + esc(data.errorMessage || '未知错误');
            setStepState(2, 'active', '校验失败');
            refreshStepGate();
        });

        const waitForSse = () => new Promise((resolve) => {
            if (sseReady) { resolve(); return; }
            const check = () => { if (sseReady) resolve(); else setTimeout(check, 50); };
            check();
            setTimeout(resolve, 3000);
        });

        try {
            await waitForSse();
            const res = await fetch(API + '/api/agent/exam/plan/validate-stream', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    sessionId: _validationSessionId,
                    distribution: JSON.stringify(currentExamPlan)
                })
            });
            if (!res.ok) {
                const err = await res.json().catch(() => ({}));
                throw new Error(err.error || '启动校验失败');
            }
        } catch (e) {
            eventSource.close();
            btn.disabled = false;
            btn.textContent = '校验方案';
            toast('校验失败: ' + e.message, 'error');
            resultEl.style.display = 'none';
            refreshStepGate();
        }
    }

    /**
     * Node 2：自动平衡分值并重新校验。
     */
    async function balanceAndRevalidate() {
        const reason = checkPlanBalanceable();
        if (reason) { toast('无法平衡: ' + reason, 'error'); refreshStepGate(); return; }
        collectPlan();

        const btn = document.getElementById('exam-balance-btn');
        btn.disabled = true;
        btn.textContent = '平衡中...';
        toast('正在按满分自动平衡分值…', 'info');

        try {
            const res = await fetch(API + '/api/agent/exam/distribution/balance', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ distribution: JSON.stringify(currentExamPlan) })
            });
            if (!res.ok) {
                const err = await res.json().catch(() => ({}));
                throw new Error(err.error || '平衡失败');
            }
            const data = await res.json();
            if (data.plan) { data.plan.manualAdjusted = true; }
            currentExamPlan = data.plan;
            renderExamPlan(currentExamPlan);
            updatePlanSummary();
            const traceBox = document.getElementById('exam-val-trace-box');
            if (traceBox) { traceBox.style.display = 'block'; }
            appendBalanceTrace(data.trace, 'exam-val-trace', 'exam-val-trace-box');
            toast('分值已按满分平衡，正在重新校验…', 'success');
            btn.disabled = false;
            btn.textContent = '自动平衡';
            // 重新校验
            await validatePlan();
        } catch (e) {
            btn.disabled = false;
            btn.textContent = '自动平衡';
            toast('平衡失败: ' + e.message, 'error');
        }
    }

    /**
     * 更新步骤节点：step=1|2；state='pending'|'active'|'done'；hint 覆盖 flow-time 提示。
     */
    function setStepState(step, state, hint) {
        const node = document.getElementById('exam-step-' + step);
        const hintEl = document.getElementById('exam-step-' + step + '-hint');
        if (!node) return;
        node.classList.remove('active', 'done');
        if (state === 'active') node.classList.add('active');
        else if (state === 'done') node.classList.add('done');
        if (hintEl && hint != null) hintEl.textContent = hint;
    }

    /**
     * 根据当前方案刷新三步节点 + 按钮门控。
     */
    function refreshStepGate() {
        const reason = checkPlanReady();
        const balanceReason = checkPlanBalanceable();
        const validateBtn = document.getElementById('exam-validate-btn');
        const balanceBtn = document.getElementById('exam-balance-btn');
        const generateBtn = document.getElementById('exam-generate-btn');
        const conn1 = document.getElementById('exam-step-connector-1');
        const conn2 = document.getElementById('exam-step-connector-2');

        // 自动平衡（现位于步骤 1「题型分布方案」）：只要方案可平衡就启用，
        // 分值合计≠满分时正是用户需要点它的时刻，不能拦截。
        if (balanceBtn) {
            balanceBtn.disabled = balanceReason != null;
            balanceBtn.title = balanceReason ? balanceReason : '手动调整题型或分数后，点此按满分自动平衡每题分值';
        }

        if (reason) {
            // Node 1 未就绪
            setStepState(1, 'active', '生成 · 调整');
            setStepState(2, 'pending', '需先完成方案');
            setStepState(3, 'pending', '需先通过校验');
            if (conn1) conn1.classList.remove('done');
            if (conn2) conn2.classList.remove('done');
            if (validateBtn) validateBtn.disabled = true;
            if (generateBtn) { generateBtn.disabled = true; generateBtn.title = reason; }
        } else {
            // Node 1 已就绪
            setStepState(1, 'done', '方案已就绪');
            if (conn1) conn1.classList.add('done');

            // Node 2 状态由 _validationPassed 控制
            if (_validationPassed) {
                setStepState(2, 'done', '校验已通过');
                if (conn2) conn2.classList.add('done');
                setStepState(3, 'active', '可以开始生成');
                if (generateBtn) { generateBtn.disabled = false; generateBtn.title = '按当前方案生成试卷'; }
            } else {
                setStepState(2, 'active', '请点击校验方案');
                if (conn2) conn2.classList.remove('done');
                setStepState(3, 'pending', '需先通过校验');
                if (generateBtn) { generateBtn.disabled = true; generateBtn.title = '请先通过分值校验'; }
            }

            if (validateBtn) validateBtn.disabled = false;
        }
    }
    function updatePlanSummary() {
        readPlanFromDom();
        if (!currentExamPlan) return;
        // 用户编辑方案后，Node 2 校验结果失效，须重新校验
        _validationPassed = false;
        currentExamPlan.types.forEach((t, ti) => {
            const el = document.getElementById('plan-sub-' + ti);
            if (el) el.textContent = typeSubtotal(t);
        });
        const allocated = planAllocated();
        const target = currentExamPlan.totalFullMark || 0;
        const sumEl = document.getElementById('exam-plan-summary');
        if (sumEl) {
            if (allocated === target) {
                sumEl.innerHTML = '<span style="color:#16a34a;font-weight:600">合计 ' + allocated + ' / 满分 ' + target + ' 分 ✓</span>';
            } else {
                sumEl.innerHTML = '<span style="color:#dc2626;font-weight:600">合计 ' + allocated + ' / 满分 ' + target + ' 分（相差 ' + (allocated - target) + '，请调整或点自动平衡）</span>';
            }
        }
        refreshStepGate();
    }
    function onPlanCountChange(ti) {
        readPlanFromDom();
        markPlanManual();
        const t = currentExamPlan && currentExamPlan.types[ti];
        if (!t) return;
        const c = Math.max(0, t.count || 0);
        let pq = t.perQuestion || [];
        if (pq.length > c) pq = pq.slice(0, c);
        while (pq.length < c) pq.push(pq.length ? pq[pq.length - 1] : 1);
        t.perQuestion = pq;
        const box = document.getElementById('plan-chips-' + ti);
        if (box) box.innerHTML = renderScoreChips(t, ti);
        updatePlanSummary();
    }
    function evenSpreadType(ti) {
        readPlanFromDom();
        markPlanManual();
        const t = currentExamPlan && currentExamPlan.types[ti];
        if (!t || !t.count) return;
        const cur = typeSubtotal(t);
        const score = cur > 0 ? cur : t.count;
        t.perQuestion = distributeInt(Math.max(1, score), t.count);
        const box = document.getElementById('plan-chips-' + ti);
        if (box) box.innerHTML = renderScoreChips(t, ti);
        updatePlanSummary();
    }
    function evenSpreadAll() {
        readPlanFromDom();
        if (!currentExamPlan) return;
        markPlanManual();
        const target = currentExamPlan.totalFullMark || 0;
        const weights = currentExamPlan.types.map(t => Math.max(0, t.count || 0) * kernelWeight(t.key));
        const totalW = weights.reduce((a, b) => a + b, 0) || 1;
        let acc = 0;
        const subtotals = weights.map((w) => { const base = Math.floor(target * w / totalW); acc += base; return base; });
        const frac = weights.map((w, i) => target * w / totalW - subtotals[i]);
        const order = frac.map((f, i) => i).sort((a, b) => frac[b] - frac[a]);
        let left = target - acc;
        for (let k = 0; k < left; k++) subtotals[order[k % order.length]]++;
        currentExamPlan.types.forEach((t, i) => {
            if (t.count > 0) t.perQuestion = distributeInt(Math.max(1, subtotals[i]), t.count);
        });
        renderExamPlan(currentExamPlan);
    }

    /* ---------- 出卷流水线（第二步） ---------- */
    function startExamLiveTimer(agent) {
        _examActiveTimers.add(agent);
        if (!_examTimerInterval) {
            _examTimerInterval = setInterval(() => {
                _examActiveTimers.forEach(a => {
                    const node = _examAgentNodeMap[a];
                    if (node && _examAgentTimes[a]) {
                        const tEl = document.getElementById('exam-flow-time-' + node);
                        if (tEl) tEl.textContent = KR.formatElapsed(Date.now() - _examAgentTimes[a]);
                    }
                });
            }, 200);
        }
    }
    function stopExamTimer(agent) {
        _examActiveTimers.delete(agent);
        if (_examActiveTimers.size === 0 && _examTimerInterval) {
            clearInterval(_examTimerInterval);
            _examTimerInterval = null;
        }
    }
    /** Step 3 Agent 面板：合并每帧的 DOM 写；折叠时不触碰 <pre>；开启时同步 st.text。 */
    function _examTokenFlush(ui) {
        const st = _examStreamState[ui];
        if (!st) return;
        st.rafId = null;
        const panel = document.getElementById('exam-panel-' + ui);
        if (!panel) return;
        const isOpen = panel.classList.contains('open');
        const cap = (txt) => txt.length > _PST_MAX_RENDER
            ? '…(前 ' + (txt.length - _PST_MAX_RENDER) + ' 字省略)\n' + txt.slice(-_PST_MAX_RENDER)
            : txt;
        if (isOpen) {
            const pre = document.getElementById('exam-thinking-' + ui);
            const out = document.getElementById('exam-output-' + ui);
            if (pre && st.started.thinking) pre.textContent = cap(st.text.thinking);
            if (out && st.started.output) out.textContent = cap(st.text.output);
        } else {
            const badge = document.getElementById('exam-status-' + ui);
            if (badge && badge.classList.contains('running')) {
                const total = st.text.thinking.length + st.text.output.length;
                badge.textContent = '执行中… ' + total + ' 字';
            }
        }
    }
    /** 用户手动展开面板时，用缓冲文本一次性追平 DOM，避免面板首次打开显示空白/半截。 */
    function _examPanelOpenSync(ui) {
        const st = _examStreamState[ui];
        if (!st) return;
        if (st.rafId) { cancelAnimationFrame(st.rafId); st.rafId = null; }
        _examTokenFlush(ui);
    }
    function resetExamUI() {
        document.getElementById('exam-progress').classList.add('active');
        document.getElementById('exam-error').classList.remove('active');
        document.getElementById('exam-error').textContent = '';
        document.getElementById('exam-result').style.display = 'none';
        document.getElementById('exam-answer-section').style.display = 'none';
        document.getElementById('exam-flow-start').classList.add('done');
        ['research', 'scoring', 'writing', 'answer', 'calibrate', 'review', 'dedup'].forEach(n => {
            const el = document.getElementById('exam-flow-' + n);
            if (el) el.classList.remove('active', 'done', 'failed');
        });
        document.getElementById('exam-flow-complete').classList.remove('done');
        _examActiveTimers.clear();
        if (_examTimerInterval) { clearInterval(_examTimerInterval); _examTimerInterval = null; }
        _examAgentTimes = {};
        Object.keys(_nodeDetails).forEach(k => delete _nodeDetails[k]);
        ['research', 'scoring', 'writing', 'answer', 'calibrate', 'review', 'dedup'].forEach(n => {
            const t = document.getElementById('exam-flow-time-' + n);
            if (t) t.textContent = '';
        });
        ['researcher', 'scoring', 'writer', 'answer', 'reviewer', 'calibrator', 'deduplicator'].forEach(a => {
            setExamAgentStatus(a, 'pending');
            const outputEl = document.getElementById('exam-output-' + a);
            if (outputEl) outputEl.textContent = '等待执行...';
            const panelEl = document.getElementById('exam-panel-' + a);
            if (panelEl) panelEl.classList.remove('open');
            const thinkWrap = document.getElementById('exam-thinkingwrap-' + a);
            if (thinkWrap) { thinkWrap.classList.remove('active'); thinkWrap.open = false; }
            const thinkPre = document.getElementById('exam-thinking-' + a);
            if (thinkPre) thinkPre.textContent = '';
        });
        _examStreamState = {};
        document.getElementById('exam-materials-researcher').textContent = '等待检索...';
        document.getElementById('exam-materials-scoring').textContent = '等待检索...';
        document.getElementById('exam-materials-writer').textContent = '等待研究员完成...';
        document.getElementById('exam-materials-answer').textContent = '等待出题人完成...';
        document.getElementById('exam-materials-reviewer').textContent = '等待答案生成完成...';
        document.getElementById('exam-materials-calibrator').textContent = '等待出题人完成...';
        document.getElementById('exam-materials-deduplicator').textContent = '等待答案生成完成...';
    }
    function setExamPhase(phase) {
        const stages = [['research', 'scoring'], ['writing'], ['answer', 'calibrate'], ['review', 'dedup']];
        let stageIdx = -1;
        for (let i = 0; i < stages.length; i++) { if (stages[i].includes(phase)) { stageIdx = i; break; } }
        stages.forEach((stage, i) => {
            stage.forEach(node => {
                const el = document.getElementById('exam-flow-' + node);
                if (!el) return;
                if (el.classList.contains('failed')) return; // 保留校验阻断的红态
                el.classList.remove('active', 'done');
                if (i < stageIdx) el.classList.add('done');
                else if (i === stageIdx) el.classList.add('active');
            });
        });
    }
    function setExamAgentStatus(agent, status, text) {
        const el = document.getElementById('exam-status-' + agent);
        if (!el) return;
        el.className = 'agent-status ' + status;
        el.textContent = text || ({ pending: '等待中', running: '执行中...', done: '已完成', failed: '已阻断' }[status]);
    }

    async function generateExamWithAgents() {
        const topic = document.getElementById('exam-topic').value.trim();
        if (!topic) { toast('请输入考试主题', 'error'); return; }
        const invalidReason = checkPlanReady();
        if (invalidReason) { toast('方案未就绪: ' + invalidReason, 'error'); refreshStepGate(); return; }
        if (!_validationPassed) { toast('请先通过分值校验', 'error'); refreshStepGate(); return; }
        collectPlan();
        const planTotal = currentExamPlan.types.reduce((s, t) => s + Math.max(0, t.count || 0), 0);
        if (planTotal <= 0) { toast('题量为 0，请调整方案', 'error'); return; }
        if (!currentExamPlan.totalFullMark) { toast('满分未设置，无法生成', 'error'); return; }

        setStepState(1, 'done');
        setStepState(2, 'done');
        setStepState(3, 'active', '流水线执行中…');
        const btn = document.getElementById('exam-generate-btn');
        btn.disabled = true;
        btn.textContent = '连接中...';
        resetExamUI();

        _lastExamRequest = {
            topic: topic,
            difficulty: document.getElementById('exam-difficulty').value,
            schoolLevel: document.getElementById('exam-school-level').value || null,
            distribution: JSON.stringify(currentExamPlan),
            userId: document.getElementById('exam-user').value,
            departmentId: 'dept-root',
            roles: 'ADMIN',
            admin: document.getElementById('exam-admin').checked,
            category: document.getElementById('exam-category').value || null,
            skipScoringValidation: true  // Node 2 已校验通过，Node 3 跳过校验
        };

        const sessionId = 'exam-' + Date.now() + '-' + Math.random().toString(36).substring(2, 10);
        const eventSource = new EventSource(API + '/api/agent/exam/progress/' + sessionId);
        let sseReady = false;
        eventSource.onopen = () => { sseReady = true; };

        eventSource.addEventListener('PHASE', (e) => {
            const data = JSON.parse(e.data);
            const phaseMap = {
                'INIT': 'research', 'RESEARCH': 'research', 'SCORING': 'scoring', 'WRITING': 'writing',
                'ANSWER_GENERATING': 'answer', 'REVIEWING': 'review', 'CALIBRATING': 'calibrate',
                'DEDUPLICATING': 'dedup', 'COMPLETED': 'dedup'
            };
            setExamPhase(phaseMap[data.phase] || 'research');
        });

        eventSource.addEventListener('AGENT_OUTPUT', (e) => {
            const data = JSON.parse(e.data);
            const agent = data.agentName;
            if (!agent) return;
            const agentUiMap = {
                'exam-researcher': 'researcher', 'exam-scoring': 'scoring', 'exam-writer': 'writer',
                'answer-generator': 'answer', 'exam-reviewer': 'reviewer', 'exam-calibrator': 'calibrator',
                'exam-deduplicator': 'deduplicator'
            };
            const uiAgent = agentUiMap[agent] || agent;
            if (data.agentStatus === 'running') {
                _examAgentTimes[uiAgent] = Date.now();
                startExamLiveTimer(uiAgent);
                setExamAgentStatus(uiAgent, 'running');
                const eNode = _examAgentNodeMap[uiAgent];
                if (eNode) {
                    const flowEl = document.getElementById('exam-flow-' + eNode);
                    if (flowEl) { flowEl.classList.remove('done'); flowEl.classList.add('active'); }
                    storeNodeDetail(eNode, 'startTime', Date.now());
                    if (data.materials) storeNodeDetail(eNode, 'materials', data.materials);
                    if (data.message) storeNodeDetail(eNode, 'message', data.message);
                }
                const panel = document.getElementById('exam-panel-' + uiAgent);
                if (panel) panel.classList.remove('open');
                _examThinkingVisible = false;
                if (data.message) {
                    const outputEl = document.getElementById('exam-output-' + uiAgent);
                    if (outputEl) outputEl.textContent = data.message;
                }
                if (data.materials) {
                    const matEl = document.getElementById('exam-materials-' + uiAgent);
                    if (matEl) matEl.textContent = data.materials;
                }
            } else if (data.agentStatus === 'done') {
                setExamAgentStatus(uiAgent, 'done');
                if (data.output) {
                    const outputEl = document.getElementById('exam-output-' + uiAgent);
                    if (outputEl) outputEl.textContent = data.output;
                }
                stopExamTimer(uiAgent);
                const eNode = _examAgentNodeMap[uiAgent];
                if (eNode) {
                    const flowEl = document.getElementById('exam-flow-' + eNode);
                    if (flowEl) { flowEl.classList.remove('active'); flowEl.classList.add('done'); }
                    if (_examAgentTimes[uiAgent]) {
                        const tEl = document.getElementById('exam-flow-time-' + eNode);
                        if (tEl) tEl.textContent = KR.formatElapsed(Date.now() - _examAgentTimes[uiAgent]);
                    }
                    storeNodeDetail(eNode, 'endTime', Date.now());
                    if (data.output) storeNodeDetail(eNode, 'output', data.output);
                }
            } else if (data.agentStatus === 'failed') {
                // 校验闸门未通过：本节点标红，下游节点保持 pending（不再推进）
                setExamAgentStatus(uiAgent, 'failed', '已阻断');
                if (data.output) {
                    const outputEl = document.getElementById('exam-output-' + uiAgent);
                    if (outputEl) outputEl.textContent = data.output;
                }
                stopExamTimer(uiAgent);
                const eNode = _examAgentNodeMap[uiAgent];
                if (eNode) {
                    const flowEl = document.getElementById('exam-flow-' + eNode);
                    if (flowEl) { flowEl.classList.remove('active', 'done'); flowEl.classList.add('failed'); }
                    if (_examAgentTimes[uiAgent]) {
                        const tEl = document.getElementById('exam-flow-time-' + eNode);
                        if (tEl) tEl.textContent = KR.formatElapsed(Date.now() - _examAgentTimes[uiAgent]);
                    }
                    storeNodeDetail(eNode, 'endTime', Date.now());
                    if (data.output) storeNodeDetail(eNode, 'output', data.output);
                }
            }
        });

        // 真流式：逐 token 累积到 st.text，rAF 合并 DOM 写；面板折叠时只更新状态徽标。
        // 完成时的 AGENT_OUTPUT(done) 仍以完整文本兜底收敛，二者不冲突。
        eventSource.addEventListener('AGENT_TOKEN', (e) => {
            const data = JSON.parse(e.data);
            const agent = data.agentName;
            if (!agent) return;
            const delta = data.delta || '';
            if (!delta) return;
            const tokenUiMap = {
                'exam-researcher': 'researcher', 'exam-scoring': 'scoring', 'exam-writer': 'writer',
                'answer-generator': 'answer', 'exam-reviewer': 'reviewer', 'exam-calibrator': 'calibrator',
                'exam-deduplicator': 'deduplicator'
            };
            const ui = tokenUiMap[agent] || agent;
            const kind = (data.kind === 'thinking') ? 'thinking' : 'output';
            let st = _examStreamState[ui];
            if (!st) {
                st = _examStreamState[ui] = {
                    text: { thinking: '', output: '' },
                    started: { thinking: false, output: false },
                    rafId: null
                };
            }
            if (!st.started[kind]) { st.text[kind] = ''; st.started[kind] = true; }
            st.text[kind] += delta;
            if (kind === 'thinking') {
                const wrap = document.getElementById('exam-thinkingwrap-' + ui);
                if (wrap && !wrap.classList.contains('active')) wrap.classList.add('active');
            }
            if (!st.rafId) st.rafId = requestAnimationFrame(() => _examTokenFlush(ui));
        });

        eventSource.addEventListener('COMPLETED', (e) => {
            const data = JSON.parse(e.data);
            eventSource.close();
            ['research', 'scoring', 'writing', 'answer', 'calibrate', 'review', 'dedup'].forEach(n => {
                const el = document.getElementById('exam-flow-' + n);
                if (el) { el.classList.remove('active'); el.classList.add('done'); }
            });
            const completeEl = document.getElementById('exam-flow-complete');
            if (completeEl) completeEl.classList.add('done');

            const resultEl = document.getElementById('exam-result');
            resultEl.style.display = 'block';
            const metaParts = [];
            if (data.qualityScore) {
                const score = data.qualityScore;
                const cls = score >= 80 ? 'quality-high' : score >= 60 ? 'quality-mid' : 'quality-low';
                metaParts.push('<span>质量评分：<strong class="quality-badge ' + cls + '">' + score + '</strong></span>');
            }
            if (data.retrievedChunks) metaParts.push('<span>知识片段：' + data.retrievedChunks + '</span>');
            document.getElementById('exam-meta').innerHTML = metaParts.join('');
            if (data.examPaper) document.getElementById('exam-content').innerHTML = renderMarkdown(data.examPaper);
            if (data.answerKey) {
                document.getElementById('exam-answer-content').innerHTML = renderMarkdown(data.answerKey);
                document.getElementById('exam-answer-section').style.display = 'block';
            }
            btn.disabled = false;
            btn.textContent = '生成试卷';
            setStepState(3, 'done', '试卷已生成');
            toast('试卷生成完成', 'success');
            loadExamHistory(0);
        });

        eventSource.addEventListener('ERROR', (e) => {
            const data = JSON.parse(e.data);
            eventSource.close();
            document.getElementById('exam-error').textContent = '生成失败: ' + (data.errorMessage || '未知错误');
            document.getElementById('exam-error').classList.add('active');
            btn.disabled = false;
            btn.textContent = '生成试卷';
            refreshStepGate();
        });

        const waitForSse = () => new Promise((resolve) => {
            if (sseReady) { resolve(); return; }
            const check = () => { if (sseReady) resolve(); else setTimeout(check, 50); };
            check();
            setTimeout(resolve, 3000);
        });

        try {
            btn.textContent = '生成中...';
            await waitForSse();
            const body = { ..._lastExamRequest, sessionId: sessionId };
            const res = await fetch(API + '/api/agent/exam/generate-stream', {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
            });
            if (!res.ok) {
                const err = await res.json().catch(() => ({}));
                throw new Error(err.error || '启动生成失败');
            }
        } catch (e) {
            eventSource.close();
            document.getElementById('exam-error').textContent = '启动失败: ' + e.message;
            document.getElementById('exam-error').classList.add('active');
            btn.disabled = false;
            btn.textContent = '生成试卷';
            refreshStepGate();
        }
    }

    function toggleExamPanels() {
        _examThinkingVisible = !_examThinkingVisible;
        ['researcher', 'writer', 'answer', 'reviewer', 'calibrator', 'deduplicator', 'scoring'].forEach(a => {
            const panel = document.getElementById('exam-panel-' + a);
            if (panel) panel.classList.toggle('open', _examThinkingVisible);
        });
    }
    function copyExamPaper() {
        const content = document.getElementById('exam-content');
        if (!content) return;
        navigator.clipboard.writeText(content.innerText).then(() => toast('试卷已复制到剪贴板', 'success'));
    }
    async function exportExamWord() {
        if (!_lastExamRequest) { toast('请先生成试卷', 'error'); return; }
        toast('正在导出 Word 文档...', 'info');
        try {
            const res = await fetch(API + '/api/agent/exam/export-word', {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(_lastExamRequest)
            });
            if (!res.ok) throw new Error('导出失败');
            const blob = await res.blob();
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = _lastExamRequest.topic + '_试卷.docx';
            a.click();
            URL.revokeObjectURL(url);
            toast('Word 文档已下载', 'success');
        } catch (e) {
            toast('导出失败: ' + e.message, 'error');
        }
    }

    /* ---------- 出卷历史 ---------- */
    const EXAM_HISTORY_SIZE = 10;
    let examHistoryPage = 0;
    function goExamHistoryPage(p) { loadExamHistory(p); }

    // 试卷生命周期状态 → 徽章（阶段 1-D 校对关口）
    function paperStatusBadge(status) {
        const map = {
            PUBLISHED: ['已发布', 'badge-success'],
            REVIEWABLE: ['待校对', 'badge-warning'],
            VALIDATION_FAILED: ['校验未通过', 'badge-danger'],
            DRAFT: ['草稿', 'badge-info'],
            COMPLETED: ['成功', 'badge-success'],
            FAILED: ['生成失败', 'badge-danger']
        };
        const m = map[status] || [status || '-', 'badge-info'];
        return '<span class="badge ' + m[1] + '">' + KR.esc(m[0]) + '</span>';
    }

    async function loadExamHistory(page) {
        if (page == null) page = examHistoryPage;
        examHistoryPage = page;
        const tbody = document.getElementById('exam-history-list');
        tbody.innerHTML = '<tr><td colspan="6" class="loading">加载中...</td></tr>';
        try {
            const res = await fetch(API + '/api/agent/exam/history/page?page=' + page + '&size=' + EXAM_HISTORY_SIZE);
            const data = await res.json();
            const list = data.records || [];
            const total = data.total || 0;
            if (!list.length) {
                tbody.innerHTML = '<tr><td colspan="6" class="empty-state">暂无出卷记录</td></tr>';
                renderPager('exam-history-pager', page, EXAM_HISTORY_SIZE, total, 'goExamHistoryPage');
                return;
            }
            const diffMap = { EASY: '简单', MEDIUM: '中等', HARD: '困难' };
            tbody.innerHTML = list.map(h => {
                const statusLabel = paperStatusBadge(h.status);
                const score = h.qualityScore != null ? h.qualityScore : '-';
                const time = KR.fmtDateTime(h.createTime);
                const diff = diffMap[h.difficulty] || h.difficulty || '-';
                const t = h.topic && h.topic.length > 40 ? h.topic.substring(0, 40) + '...' : (h.topic || '-');
                return '<tr>'
                    + '<td title="' + esc(h.topic || '') + '">' + esc(t) + '</td>'
                    + '<td>' + diff + '</td>'
                    + '<td>' + statusLabel + '</td>'
                    + '<td>' + score + '</td>'
                    + '<td style="white-space:nowrap">' + time + '</td>'
                    + '<td style="white-space:nowrap">'
                    + '<button class="btn btn-outline btn-sm" onclick="showExamHistoryDetail(\'' + esc(h.sessionId) + '\')">查看</button> '
                    + '<button class="btn btn-outline btn-sm" onclick="exportHistoryWord(\'' + esc(h.sessionId) + '\')" title="导出 Word">导出</button> '
                    + '<button class="btn btn-outline btn-sm" style="color:var(--danger)" onclick="deleteExamHistory(\'' + esc(h.sessionId) + '\')" title="删除">删除</button>'
                    + '</td></tr>';
            }).join('');
            renderPager('exam-history-pager', page, EXAM_HISTORY_SIZE, total, 'goExamHistoryPage');
        } catch (e) {
            console.error('加载出卷历史失败', e);
            tbody.innerHTML = '<tr><td colspan="6" class="empty-state">加载失败</td></tr>';
        }
    }

    async function showExamHistoryDetail(sessionId) {
        const container = document.getElementById('history-detail-content');
        container.innerHTML = '<p class="loading">加载中...</p>';
        document.getElementById('history-modal-overlay').classList.add('active');
        try {
            const res = await fetch(API + '/api/agent/exam/history/' + encodeURIComponent(sessionId));
            if (!res.ok) throw new Error('加载失败');
            const h = await res.json();
            const statusLabel = paperStatusBadge(h.status);
            const time = h.createTime ? h.createTime.replace('T', ' ').substring(0, 19) : '-';
            const diffMap = { EASY: '简单', MEDIUM: '中等', HARD: '困难' };
            const diff = diffMap[h.difficulty] || h.difficulty || '-';
            let html = '<div style="margin-bottom:20px"><h3 style="margin:0 0 12px;font-size:18px">出卷详情</h3>'
                + '<div style="display:flex;gap:16px;flex-wrap:wrap;font-size:13px;color:var(--text-light)">'
                + '<span>状态: ' + statusLabel + '</span><span>难度: ' + diff + '</span>'
                + '<span>评分: ' + (h.qualityScore != null ? h.qualityScore : '-')
                + (h.qualityScore != null ? ' <a href="javascript:void(0)" onclick="showScoreRuleModal()" style="color:var(--primary);text-decoration:none;font-weight:600" title="查看分数计算规则">?</a>' : '') + '</span>'
                + '<span>检索分块: ' + (h.retrievedChunks != null ? h.retrievedChunks : '-') + '</span>'
                + '<span>时间: ' + time + '</span>'
                + (h.category ? '<span>分类: ' + esc(h.category) + '</span>' : '') + '</div></div>';
            html += buildScoreBreakdownHtml(h.scoreDetail);
            html += '<div style="margin-bottom:16px"><div style="font-weight:500;margin-bottom:6px">考试主题</div>'
                + '<div style="background:#f8fafc;padding:12px;border-radius:6px;border:1px solid var(--border);font-size:14px">' + esc(h.topic || '-') + '</div></div>';
            if (h.questionConfig) html += '<div style="margin-bottom:16px"><div style="font-weight:500;margin-bottom:6px">题型配置</div>'
                + '<div style="background:#f8fafc;padding:12px;border-radius:6px;border:1px solid var(--border);font-size:14px">' + esc(h.questionConfig) + '</div></div>';
            if (h.keyFindings) html += '<details style="margin-bottom:16px"><summary style="cursor:pointer;font-weight:500;font-size:14px;color:var(--text-light)">关键发现（研究员）</summary>'
                + '<div style="background:#f8fafc;padding:12px;border-radius:6px;border:1px solid var(--border);font-size:14px;white-space:pre-wrap;margin-top:8px">' + esc(h.keyFindings) + '</div></details>';
            if (h.examPaper) html += '<div style="margin-bottom:16px"><div style="font-weight:500;margin-bottom:6px">试卷内容</div>'
                + '<div class="article-content" style="font-size:14px">' + renderMarkdown(h.examPaper) + '</div></div>';
            if (h.answerKey) html += '<details style="margin-bottom:16px" open><summary style="cursor:pointer;font-weight:500;font-size:14px;color:var(--primary)">标准答案与评分标准</summary>'
                + '<div class="article-content" style="margin-top:8px;font-size:14px">' + renderMarkdown(h.answerKey) + '</div></details>';
            if (h.reviewFeedback) html += '<details style="margin-bottom:16px"><summary style="cursor:pointer;font-weight:500;font-size:14px;color:var(--text-light)">审核反馈</summary>'
                + '<div style="background:#f8fafc;padding:12px;border-radius:6px;border:1px solid var(--border);font-size:14px;white-space:pre-wrap;margin-top:8px">' + esc(h.reviewFeedback) + '</div></details>';
            if (h.difficultyAssessment) html += '<details style="margin-bottom:16px"><summary style="cursor:pointer;font-weight:500;font-size:14px;color:var(--text-light)">难度评估</summary>'
                + '<div style="background:#f8fafc;padding:12px;border-radius:6px;border:1px solid var(--border);font-size:14px;white-space:pre-wrap;margin-top:8px">' + esc(h.difficultyAssessment) + '</div></details>';
            if (h.deduplicationReport) html += '<details style="margin-bottom:16px"><summary style="cursor:pointer;font-weight:500;font-size:14px;color:var(--text-light)">查重报告</summary>'
                + '<div style="background:#f8fafc;padding:12px;border-radius:6px;border:1px solid var(--border);font-size:14px;white-space:pre-wrap;margin-top:8px">' + esc(h.deduplicationReport) + '</div></details>';
            if (h.errorMessage) html += '<div style="margin-bottom:16px"><div style="font-weight:500;margin-bottom:6px;color:#dc2626">错误信息</div>'
                + '<div style="background:#fef2f2;padding:12px;border-radius:6px;border:1px solid #fecaca;color:#dc2626;font-size:14px">' + esc(h.errorMessage) + '</div></div>';
            container.innerHTML = html;
        } catch (e) {
            container.innerHTML = '<p style="text-align:center;color:#dc2626">加载详情失败: ' + esc(e.message) + '</p>';
        }
    }
    function closeHistoryModal() { document.getElementById('history-modal-overlay').classList.remove('active'); }

    function showScoreRuleModal() {
        const m = document.getElementById('score-rule-modal');
        if (m) m.classList.add('active');
    }
    function closeScoreRuleModal() {
        const m = document.getElementById('score-rule-modal');
        if (m) m.classList.remove('active');
    }

    /** 六维度定义：key、中文名、权重（与后端 ExamReviewerAgent.WEIGHTS 保持一致） */
    const SCORE_DIMENSIONS = [
        { key: 'accuracy', label: '知识准确性', weight: 0.25 },
        { key: 'coverage', label: '知识点覆盖', weight: 0.25 },
        { key: 'wording', label: '题目表述', weight: 0.15 },
        { key: 'typeReasonable', label: '题型合理性', weight: 0.15 },
        { key: 'difficulty', label: '难度适当性', weight: 0.10 },
        { key: 'format', label: '格式规范性', weight: 0.10 }
    ];

    /**
     * 依据落库的 scoreDetail JSON 渲染"评分构成"（逐维度得分 × 权重 = 加权贡献，末行合计）。
     * 无明细（历史旧记录 / 模型未按格式输出）时返回空串。
     */
    function buildScoreBreakdownHtml(scoreDetailJson) {
        if (!scoreDetailJson) return '';
        let d;
        try { d = JSON.parse(scoreDetailJson); } catch (_) { return ''; }
        if (!d || typeof d !== 'object') return '';
        const rows = SCORE_DIMENSIONS.filter(x => typeof d[x.key] === 'number');
        if (!rows.length) return '';
        let calc = 0;
        const body = rows.map(function (x) {
            const s = d[x.key];
            const contrib = s * x.weight;
            calc += contrib;
            return '<div style="display:grid;grid-template-columns:1.6fr 0.7fr 1fr 1.1fr;gap:8px;align-items:center;padding:5px 8px;border-bottom:1px solid #eef2f7;font-size:13px">'
                + '<span style="font-weight:600;color:#1e293b">' + x.label + '</span>'
                + '<span style="color:#64748b">' + s + ' 分</span>'
                + '<span style="color:#94a3b8">× ' + x.weight.toFixed(2) + '</span>'
                + '<span style="text-align:right;font-weight:600;color:var(--primary)">' + contrib.toFixed(1) + '</span>'
                + '</div>';
        }).join('');
        const totalLine = (typeof d.total === 'number' ? d.total : Math.round(calc));
        return '<div style="margin-bottom:18px"><div style="font-weight:500;margin-bottom:6px">评分构成'
            + ' <a href="javascript:void(0)" onclick="showScoreRuleModal()" style="font-size:12px;font-weight:400;color:var(--primary);text-decoration:none">（计算规则 ?）</a></div>'
            + '<div style="border:1px solid var(--border);border-radius:8px;overflow:hidden">'
            + '<div style="display:grid;grid-template-columns:1.6fr 0.7fr 1fr 1.1fr;gap:8px;padding:6px 8px;background:#f8fafc;font-size:11px;font-weight:700;color:#64748b;text-transform:uppercase;letter-spacing:.4px;border-bottom:1px solid #eef2f7"><span>维度</span><span>得分</span><span>权重</span><span style="text-align:right">加权贡献</span></div>'
            + body
            + '<div style="display:flex;justify-content:space-between;align-items:center;padding:8px;font-size:13px;background:#f0fdf4;border-top:1px solid #bbf7d0">'
            + '<span style="font-weight:600;color:#166534">合计（四舍五入）</span>'
            + '<span style="font-weight:700;color:#166534">' + calc.toFixed(1) + ' → ' + totalLine + ' 分</span>'
            + '</div></div></div>';
    }

    async function deleteExamHistory(sessionId) {
        const ok = await showConfirm('确定要删除这条出卷记录吗？', { confirmText: '删除' });
        if (!ok) return;
        try {
            const res = await fetch(API + '/api/agent/exam/history/' + encodeURIComponent(sessionId), { method: 'DELETE' });
            if (!res.ok) throw new Error('删除失败');
            toast('出卷记录已删除', 'success');
            loadExamHistory();
        } catch (e) {
            console.error('删除出卷历史失败', e);
            toast('删除失败: ' + e.message, 'error');
        }
    }
    async function exportHistoryWord(sessionId) {
        try {
            const res = await fetch(API + '/api/agent/exam/history/' + encodeURIComponent(sessionId) + '/export-word', { method: 'POST' });
            if (!res.ok) throw new Error('导出失败');
            const blob = await res.blob();
            const disposition = res.headers.get('Content-Disposition') || '';
            let filename = '试卷.docx';
            const match = disposition.match(/filename\*?=(?:UTF-8'')?(.+)/);
            if (match) filename = decodeURIComponent(match[1]);
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url; a.download = filename;
            document.body.appendChild(a); a.click(); document.body.removeChild(a);
            URL.revokeObjectURL(url);
            toast('导出成功', 'success');
        } catch (e) {
            console.error('导出历史试卷失败', e);
            toast('导出失败: ' + e.message, 'error');
        }
    }

    /* ---------- 暴露给内联 onclick 的全局函数 ---------- */
    Object.assign(window, {
        generateExamPlan, validatePlan, balanceAndRevalidate, onPlanCountChange, evenSpreadType, evenSpreadAll,
        updatePlanSummary, markPlanManual, generateExamWithAgents, toggleExamPanels, copyExamPaper, exportExamWord,
        loadExamHistory, goExamHistoryPage, showExamHistoryDetail, deleteExamHistory,
        exportHistoryWord, closeHistoryModal, closeNodeModal, showNodeModal,
        showScoreRuleModal, closeScoreRuleModal
    });

    /* ---------- 初始化 ---------- */
    (function bindExamAgentPanelSync() {
        ['researcher', 'scoring', 'writer', 'answer', 'reviewer', 'calibrator', 'deduplicator'].forEach(ui => {
            const panel = document.getElementById('exam-panel-' + ui);
            if (!panel) return;
            const header = panel.querySelector('.agent-panel-header');
            if (!header) return;
            // 内联 onclick 已完成 classList.toggle，这里在捕获之后同步一次缓冲。
            header.addEventListener('click', () => {
                requestAnimationFrame(() => _examPanelOpenSync(ui));
            });
        });
    })();
    (function bindTopicReset() {
        const topicEl = document.getElementById('exam-topic');
        if (topicEl) {
            topicEl.addEventListener('change', () => {
                if (!currentExamPlan) return;
                currentExamPlan = null;
                const wrap = document.getElementById('exam-plan-wrap');
                if (wrap) wrap.innerHTML = '';
                const traceBox = document.getElementById('exam-plan-trace-box');
                if (traceBox) traceBox.style.display = 'none';
                refreshStepGate();
            });
        }
    })();
    /* 切换到「出卷历史」Tab 时主动拉取最新数据：common.js 的通用 Tab 只切换样式不刷新数据，
       避免工作台生成新卷后需手动刷新页面才能看到最新记录。 */
    (function bindHistoryTabRefresh() {
        const bar = document.querySelector('.tabs');
        if (!bar) return;
        bar.addEventListener('click', (e) => {
            const tab = e.target.closest('.tab');
            if (tab && tab.dataset.panel === 'history') {
                loadExamHistory(examHistoryPage);
            }
        });
    })();
    refreshStepGate();
    loadExamHistory(0);
})();
