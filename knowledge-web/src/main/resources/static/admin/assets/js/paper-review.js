/* ==========================================================================
   Knowledge Repository · Admin — 试卷校对 (paper-review.js)
   职责：待校对试卷列表 → 结构化逐题校对（编辑答案 / 分值 / 解析）→
         就地回写 kb_exam_question → 契约校验实时反馈 → 校对通过发布 / 重新切分。
   @author Knowledge-Repository
   @date 2026-09-18
   ========================================================================== */
(function () {
    'use strict';

    KR.initLayout('paper-review');

    let currentSessionId = null;

    const TYPE_LABEL = {
        SINGLE_CHOICE: '单选题', MULTI_CHOICE: '多选题', TRUE_FALSE: '判断题',
        FILL_BLANK: '填空题', SHORT_ANSWER: '简答题', ESSAY: '论述题'
    };

    const STATUS_BADGE = {
        REVIEWABLE: ['待校对', 'badge-warning'],
        VALIDATION_FAILED: ['校验未通过', 'badge-danger'],
        PUBLISHED: ['已发布', 'badge-success'],
        DRAFT: ['草稿', 'badge-info'],
        FAILED: ['生成失败', 'badge-danger']
    };

    function esc(v) { return KR.esc(v); }
    function typeLabel(t) { return TYPE_LABEL[t] || t || '-'; }
    function statusBadge(s) {
        const m = STATUS_BADGE[s] || [s || '-', 'badge-info'];
        return '<span class="badge ' + m[1] + '">' + esc(m[0]) + '</span>';
    }

    /* ---------------- 待校对列表 ---------------- */
    async function loadPending() {
        const tbody = document.getElementById('pending-list');
        tbody.innerHTML = '<tr><td colspan="7" class="loading">加载中...</td></tr>';
        try {
            const res = await fetch(API + '/api/admin/paper-review/pending?limit=100&offset=0');
            const data = await res.json();
            const records = data.records || [];
            document.getElementById('pending-count').textContent = data.total != null ? data.total : records.length;
            if (!records.length) {
                tbody.innerHTML = '<tr><td colspan="7" class="empty-state">暂无待校对试卷</td></tr>';
                return;
            }
            const diffMap = { EASY: '简单', MEDIUM: '中等', HARD: '困难' };
            tbody.innerHTML = records.map(function (r) {
                const topic = r.topic && r.topic.length > 40 ? r.topic.substring(0, 40) + '…' : (r.topic || '-');
                return '<tr>'
                    + '<td title="' + esc(r.topic || '') + '">' + esc(topic) + '</td>'
                    + '<td>' + esc(diffMap[r.difficulty] || r.difficulty || '-') + '</td>'
                    + '<td>' + esc(r.category || '-') + '</td>'
                    + '<td>' + (r.qualityScore != null ? r.qualityScore : '-') + '</td>'
                    + '<td>' + statusBadge(r.status) + '</td>'
                    + '<td style="white-space:nowrap">' + esc(KR.fmtDateTime(r.createTime)) + '</td>'
                    + '<td style="white-space:nowrap"><button class="btn btn-primary btn-sm" onclick="openReview(\'' + esc(r.sessionId) + '\')">校对</button></td>'
                    + '</tr>';
            }).join('');
        } catch (e) {
            console.error('加载待校对列表失败', e);
            tbody.innerHTML = '<tr><td colspan="7" class="empty-state" style="color:var(--danger)">加载失败</td></tr>';
        }
    }

    /* ---------------- 校对详情 ---------------- */
    async function openReview(sessionId) {
        currentSessionId = sessionId;
        document.getElementById('list-card').style.display = 'none';
        const detail = document.getElementById('detail-card');
        detail.style.display = 'block';
        document.getElementById('detail-questions').innerHTML = '<div class="pr-empty">加载中…</div>';
        document.getElementById('detail-validation').innerHTML = '';
        document.getElementById('detail-summary').innerHTML = '';
        try {
            const res = await fetch(API + '/api/admin/paper-review/' + encodeURIComponent(sessionId) + '/questions');
            if (!res.ok) throw new Error('HTTP ' + res.status);
            const data = await res.json();
            renderSummary(data);
            renderValidation(data);
            renderQuestions(data.questions || []);
        } catch (e) {
            console.error('加载校对详情失败', e);
            document.getElementById('detail-questions').innerHTML = '<div class="pr-empty" style="color:var(--danger)">加载失败：' + esc(e.message) + '</div>';
        }
    }

    function closeDetail() {
        currentSessionId = null;
        document.getElementById('detail-card').style.display = 'none';
        document.getElementById('list-card').style.display = 'block';
        loadPending();
    }

    function renderSummary(d) {
        const diffMap = { EASY: '简单', MEDIUM: '中等', HARD: '困难' };
        document.getElementById('detail-title').textContent = '试卷校对：' + (d.topic || '');
        const reviewed = d.reviewedBy ? ('　已发布 by ' + esc(d.reviewedBy) + ' @ ' + esc(KR.fmtDateTime(d.reviewedTime))) : '';
        document.getElementById('detail-summary').innerHTML =
            '<span>状态: ' + statusBadge(d.status) + '</span>'
            + '<span>难度: ' + esc(diffMap[d.difficulty] || d.difficulty || '-') + '</span>'
            + '<span>质量分: <b>' + (d.qualityScore != null ? d.qualityScore : '-') + '</b></span>'
            + '<span>时长: ' + (d.durationMinutes ? esc(d.durationMinutes) + ' 分钟' : '不限') + '</span>'
            + '<span>题目数: <b>' + (d.questions ? d.questions.length : 0) + '</b></span>'
            + '<span>校对要求: ' + (d.reviewRequired ? '强制人工' : '可自动发布') + '</span>'
            + (reviewed ? '<span style="color:var(--success, #16a34a)">' + reviewed + '</span>' : '');
    }

    function renderValidation(d) {
        const box = document.getElementById('detail-validation');
        if (d.pass) {
            box.className = 'pr-validation pass';
            box.innerHTML = '✅ 出卷契约校验通过，可点击「校对通过并发布」。';
        } else {
            const issues = (d.issues || []).map(function (i) { return '<li>' + esc(i) + '</li>'; }).join('');
            box.className = 'pr-validation fail';
            box.innerHTML = '❌ 出卷契约校验未通过，须修正后方可发布：<ul>' + (issues || '<li>未知问题</li>') + '</ul>';
        }
    }

    function renderQuestions(questions) {
        const host = document.getElementById('detail-questions');
        if (!questions.length) {
            host.innerHTML = '<div class="pr-empty">该试卷无结构化题目行，可点「重新切分」回灌。</div>';
            return;
        }
        host.innerHTML = questions.map(renderQuestionCard).join('');
    }

    function renderQuestionCard(q) {
        const num = q.questionNumber;
        const opts = parseOptions(q.optionsJson);
        const optsHtml = opts.length
            ? '<ul class="pr-opts">' + opts.map(function (o) {
                const isCorrect = isOptionCorrect(q, o.key, o.value);
                return '<li class="' + (isCorrect ? 'correct' : '') + '"><b>' + esc(o.key) + '.</b> ' + esc(o.value) + '</li>';
            }).join('') + '</ul>'
            : '';
        const secLabel = q.sectionLabel ? '<span class="pr-q-sec">' + esc(q.sectionLabel) + '</span>' : '';
        return '<div class="pr-q" data-num="' + num + '">'
            + '<div class="pr-q-head">'
            + '<span class="pr-q-idx">' + esc(num) + '</span>'
            + secLabel
            + '<span class="pr-q-type">' + esc(typeLabel(q.questionType)) + '</span>'
            + '<span class="pr-q-score">满分 ' + esc(q.maxScore != null ? q.maxScore : '-') + ' 分</span>'
            + '</div>'
            + '<div class="pr-stem">' + esc(q.stem || '') + '</div>'
            + optsHtml
            + '<div class="pr-fields">'
            + '<div class="row">'
            + '<label class="f-answer">标准答案<input class="form-input pr-in-answer" type="text" value="' + esc(q.correctAnswer || '') + '"></label>'
            + '<label class="f-score">分值(分)<input class="form-input pr-in-score" type="number" min="1" value="' + esc(q.maxScore != null ? q.maxScore : '') + '"></label>'
            + '</div>'
            + '<label>解析 / 说明<textarea class="form-input pr-in-analysis" rows="2">' + esc(q.analysis || '') + '</textarea></label>'
            + '</div>'
            + '<div class="pr-q-actions"><button class="btn btn-primary btn-sm" onclick="saveQuestion(' + num + ', this)">保存本题</button></div>'
            + '</div>';
    }

    // 选项落库为 JSON 对象数组 [{key,value}]（兼容早期纯字符串数组）；统一归一化为 {key, value}
    function parseOptions(optionsJson) {
        if (!optionsJson) return [];
        try {
            const arr = JSON.parse(optionsJson);
            if (!Array.isArray(arr)) return [];
            return arr.map(function (x, idx) {
                const fallbackKey = String.fromCharCode(65 + idx);
                if (x && typeof x === 'object') {
                    const key = x.key != null ? String(x.key) : fallbackKey;
                    const value = x.value != null ? String(x.value)
                        : (x.label != null ? String(x.label) : String(x.text != null ? x.text : ''));
                    return { key: key, value: value };
                }
                return { key: fallbackKey, value: String(x) };
            });
        } catch (e) {
            return [];
        }
    }

    // 高亮正确选项（尽力而为：选择题按答案字母匹配选项 key，判断题按选项文本推断）
    function isOptionCorrect(q, letter, optionText) {
        const ans = (q.correctAnswer || '').toUpperCase();
        if (!ans) return false;
        if (q.questionType === 'TRUE_FALSE') {
            const t = (optionText || '');
            const truthy = /正确|对|是|√|T/.test(t) && !/错误|错|否|×/.test(t);
            const ansTrue = /正确|对|是|√|^T$|TRUE/.test(ans);
            return truthy === ansTrue && (ansTrue ? truthy : !truthy);
        }
        return ans.indexOf(String(letter).toUpperCase()) >= 0;
    }

    /* ---------------- 就地保存单题 ---------------- */
    async function saveQuestion(number, btn) {
        const card = document.querySelector('.pr-q[data-num="' + number + '"]');
        if (!card || !currentSessionId) return;
        const answer = card.querySelector('.pr-in-answer').value;
        const analysis = card.querySelector('.pr-in-analysis').value;
        const scoreRaw = card.querySelector('.pr-in-score').value;
        const maxScore = scoreRaw === '' ? null : parseInt(scoreRaw, 10);
        if (btn) { btn.disabled = true; }
        try {
            const res = await fetch(API + '/api/admin/paper-review/' + encodeURIComponent(currentSessionId)
                + '/question/' + number, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ correctAnswer: answer, analysis: analysis, maxScore: maxScore })
            });
            const data = await res.json();
            if (!res.ok) { KR.toast(data.error || '保存失败', 'error'); return; }
            KR.toast('第 ' + number + ' 题已保存', 'success');
            if (data.pass != null) {
                renderValidation({ pass: data.pass, issues: data.issues });
            }
        } catch (e) {
            KR.toast('保存失败：' + e.message, 'error');
        } finally {
            if (btn) { btn.disabled = false; }
        }
    }

    /* ---------------- 批准发布 ---------------- */
    async function doApprove() {
        if (!currentSessionId) return;
        const ok = await KR.showConfirm('确认校对通过并发布该试卷？发布后学生即可开考。', {
            confirmText: '确认发布', confirmClass: 'btn-primary', icon: '✓'
        });
        if (!ok) return;
        try {
            const res = await fetch(API + '/api/admin/paper-review/' + encodeURIComponent(currentSessionId) + '/approve?reviewer=admin', {
                method: 'POST'
            });
            const data = await res.json();
            if (!res.ok) {
                KR.toast(data.error || '发布失败', 'error');
                if (data.issues) renderValidation({ pass: false, issues: data.issues });
                return;
            }
            KR.toast('试卷已发布', 'success');
            openReview(currentSessionId);
        } catch (e) {
            KR.toast('发布失败：' + e.message, 'error');
        }
    }

    /* ---------------- 重新切分 ---------------- */
    async function doResplit() {
        if (!currentSessionId) return;
        const ok = await KR.showConfirm('按试卷原文重新切分会覆盖当前结构化题目行（含已编辑的答案），确认？', {
            confirmText: '确认重切', confirmClass: 'btn-danger', icon: '⚠'
        });
        if (!ok) return;
        try {
            const res = await fetch(API + '/api/admin/paper-review/' + encodeURIComponent(currentSessionId) + '/resplit', {
                method: 'POST'
            });
            const data = await res.json();
            if (!res.ok) { KR.toast(data.error || '重切失败', 'error'); return; }
            KR.toast('重新切分完成：' + (data.count || 0) + ' 题', 'success');
            openReview(currentSessionId);
        } catch (e) {
            KR.toast('重切失败：' + e.message, 'error');
        }
    }

    // 暴露给内联 onclick
    window.loadPending = loadPending;
    window.openReview = openReview;
    window.closeDetail = closeDetail;
    window.saveQuestion = saveQuestion;
    window.doApprove = doApprove;
    window.doResplit = doResplit;

    loadPending();
})();
