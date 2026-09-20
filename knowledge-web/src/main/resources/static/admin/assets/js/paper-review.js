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

    // —— 配图选择器状态（阶段 2）——
    let pickerNumber = null;        // 当前正在配图的题号
    let pickerSel = [];             // 有序已选图片 [{assetKey,url,name}]
    let pickerTab = 'global';       // 'global' 全局搜索 | 'document' 按文档
    let docListCache = null;        // 文档下拉数据缓存 [{documentKey,fileName}]
    let questionsByNumber = {};     // 当前试卷题目缓存 {题号: question}

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
        questionsByNumber = {};
        questions.forEach(function (q) { questionsByNumber[q.questionNumber] = q; });
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
            + imagesHtml(q)
            + '<div class="pr-q-actions"><button class="btn btn-primary btn-sm" onclick="saveQuestion(' + num + ', this)">保存本题</button></div>'
            + '</div>';
    }

    // 渲染某题的配图区：缩略图（按 assetKey 走公开流端点）+「配置配图」入口
    function imagesHtml(q) {
        const keys = parseImages(q.imagesJson);
        const thumbs = keys.length
            ? '<div class="pr-img-thumbs">' + keys.map(function (k) {
                return '<img src="' + esc(imageUrl(k)) + '" alt="配图" loading="lazy">';
            }).join('') + '</div>'
            : '<div class="pr-img-none">尚未配图</div>';
        return '<div class="pr-imgs">'
            + '<div class="pr-imgs-head"><span>配图' + (keys.length ? '（' + keys.length + '）' : '') + '</span>'
            + '<button class="btn btn-outline btn-sm" onclick="openImagePicker(' + q.questionNumber + ')">配置配图</button></div>'
            + thumbs + '</div>';
    }

    function imageUrl(assetKey) {
        return API + '/api/exam/assets/' + encodeURIComponent(assetKey);
    }

    // images_json 存的是 assetKey 有序字符串数组；容错解析
    function parseImages(imagesJson) {
        if (!imagesJson) return [];
        try {
            const arr = JSON.parse(imagesJson);
            if (!Array.isArray(arr)) return [];
            return arr.filter(function (x) { return x != null && String(x).trim(); }).map(String);
        } catch (e) {
            return [];
        }
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
            // 校验通过并发布成功后，返回待校对列表（该卷已 PUBLISHED，会从列表移除）
            closeDetail();
        } catch (e) {
            KR.toast('发布失败：' + e.message, 'error');
        }
    }

    /* ---------------- 重新切分 ---------------- */
    async function doResplit() {
        if (!currentSessionId) return;
        const ok = await KR.showConfirm('按试卷原文重新切分会覆盖当前结构化题目行（含已编辑的答案）；人工绑定的配图将按题号尽量保留。确认？', {
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

    /* ---------------- 配图选择器 ---------------- */
    function openImagePicker(number) {
        if (!currentSessionId) return;
        pickerNumber = number;
        const q = questionsByNumber[number] || {};
        // 以既有绑定的 assetKey 初始化有序已选（url 由句柄重建，name 暂用句柄）
        pickerSel = parseImages(q.imagesJson).map(function (k) {
            return { assetKey: k, url: imageUrl(k), name: '' };
        });
        renderImagePicker();
    }

    function switchImageTab(tab) {
        pickerTab = tab;
        renderImagePicker();
    }

    function renderImagePicker() {
        const title = document.getElementById('modal-title');
        if (title) title.textContent = '配置配图 · 第 ' + pickerNumber + ' 题';
        const body = document.getElementById('modal-body');
        if (!body) return;
        body.innerHTML =
            '<div class="ip-tabs">'
            + '<button class="ip-tab ' + (pickerTab === 'global' ? 'active' : '') + '" onclick="switchImageTab(\'global\')">全局搜索</button>'
            + '<button class="ip-tab ' + (pickerTab === 'document' ? 'active' : '') + '" onclick="switchImageTab(\'document\')">按文档选图</button>'
            + '</div>'
            + (pickerTab === 'global'
                ? '<div class="ip-tools"><input class="form-input" id="ip-keyword" onkeydown="if(event.key===\'Enter\'){event.preventDefault();searchGlobalImages()}" placeholder="输入关键词匹配来源文档名 / 文档Key，回车或点检索">'
                    + '<button class="btn btn-primary btn-sm" onclick="searchGlobalImages()">检索</button></div>'
                : '<div class="ip-tools"><select class="form-input" id="ip-doc"></select>'
                    + '<button class="btn btn-primary btn-sm" onclick="searchByDocument()">加载该文档配图</button></div>')
            + '<div class="ip-grid" id="ip-grid"><div class="ip-empty">加载中…</div></div>'
            + '<div class="ip-sel"><div class="ip-sel-label">已选配图（拖顺序号即展示顺序）：</div>'
            + '<div class="ip-sel-row" id="ip-sel-row"></div></div>'
            + '<div class="ip-foot"><button class="btn btn-outline" onclick="closeImagePicker()">取消</button>'
            + '<button class="btn btn-primary" onclick="saveImagePicker(this)">保存配图</button></div>';
        renderSelected();
        KR.openModal();
        if (pickerTab === 'document') { loadDocOptions(); } else { searchGlobalImages(); }
    }

    async function loadDocOptions() {
        const sel = document.getElementById('ip-doc');
        if (!sel) return;
        try {
            if (!docListCache) {
                const res = await fetch(API + '/api/admin/document/list');
                const arr = await res.json();
                docListCache = Array.isArray(arr) ? arr : (arr.records || []);
            }
            if (!docListCache.length) { sel.innerHTML = '<option value="">（知识库暂无文档）</option>'; return; }
            sel.innerHTML = docListCache.map(function (d) {
                const label = d.fileName || d.documentKey;
                return '<option value="' + esc(d.documentKey) + '">' + esc(label) + '</option>';
            }).join('');
        } catch (e) {
            sel.innerHTML = '<option value="">（文档列表加载失败）</option>';
        }
    }

    async function searchGlobalImages() {
        const kwEl = document.getElementById('ip-keyword');
        const kw = kwEl ? kwEl.value.trim() : '';
        const url = API + '/api/admin/exam-images/search?limit=60&offset=0'
            + (kw ? '&keyword=' + encodeURIComponent(kw) : '');
        await runImageSearch(url);
    }

    async function searchByDocument() {
        const sel = document.getElementById('ip-doc');
        const key = sel ? sel.value : '';
        if (!key) { KR.toast('请先选择一篇文档', 'error'); return; }
        const url = API + '/api/admin/exam-images/search?limit=120&offset=0&documentKey=' + encodeURIComponent(key);
        await runImageSearch(url);
    }

    async function runImageSearch(url) {
        const grid = document.getElementById('ip-grid');
        if (grid) grid.innerHTML = '<div class="ip-empty">加载中…</div>';
        try {
            const res = await fetch(url);
            const data = await res.json();
            if (!res.ok) { if (grid) grid.innerHTML = '<div class="ip-empty">' + esc(data.error || '检索失败') + '</div>'; return; }
            loadImageGrid(data.records || []);
        } catch (e) {
            if (grid) grid.innerHTML = '<div class="ip-empty">检索失败：' + esc(e.message) + '</div>';
        }
    }

    // 网格数据缓存，供点击按 assetKey 取回完整元信息
    let pickerGridData = [];

    function loadImageGrid(records) {
        pickerGridData = records;
        const grid = document.getElementById('ip-grid');
        if (!grid) return;
        if (!records.length) { grid.innerHTML = '<div class="ip-empty">没有匹配的图片</div>'; return; }
        grid.innerHTML = records.map(function (r, i) {
            const sel = isPicked(r.assetKey) ? ' sel' : '';
            const cap = esc(r.sourceDocumentName || '') + (r.pageNo ? (' · 第' + esc(r.pageNo) + '页') : '');
            return '<div class="ip-cell' + sel + '" data-i="' + i + '" onclick="togglePickByIndex(' + i + ')">'
                + '<img src="' + esc(r.url) + '" alt="配图" loading="lazy">'
                + '<div class="ip-cap">' + cap + '</div>'
                + '</div>';
        }).join('');
    }

    function isPicked(assetKey) {
        return pickerSel.some(function (x) { return x.assetKey === assetKey; });
    }

    function togglePickByIndex(i) {
        const r = pickerGridData[i];
        if (r) togglePick({ assetKey: r.assetKey, url: r.url, name: r.sourceDocumentName || '' });
    }

    function togglePick(image) {
        const idx = pickerSel.findIndex(function (x) { return x.assetKey === image.assetKey; });
        if (idx >= 0) {
            pickerSel.splice(idx, 1);
        } else {
            if (pickerSel.length >= 20) { KR.toast('单题配图最多 20 张', 'error'); return; }
            pickerSel.push(image);
        }
        // 同步刷新网格选中态 + 已选条
        const cell = document.querySelector('.ip-cell[data-i="' + pickerGridData.findIndex(function (r) { return r.assetKey === image.assetKey; }) + '"]');
        if (cell) cell.classList.toggle('sel', isPicked(image.assetKey));
        renderSelected();
    }

    function renderSelected() {
        const row = document.getElementById('ip-sel-row');
        if (!row) return;
        if (!pickerSel.length) { row.innerHTML = '<div class="ip-empty">尚未选择，点击上方缩略图添加</div>'; return; }
        row.innerHTML = pickerSel.map(function (x, i) {
            return '<div class="ip-sel-item">'
                + '<span class="ip-order">' + (i + 1) + '</span>'
                + '<button class="ip-rm" onclick="removePick(' + i + ')">×</button>'
                + '<img src="' + esc(x.url) + '" alt="已选">'
                + '<div class="ip-mv">'
                + (i > 0 ? '<button onclick="movePick(' + i + ',-1)" title="前移">‹</button>' : '')
                + (i < pickerSel.length - 1 ? '<button onclick="movePick(' + i + ',1)" title="后移">›</button>' : '')
                + '</div>'
                + '</div>';
        }).join('');
    }

    function removePick(i) {
        const removed = pickerSel.splice(i, 1)[0];
        if (removed) {
            const gi = pickerGridData.findIndex(function (r) { return r.assetKey === removed.assetKey; });
            if (gi >= 0) { const cell = document.querySelector('.ip-cell[data-i="' + gi + '"]'); if (cell) cell.classList.remove('sel'); }
        }
        renderSelected();
    }

    function movePick(i, dir) {
        const j = i + dir;
        if (j < 0 || j >= pickerSel.length) return;
        const tmp = pickerSel[i]; pickerSel[i] = pickerSel[j]; pickerSel[j] = tmp;
        renderSelected();
    }

    async function saveImagePicker(btn) {
        if (!currentSessionId || pickerNumber == null) return;
        if (btn) btn.disabled = true;
        const assetKeys = pickerSel.map(function (x) { return x.assetKey; });
        try {
            const res = await fetch(API + '/api/admin/paper-review/' + encodeURIComponent(currentSessionId)
                + '/question/' + pickerNumber + '/images', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ assetKeys: assetKeys })
            });
            const data = await res.json();
            if (!res.ok) { KR.toast(data.error || '保存配图失败', 'error'); return; }
            KR.toast('第 ' + pickerNumber + ' 题配图已保存', 'success');
            // 更新缓存与卡片（就地重绘配图区，避免整卷重拉）
            if (questionsByNumber[pickerNumber]) questionsByNumber[pickerNumber].imagesJson = JSON.stringify(data.assetKeys || assetKeys);
            const card = document.querySelector('.pr-q[data-num="' + pickerNumber + '"]');
            if (card) {
                const old = card.querySelector('.pr-imgs');
                const holder = document.createElement('div');
                holder.innerHTML = imagesHtml({ questionNumber: pickerNumber, imagesJson: JSON.stringify(data.assetKeys || assetKeys) });
                if (old && holder.firstChild) card.replaceChild(holder.firstChild, old);
            }
            closeImagePicker();
        } catch (e) {
            KR.toast('保存配图失败：' + e.message, 'error');
        } finally {
            if (btn) btn.disabled = false;
        }
    }

    function closeImagePicker() {
        pickerNumber = null;
        pickerSel = [];
        pickerGridData = [];
        KR.closeModal();
    }

    // 暴露给内联 onclick
    window.loadPending = loadPending;
    window.openReview = openReview;
    window.closeDetail = closeDetail;
    window.saveQuestion = saveQuestion;
    window.doApprove = doApprove;
    window.doResplit = doResplit;
    window.openImagePicker = openImagePicker;
    window.switchImageTab = switchImageTab;
    window.searchGlobalImages = searchGlobalImages;
    window.searchByDocument = searchByDocument;
    window.togglePickByIndex = togglePickByIndex;
    window.removePick = removePick;
    window.movePick = movePick;
    window.saveImagePicker = saveImagePicker;
    window.closeImagePicker = closeImagePicker;

    loadPending();
})();
