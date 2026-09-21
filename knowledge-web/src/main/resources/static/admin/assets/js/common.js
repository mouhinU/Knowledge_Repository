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

    /* ---------- 管理端鉴权（无状态 JWT）：令牌存取 + fetch/EventSource 拦截 + 退出 ---------- */
    const AUTH_TOKEN_KEY = 'kr_admin_token';
    const LOGIN_PATH = '/admin/login.html';

    function getToken() {
        try { return sessionStorage.getItem(AUTH_TOKEN_KEY); } catch (e) { return null; }
    }

    function setToken(t) {
        try { if (t) sessionStorage.setItem(AUTH_TOKEN_KEY, t); } catch (e) { /* ignore */ }
    }

    function clearToken() {
        try { sessionStorage.removeItem(AUTH_TOKEN_KEY); } catch (e) { /* ignore */ }
    }

    function isLoginPage() {
        return location.pathname.indexOf(LOGIN_PATH) !== -1;
    }

    function redirectToLogin() {
        clearToken();
        if (!isLoginPage()) {
            location.replace(LOGIN_PATH + '?next=' + encodeURIComponent(location.pathname + location.search));
        }
    }

    /* 将既有 headers（对象 / 数组 / Headers / Request.headers）与 init.headers 合并为单一 Headers。 */
    function mergeHeaders(input, init) {
        const headers = new Headers();
        try {
            if (typeof Request !== 'undefined' && input instanceof Request) {
                input.headers.forEach(function (v, k) { headers.set(k, v); });
            }
        } catch (e) { /* ignore */ }
        const ih = init && init.headers;
        if (ih) {
            if (typeof Headers !== 'undefined' && ih instanceof Headers) {
                ih.forEach(function (v, k) { headers.set(k, v); });
            } else if (Array.isArray(ih)) {
                ih.forEach(function (kv) { headers.set(kv[0], kv[1]); });
            } else {
                Object.keys(ih).forEach(function (k) { headers.set(k, ih[k]); });
            }
        }
        return headers;
    }

    function isAuthEndpoint(url) {
        return /\/api\/admin\/auth\/(login|logout)/.test(url);
    }

    function isApiUrl(url) {
        return url.indexOf('/api/') !== -1;
    }

    /* fetch 拦截：注入 X-Admin-Token，命中 401 统一清令牌并跳登录。 */
    (function patchFetch() {
        if (typeof window.fetch !== 'function' || window.__krFetchPatched) return;
        window.__krFetchPatched = true;
        const originalFetch = window.fetch.bind(window);
        window.fetch = function (input, init) {
            init = init || {};
            const url = typeof input === 'string' ? input
                : (input && typeof input.url === 'string' ? input.url : String(input || ''));
            if (isApiUrl(url) && !isAuthEndpoint(url)) {
                const tok = getToken();
                if (tok) {
                    const headers = mergeHeaders(input, init);
                    headers.set('X-Admin-Token', tok);
                    init = Object.assign({}, init, { headers: headers });
                }
            }
            return originalFetch(input, init).then(function (res) {
                if (isApiUrl(url) && !isAuthEndpoint(url) && res && res.status === 401) {
                    redirectToLogin();
                }
                return res;
            });
        };
    })();

    /* EventSource 拦截：SSE 无法带自定义头，改在 URL 上追加 access_token 查询参数。 */
    (function patchEventSource() {
        if (typeof window.EventSource !== 'function' || window.__krEventSourcePatched) return;
        window.__krEventSourcePatched = true;
        const OriginalES = window.EventSource;
        window.EventSource = function (url, config) {
            let target = url;
            try {
                if (typeof target === 'string' && isApiUrl(target) && !isAuthEndpoint(target)) {
                    const tok = getToken();
                    if (tok) {
                        const sep = target.indexOf('?') === -1 ? '?' : '&';
                        target = target + sep + 'access_token=' + encodeURIComponent(tok);
                    }
                }
            } catch (e) { /* ignore */ }
            return new OriginalES(target, config);
        };
        window.EventSource.prototype = OriginalES.prototype;
        window.EventSource.CONNECTING = OriginalES.CONNECTING;
        window.EventSource.OPEN = OriginalES.OPEN;
        window.EventSource.CLOSED = OriginalES.CLOSED;
    })();

    /* 退出登录：通知服务端（失败也继续本地清理），清令牌后跳登录页。 */
    function logout() {
        const tok = getToken();
        const done = function () { clearToken(); location.replace(LOGIN_PATH); };
        if (!tok) { done(); return; }
        try {
            fetch(API + '/api/admin/auth/logout', {
                method: 'POST',
                headers: { 'X-Admin-Token': tok },
                // 直接用 fetch（已被拦截），但 logout 在鉴权放行清单内，令牌头不影响
            }).catch(function () { /* ignore */ }).then(done, done);
        } catch (e) { done(); }
    }

    function openChangePasswordModal() {
        const title = document.getElementById('modal-title');
        const body = document.getElementById('modal-body');
        if (title) title.textContent = '修改密码';
        if (body) {
            body.innerHTML =
                '<div class="form-group"><label class="form-label">原密码</label><input class="form-input" id="cp-old" type="password" autocomplete="current-password"></div>'
                + '<div class="form-group"><label class="form-label">新密码</label><input class="form-input" id="cp-new" type="password" autocomplete="new-password"></div>'
                + '<div class="form-group"><label class="form-label">确认新密码</label><input class="form-input" id="cp-confirm" type="password" autocomplete="new-password"></div>'
                + '<div class="login-err" id="cp-err" style="display:none"></div>'
                + '<button class="btn btn-primary" onclick="KR.submitChangePassword()">确认修改</button>';
        }
        openModal();
    }

    function submitChangePassword() {
        const errBox = document.getElementById('cp-err');
        function showErr(msg) { if (errBox) { errBox.textContent = msg; errBox.style.display = 'block'; } }
        const oldPwd = (document.getElementById('cp-old') || {}).value || '';
        const newPwd = (document.getElementById('cp-new') || {}).value || '';
        const confirmPwd = (document.getElementById('cp-confirm') || {}).value || '';
        if (errBox) errBox.style.display = 'none';
        if (!oldPwd || !newPwd) { showErr('请填写原密码与新密码'); return; }
        if (newPwd !== confirmPwd) { showErr('两次输入的新密码不一致'); return; }
        fetch(API + '/api/admin/auth/change-password', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ oldPassword: oldPwd, newPassword: newPwd })
        }).then(function (res) {
            return res.json().catch(function () { return {}; }).then(function (data) { return { res: res, data: data }; });
        }).then(function (r) {
            if (r.res.ok) {
                closeModal();
                toast('密码已修改，请使用新密码重新登录', 'success');
                setTimeout(function () { clearToken(); location.replace(LOGIN_PATH); }, 1200);
            } else {
                showErr((r.data && r.data.error) || '密码修改失败');
            }
        }).catch(function () { showErr('请求失败，请稍后重试'); });
    }

    const AUTH = {
        TOKEN_KEY: AUTH_TOKEN_KEY,
        LOGIN_PATH: LOGIN_PATH,
        getToken: getToken, setToken: setToken, clearToken: clearToken,
        isLoginPage: isLoginPage, redirectToLogin: redirectToLogin, logout: logout
    };


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
                { key: 'paper-review', label: '试卷校对', icon: '❐', href: 'paper-review.html' },
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

        const actions = document.getElementById('topbar-actions');
        if (actions) {
            let user = '';
            try { user = sessionStorage.getItem('kr_admin_user') || ''; } catch (e) { /* ignore */ }
            const esc2 = (user || '管理员').replace(/[&<>"']/g, function (c) {
                return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
            });
            actions.innerHTML =
                '<span class="user-chip" title="当前登录">' + esc2 + '</span>'
                + '<button class="btn btn-ghost btn-sm" id="kr-chpwd-btn">修改密码</button>'
                + '<button class="btn btn-outline btn-sm" id="kr-logout-btn">退出登录</button>';
            const cpwBtn = document.getElementById('kr-chpwd-btn');
            if (cpwBtn) cpwBtn.addEventListener('click', openChangePasswordModal);
            const btn = document.getElementById('kr-logout-btn');
            if (btn) btn.addEventListener('click', logout);
        }
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
        if (!isLoginPage() && !getToken()) {
            redirectToLogin();
            return;
        }
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
        // 显式转义 5 个实体（& 必须最先）。DOM textContent→innerHTML 技巧只处理 & < >，
        // 不转义引号，一旦把结果拼进带引号的属性（value/title/onclick）即可被 " 截断注入（SEC-3）。
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
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

    /* ---------- 看图题配图渲染（错题本 / 成绩复核复用；公开流 /api/exam/assets/{key}，无需令牌） ---------- */
    function renderImages(images) {
        if (!images || !Array.isArray(images) || images.length === 0) return '';
        var html = '<div class="kr-imgs">';
        images.forEach(function (key) {
            if (!key || typeof key !== 'string') return;
            var url = '/api/exam/assets/' + encodeURIComponent(key);
            var safe = esc(url);
            html += '<img class="kr-img" src="' + safe + '" alt="题目配图" loading="lazy"'
                + ' onclick="KR.openImgLightbox(\'' + safe + '\')">';
        });
        html += '</div>';
        return html;
    }
    function openImgLightbox(src) {
        var box = document.getElementById('kr-img-lightbox');
        if (!box) {
            box = document.createElement('div');
            box.id = 'kr-img-lightbox';
            box.className = 'kr-img-lightbox';
            box.onclick = closeImgLightbox;
            box.innerHTML = '<span class="kr-img-close" onclick="KR.closeImgLightbox()">&times;</span>'
                + '<img id="kr-img-lightbox-img" alt="题目配图">';
            document.body.appendChild(box);
        }
        var img = document.getElementById('kr-img-lightbox-img');
        if (img) img.src = src;
        box.classList.add('open');
    }
    function closeImgLightbox() {
        var box = document.getElementById('kr-img-lightbox');
        if (box) box.classList.remove('open');
    }

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
        auth: AUTH,
        submitChangePassword: submitChangePassword,
        openChangePasswordModal: openChangePasswordModal,
        initLayout: initLayout,
        activateTab: activateTab,
        initTabs: initTabs,
        findNav: findNav,
        esc: esc, escHtml: esc, toast: toast, openModal: openModal, closeModal: closeModal,
        showConfirm: showConfirm, statusBadge: statusBadge, formatSize: formatSize,
        formatElapsed: formatElapsed, formatTime: formatTime, fmtDateTime: fmtDateTime,
        renderMarkdown: renderMarkdown, renderPager: renderPager, distributeInt: distributeInt,
        renderImages: renderImages, openImgLightbox: openImgLightbox, closeImgLightbox: closeImgLightbox,
        uuid: uuid
    };
    window.KR = KR;
    // 全局别名：与旧代码保持一致，页面脚本可直接使用这些裸函数名
    ['esc', 'escHtml', 'toast', 'openModal', 'closeModal', 'showConfirm', 'statusBadge',
        'formatSize', 'formatElapsed', 'formatTime', 'renderMarkdown', 'renderPager',
        'renderImages', 'openImgLightbox', 'closeImgLightbox',
        'distributeInt'].forEach(function (fn) { window[fn] = KR[fn]; });
    window.generateId = KR.uuid;
})();
