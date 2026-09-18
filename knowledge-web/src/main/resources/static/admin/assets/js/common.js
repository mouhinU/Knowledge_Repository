/* ==========================================================================
   Knowledge Repository · Admin Console — 共享脚本 (common.js)
   职责：统一布局（分组侧边栏 + 顶栏面包屑）、通用工具函数、Tab 组件、
         Toast / 模态框 / 确认弹窗外壳。各页面通过全局函数直接复用。
   @author Knowledge-Repository
   @date 2026-09-16
   ========================================================================== */
(function () {
    'use strict';

    window.API = '';

    /* ---------- 导航结构（两级：分组 → 页面） ---------- */
    const NAV = [
        {
            group: '概览', items: [
                { key: 'dashboard', label: '仪表盘', icon: '▦', href: 'index.html' }
            ]
        },
        {
            group: '知识库', items: [
                { key: 'documents', label: '文档管理', icon: '☰', href: 'documents.html' },
                { key: 'search', label: '知识检索', icon: '⌕', href: 'search.html' }
            ]
        },
        {
            group: 'AI 工坊', items: [
                { key: 'ai-writing', label: 'AI 写作', icon: '✎', href: 'ai-writing.html' },
                { key: 'ai-exam', label: 'AI 出卷', icon: '▤', href: 'ai-exam.html' }
            ]
        },
        {
            group: '考试管理', items: [
                { key: 'exam-review', label: '成绩复核', icon: '✓', href: 'exam-review.html' },
                { key: 'wrong-answers', label: '错题库', icon: '⚑', href: 'wrong-answers.html' }
            ]
        },
        {
            group: '系统管理', items: [
                { key: 'users', label: '用户管理', icon: '◍', href: 'users.html' },
                { key: 'departments', label: '部门管理', icon: '⌂', href: 'departments.html' },
                { key: 'system', label: '系统配置', icon: '⚙', href: 'system.html' }
            ]
        }
    ];

    function findNav(key) {
        for (const g of NAV) {
            for (const it of g.items) {
                if (it.key === key) return { group: g.group, item: it };
            }
        }
        return null;
    }

    /* ---------- 布局渲染 ---------- */
    function renderSidebar(activeKey) {
        let html = '';
        html += '<div class="sidebar-header"><h1><span class="logo-dot">K</span>Knowledge Repo</h1><p>RAG 知识库管理台</p></div>';
        html += '<nav class="sidebar-nav">';
        for (const g of NAV) {
            html += '<div class="nav-group"><div class="nav-group-title">' + g.group + '</div>';
            for (const it of g.items) {
                const active = it.key === activeKey ? ' active' : '';
                html += '<a class="nav-item' + active + '" href="' + it.href + '">'
                    + '<span class="nav-icon">' + it.icon + '</span>'
                    + '<span class="nav-label">' + it.label + '</span></a>';
            }
            html += '</div>';
        }
        html += '</nav>';
        const sb = document.getElementById('sidebar');
        if (sb) sb.innerHTML = html;
    }

    function renderTopbar(activeKey) {
        const found = findNav(activeKey);
        const tb = document.getElementById('topbar');
        if (!tb) return;
        const group = found ? found.group : '管理';
        const label = found ? found.item.label : '控制台';
        tb.innerHTML =
            '<div style="display:flex;align-items:center;gap:14px">'
            + '<button class="menu-toggle" id="menu-toggle" aria-label="菜单">☰</button>'
            + '<div class="breadcrumb">'
            + '<span class="bc-group">' + group + '</span>'
            + '<span class="bc-sep">/</span>'
            + '<span class="bc-current">' + label + '</span>'
            + '</div></div>'
            + '<div class="topbar-right" id="topbar-actions"></div>';
        const mt = document.getElementById('menu-toggle');
        if (mt) mt.addEventListener('click', toggleSidebar);
    }

    function toggleSidebar() {
        const sb = document.getElementById('sidebar');
        const bd = document.getElementById('sidebar-backdrop');
        if (!sb) return;
        const open = sb.classList.toggle('open');
        if (bd) bd.classList.toggle('show', open);
    }

    function injectShells() {
        if (!document.getElementById('toast-root')) {
            const t = document.createElement('div');
            t.id = 'toast-root';
            document.body.appendChild(t);
        }
        if (!document.getElementById('modal-overlay')) {
            const m = document.createElement('div');
            m.className = 'modal-overlay';
            m.id = 'modal-overlay';
            m.innerHTML = '<div class="modal"><div class="modal-header"><h3 id="modal-title">对话框</h3>'
                + '<button class="modal-close" onclick="closeModal()">×</button></div>'
                + '<div id="modal-body"></div></div>';
            m.addEventListener('click', function (e) { if (e.target === m) closeModal(); });
            document.body.appendChild(m);
        }
        if (!document.getElementById('confirm-overlay')) {
            const c = document.createElement('div');
            c.className = 'confirm-overlay';
            c.id = 'confirm-overlay';
            c.innerHTML = '<div class="confirm-box"><div class="confirm-icon" id="confirm-icon">⚠</div>'
                + '<div class="confirm-msg" id="confirm-msg"></div><div class="confirm-btns">'
                + '<button class="btn btn-outline" id="confirm-cancel-btn">取消</button>'
                + '<button class="btn btn-danger" id="confirm-ok-btn">确认</button></div></div>';
            document.body.appendChild(c);
        }
        const bd = document.getElementById('sidebar-backdrop');
        if (bd) bd.addEventListener('click', toggleSidebar);
    }

    function initLayout(activeKey) {
        renderSidebar(activeKey);
        renderTopbar(activeKey);
        injectShells();
        initTabs(document);
    }

    /* ---------- Tab 组件 ----------
       结构：.tabs 内含若干 .tab[data-panel=x]，与 .tab-panel#panel-x 同级于同一容器。 */
    function initTabs(scope) {
        (scope || document).querySelectorAll('.tabs').forEach(function (bar) {
            if (bar._tabsBound || bar.hasAttribute('data-manual')) return;
            bar._tabsBound = true;
            bar.addEventListener('click', function (e) {
                const tab = e.target.closest('.tab');
                if (!tab || !bar.contains(tab)) return;
                const container = bar.parentElement;
                container.querySelectorAll('.tab').forEach(function (t) { t.classList.remove('active'); });
                tab.classList.add('active');
                container.querySelectorAll('.tab-panel').forEach(function (p) {
                    p.classList.toggle('active', p.id === 'panel-' + tab.dataset.panel);
                });
            });
        });
    }

    // 全局程序化切换（适用于单 Tab 组页面，如 AI 出卷）
    function activateTab(panelKey) {
        document.querySelectorAll('.tab').forEach(function (t) {
            if (t.closest('.tabs[data-manual]')) return;
            t.classList.toggle('active', t.dataset.panel === panelKey);
        });
        document.querySelectorAll('.tab-panel').forEach(function (p) {
            p.classList.toggle('active', p.id === 'panel-' + panelKey);
        });
    }

    /* ---------- 通用工具 ---------- */
    function esc(str) {
        if (str == null) return '';
        const d = document.createElement('div');
        d.textContent = String(str);
        return d.innerHTML;
    }

    function toast(msg, type) {
        const root = document.getElementById('toast-root');
        if (!root) { console.log('[toast]', type, msg); return; }
        const el = document.createElement('div');
        el.className = 'toast toast-' + (type || 'info');
        el.textContent = msg;
        root.appendChild(el);
        setTimeout(function () {
            el.classList.add('leaving');
            setTimeout(function () { el.remove(); }, 250);
        }, 3000);
    }

    function openModal() { document.getElementById('modal-overlay').classList.add('active'); }
    function closeModal() { const m = document.getElementById('modal-overlay'); if (m) m.classList.remove('active'); }

    function showConfirm(msg, opts) {
        return new Promise(function (resolve) {
            const overlay = document.getElementById('confirm-overlay');
            const iconEl = document.getElementById('confirm-icon');
            const msgEl = document.getElementById('confirm-msg');
            const okBtn = document.getElementById('confirm-ok-btn');
            const cancelBtn = document.getElementById('confirm-cancel-btn');
            const o = opts || {};
            iconEl.innerHTML = o.icon || '⚠';
            msgEl.textContent = msg;
            okBtn.textContent = o.confirmText || '确认';
            okBtn.className = 'btn ' + (o.confirmClass || 'btn-danger');
            function cleanup() {
                overlay.classList.remove('active');
                okBtn.removeEventListener('click', onOk);
                cancelBtn.removeEventListener('click', onCancel);
            }
            function onOk() { cleanup(); resolve(true); }
            function onCancel() { cleanup(); resolve(false); }
            okBtn.addEventListener('click', onOk);
            cancelBtn.addEventListener('click', onCancel);
            overlay.classList.add('active');
            okBtn.focus();
        });
    }

    function statusBadge(s) {
        return { INDEXED: 'success', PROCESSING: 'warning', FAILED: 'danger', UPLOADED: 'info' }[s] || 'info';
    }

    function formatSize(bytes) {
        if (bytes == null) return '0 B';
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
        return (bytes / 1048576).toFixed(1) + ' MB';
    }

    function formatElapsed(ms) {
        if (ms < 10000) return (ms / 1000).toFixed(1) + 's';
        const s = Math.round(ms / 1000);
        return s < 60 ? s + 's' : Math.floor(s / 60) + 'm' + (s % 60) + 's';
    }

    function formatTime(ts) {
        if (!ts) return '-';
        const d = new Date(ts);
        return d.toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' })
            + '.' + String(d.getMilliseconds()).padStart(3, '0');
    }

    function fmtDateTime(s) {
        return s ? String(s).replace('T', ' ').substring(0, 16) : '-';
    }

    /* 轻量 Markdown 渲染（无第三方依赖） */
    function renderMarkdown(md) {
        if (!md) return '';
        let html = esc(md);
        html = html.replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>');
        html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>');
        html = html.replace(/^## (.+)$/gm, '<h2>$1</h2>');
        html = html.replace(/^# (.+)$/gm, '<h1>$1</h1>');
        html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
        html = html.replace(/\*(.+?)\*/g, '<em>$1</em>');
        html = html.replace(/`([^`]+)`/g, '<code>$1</code>');
        html = html.replace(/^&gt; (.+)$/gm, '<blockquote>$1</blockquote>');
        html = html.replace(/^[-*] (.+)$/gm, '<li>$1</li>');
        html = html.replace(/(<li>.*<\/li>\n?)+/g, '<ul>$&</ul>');
        html = html.replace(/^\d+\. (.+)$/gm, '<li>$1</li>');
        html = html.replace(/\n\n/g, '</p><p>');
        html = '<p>' + html + '</p>';
        html = html.replace(/<p>\s*(<h[123]>)/g, '$1');
        html = html.replace(/(<\/h[123]>)\s*<\/p>/g, '$1');
        html = html.replace(/<p>\s*(<ul>)/g, '$1');
        html = html.replace(/(<\/ul>)\s*<\/p>/g, '$1');
        html = html.replace(/<p>\s*(<pre>)/g, '$1');
        html = html.replace(/(<\/pre>)\s*<\/p>/g, '$1');
        html = html.replace(/<p>\s*(<blockquote>)/g, '$1');
        html = html.replace(/(<\/blockquote>)\s*<\/p>/g, '$1');
        html = html.replace(/<p>\s*<\/p>/g, '');
        html = html.replace(/\n/g, '<br>');
        return html;
    }

    /* 通用分页器：page 从 0 开始，gotoFnName 全局函数签名 fn(page) */
    function renderPager(containerId, page, size, total, gotoFnName) {
        const el = document.getElementById(containerId);
        if (!el) return;
        const totalPages = Math.max(1, Math.ceil(total / size));
        const cur = Math.min(page, totalPages - 1);
        const from = total === 0 ? 0 : cur * size + 1;
        const to = Math.min(total, (cur + 1) * size);
        el.className = 'pager';
        el.innerHTML =
            '<span class="pager-info">共 ' + total + ' 条 · 第 ' + (cur + 1) + '/' + totalPages + ' 页'
            + (total > 0 ? '（' + from + '-' + to + '）' : '') + '</span>'
            + '<span style="display:flex;gap:8px">'
            + '<button class="pager-btn" ' + (cur <= 0 ? 'disabled' : 'onclick="' + gotoFnName + '(' + (cur - 1) + ')"') + '>上一页</button>'
            + '<button class="pager-btn" ' + (cur >= totalPages - 1 ? 'disabled' : 'onclick="' + gotoFnName + '(' + (cur + 1) + ')"') + '>下一页</button>'
            + '</span>';
    }

    /* 将 total 拆成 n 份，尽量均匀且和恒等于 total */
    function distributeInt(total, n) {
        if (n <= 0) return [];
        const base = Math.floor(total / n);
        let rem = total - base * n;
        const arr = [];
        for (let i = 0; i < n; i++) {
            let v = base + (i >= n - rem ? 1 : 0);
            if (v < 1) v = 1;
            arr.push(v);
        }
        let sum = arr.reduce(function (a, b) { return a + b; }, 0);
        while (sum > total) {
            const idx = arr.indexOf(Math.max.apply(null, arr));
            if (arr[idx] <= 1) break;
            arr[idx]--; sum--;
        }
        while (sum < total) {
            const idx = arr.indexOf(Math.min.apply(null, arr));
            arr[idx]++; sum++;
        }
        return arr;
    }

    function uuid() {
        if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID();
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
            const r = Math.random() * 16 | 0;
            return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
        });
    }

    /* ---------- 挂载到 window（供各页面内联脚本以全局函数名直接调用） ---------- */
    const KR = {
        NAV: NAV,
        initLayout: initLayout,
        activateTab: activateTab,
        initTabs: initTabs,
        findNav: findNav,
        esc: esc, escHtml: esc, toast: toast, openModal: openModal, closeModal: closeModal,
        showConfirm: showConfirm, statusBadge: statusBadge, formatSize: formatSize,
        formatElapsed: formatElapsed, formatTime: formatTime, fmtDateTime: fmtDateTime,
        renderMarkdown: renderMarkdown, renderPager: renderPager, distributeInt: distributeInt,
        uuid: uuid
    };
    window.KR = KR;
    // 全局别名：与旧代码保持一致，页面脚本可直接使用这些裸函数名
    ['esc', 'escHtml', 'toast', 'openModal', 'closeModal', 'showConfirm', 'statusBadge',
        'formatSize', 'formatElapsed', 'formatTime', 'renderMarkdown', 'renderPager',
        'distributeInt'].forEach(function (fn) { window[fn] = KR[fn]; });
    window.generateId = KR.uuid;
})();
