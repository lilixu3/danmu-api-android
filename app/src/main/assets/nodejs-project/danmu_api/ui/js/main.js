// language=JavaScript
export const mainJsContent = /* javascript */ `
/* ========================================
   全局变量定义
   ======================================== */
let envVariables = {};
let currentCategory = 'api';
let editingKey = null;
let logs = [];
let currentVersion = '';
let latestVersion = '';
let currentToken = 'globals.currentToken';
let currentAdminToken = '';
let originalToken = '87654321';

/* ========================================
   移动端 viewport/软键盘兼容
   - 修复：输入框聚焦后按钮被“挤出视口”看起来像消失
   - 修复：部分移动端浏览器/内置 WebView 偶发重绘导致按钮/头部不显示
   ======================================== */
function syncAppViewportHeight() {
    try {
        const vv = window.visualViewport;
        const height = (vv && vv.height) ? vv.height : window.innerHeight;
        if (!height) return;
        document.documentElement.style.setProperty('--app-vh', (height * 0.01) + 'px');
    } catch (e) {}
}

function initMobileViewportFixes() {
    // 首次同步
    syncAppViewportHeight();

    // 监听软键盘弹出/收起（visualViewport 更准确）
    try {
        if (window.visualViewport) {
            window.visualViewport.addEventListener('resize', syncAppViewportHeight);
            window.visualViewport.addEventListener('scroll', syncAppViewportHeight);
        }
    } catch (e) {}

    window.addEventListener('resize', syncAppViewportHeight);

    // 聚焦输入框时，尽量把对应的操作按钮保持在可视区域内（尤其是“开始匹配/搜索”等按钮）
    document.addEventListener('focusin', function(e) {
        // 仅移动端启用，避免桌面端滚动干扰
        if (window.innerWidth > 767) return;

        const target = e.target;
        if (!target) return;

        const tag = (target.tagName || '').toUpperCase();
        if (tag !== 'INPUT' && tag !== 'TEXTAREA' && tag !== 'SELECT') return;

        requestAnimationFrame(() => {
            try {
                syncAppViewportHeight();

                const vv = window.visualViewport;
                const viewportHeight = (vv && vv.height) ? vv.height : window.innerHeight;
                const padding = 16;

                // 优先在弹幕测试面板/搜索输入组内找按钮
                const scope = target.closest('.danmu-method-panel') ||
                              target.closest('.search-input-group') ||
                              target.closest('.input-group') ||
                              target.closest('.form-card') ||
                              target.parentElement;

                let btn = null;
                if (scope) {
                    // 先找大按钮（开始匹配/搜索），再降级
                    btn = scope.querySelector('button.btn.btn-lg') || scope.querySelector('button.btn');
                }

                const checkEl = btn || target;
                const rect = checkEl.getBoundingClientRect();
                const bottomLimit = viewportHeight - padding;

                if (rect.bottom > bottomLimit) {
                    const delta = rect.bottom - bottomLimit;
                    window.scrollBy({ top: delta, left: 0, behavior: 'smooth' });
                }
            } catch (err) {}
        });
    }, true);
}


/* ========================================
   主题切换功能
   ======================================== */
function initTheme() {
    const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    const savedTheme = localStorage.getItem('theme') || document.documentElement.getAttribute('data-theme') || (prefersDark ? 'dark' : 'light');
    document.documentElement.setAttribute('data-theme', savedTheme);
    document.documentElement.style.backgroundColor = savedTheme === 'dark' ? '#0A0F1E' : '#ffffff';
    document.documentElement.style.colorScheme = savedTheme;
    
    // 添加主题切换动画
    const themeToggle = document.getElementById('theme-toggle');
    if (themeToggle) {
        themeToggle.style.opacity = '0';
        themeToggle.style.transform = 'scale(0.8)';
        setTimeout(() => {
            themeToggle.style.transition = 'all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1)';
            themeToggle.style.opacity = '1';
            themeToggle.style.transform = 'scale(1)';
        }, 300);
    }
    addLog(\`已加载\${savedTheme === 'dark' ? '深色' : '浅色'}主题 ✨\`, 'info');
}

function toggleTheme() {
    const currentTheme = document.documentElement.getAttribute('data-theme');
    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
    
    // 添加页面过渡效果
    document.body.style.transition = 'background 0.3s ease';
    
    document.documentElement.setAttribute('data-theme', newTheme);
    localStorage.setItem('theme', newTheme);
    
    const themeButton = document.getElementById('theme-toggle');
    themeButton.style.transform = 'scale(0.8) rotate(360deg)';
    
    // 创建主题切换涟漪效果
    const ripple = document.createElement('div');
    ripple.style.cssText = \`
        position: fixed;
        border-radius: 50%;
        background: \${newTheme === 'dark' ? 'rgba(0, 0, 0, 0.8)' : 'rgba(255, 255, 255, 0.8)'};
        width: 20px;
        height: 20px;
        left: \${themeButton.offsetLeft + themeButton.offsetWidth / 2}px;
        top: \${themeButton.offsetTop + themeButton.offsetHeight / 2}px;
        transform: translate(-50%, -50%);
        pointer-events: none;
        z-index: 9999;
        animation: themeRipple 0.6s ease-out;
    \`;
    
    const style = document.createElement('style');
    style.textContent = \`
        @keyframes themeRipple {
            to {
                width: 3000px;
                height: 3000px;
                opacity: 0;
            }
        }
    \`;
    document.head.appendChild(style);
    document.body.appendChild(ripple);
    
    setTimeout(() => {
        themeButton.style.transform = '';
        ripple.remove();
        style.remove();
    }, 600);
    
    addLog(\`已切换到\${newTheme === 'dark' ? '深色' : '浅色'}主题 🎨\`, 'success');
}


/* ========================================
   部署平台环境变量状态指示器
   ======================================== */
let deployEnvStatus = {
    platform: 'node',
    platformLabel: 'Node.js',
    requiredVars: [],
    missingVars: [],
    lastUpdated: 0
};

function getDeployPlatformLabel(platform) {
    const p = (platform || 'node').toString().toLowerCase();
    const map = {
        vercel: 'Vercel',
        netlify: 'Netlify',
        edgeone: 'EdgeOne (腾讯云 Pages)',
        cloudflare: 'Cloudflare',
        docker: '本地/Docker',
        node: '本地/Docker',
        nodejs: '本地/Docker'
    };
    return map[p] || (platform || 'Unknown');
}

function getDeployRequiredVars(platform) {
    const p = (platform || 'node').toString().toLowerCase();
    if (p === 'vercel' || p === 'edgeone') {
        return ['DEPLOY_PLATFROM_PROJECT', 'DEPLOY_PLATFROM_TOKEN'];
    }
    if (p === 'netlify' || p === 'cloudflare') {
        return ['DEPLOY_PLATFROM_ACCOUNT', 'DEPLOY_PLATFROM_PROJECT', 'DEPLOY_PLATFROM_TOKEN'];
    }
    // 本地/Docker 部署不需要额外部署变量，修改配置后自动生效
    return [];
}

function readEnvValue(config, key) {
    try {
        if (config && config.originalEnvVars && Object.prototype.hasOwnProperty.call(config.originalEnvVars, key)) {
            return config.originalEnvVars[key];
        }
        if (config && config.envs && Object.prototype.hasOwnProperty.call(config.envs, key)) {
            return config.envs[key];
        }
    } catch (e) {}
    return '';
}

function computeDeployEnvStatus(config) {
    const platformRaw = (config && config.envs && (config.envs.deployPlatform || config.envs.DEPLOY_PLATFORM)) || 'node';
    const platform = (platformRaw || 'node').toString().toLowerCase();
    const requiredVars = getDeployRequiredVars(platform);
    const missingVars = requiredVars.filter(v => {
        const val = readEnvValue(config, v);
        return !val || (typeof val === 'string' && val.trim() === '');
    });

    return {
        platform,
        platformLabel: getDeployPlatformLabel(platform),
        requiredVars,
        missingVars
    };
}

function applyDeployEnvStatusToBadge(status) {
    const badge = document.getElementById('mobile-status');
    const dot = document.getElementById('deploy-env-status-dot') || (badge ? badge.querySelector('.status-dot') : null);
    if (!badge || !dot) return;

    const ok = !status.missingVars || status.missingVars.length === 0;

    dot.classList.remove('status-running', 'status-warning', 'status-error');
    dot.classList.add(ok ? 'status-running' : 'status-error');

    const titleOk = '部署平台 ' + status.platformLabel + '：所需环境变量已配置';
    const titleBad = '部署平台 ' + status.platformLabel + '：缺少 ' + (status.missingVars ? status.missingVars.length : 0) + ' 项必需环境变量';
    badge.title = ok ? titleOk : titleBad;

    badge.setAttribute('data-deploy-ok', ok ? '1' : '0');
}

function updateDeployEnvStatusBadgeFromConfig(config) {
    const status = computeDeployEnvStatus(config || {});
    deployEnvStatus = Object.assign({}, deployEnvStatus, status, { lastUpdated: Date.now() });
    applyDeployEnvStatusToBadge(deployEnvStatus);
}

async function refreshDeployEnvStatusBadge(force = false) {
    try {
        const now = Date.now();
        if (!force && deployEnvStatus.lastUpdated && (now - deployEnvStatus.lastUpdated) < 5000) {
            applyDeployEnvStatusToBadge(deployEnvStatus);
            return deployEnvStatus;
        }

        const response = await fetch(buildApiUrl('/api/config'));
        const config = await response.json();
        const status = computeDeployEnvStatus(config);

        deployEnvStatus = Object.assign({}, deployEnvStatus, status, { lastUpdated: now });
        applyDeployEnvStatusToBadge(deployEnvStatus);
        return deployEnvStatus;
    } catch (e) {
        console.error('获取部署平台环境变量状态失败:', e);
        // 网络异常时显示红色
        deployEnvStatus = Object.assign({}, deployEnvStatus, { missingVars: ['UNKNOWN'], lastUpdated: Date.now() });
        applyDeployEnvStatusToBadge(deployEnvStatus);
        return deployEnvStatus;
    }
}

function closeDeployEnvStatusModal() {
    const modal = document.getElementById('deploy-env-status-modal');
    if (modal) modal.classList.remove('active');
}

async function openDeployEnvStatusModal() {
    const modal = document.getElementById('deploy-env-status-modal');
    const body = document.getElementById('deploy-env-status-body');
    if (!modal || !body) return;

    const status = await refreshDeployEnvStatusBadge(true);
    const ok = !status.missingVars || status.missingVars.length === 0;

    const iconSvgOk = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">' +
        '<path d="M20 6L9 17l-5-5" stroke-linecap="round" stroke-linejoin="round"/>' +
    '</svg>';

    const iconSvgBad = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">' +
        '<path d="M12 9v4" stroke-linecap="round"/>' +
        '<path d="M12 17h.01" stroke-linecap="round"/>' +
        '<path d="M10.29 3.86l-7.4 12.82A2 2 0 004.62 20h14.76a2 2 0 001.73-3.32l-7.4-12.82a2 2 0 00-3.42 0z" stroke-linejoin="round"/>' +
    '</svg>';

    const heroClass = ok ? 'deploy-env-status-hero success' : 'deploy-env-status-hero error';
    const heroTitle = ok ? '所需环境变量已配置' : '存在未配置的必需环境变量';
    const heroSubtitle = ok
        ? ('当前部署平台为 ' + status.platformLabel + '，按钮显示绿色表示部署平台所需变量已满足。')
        : ('当前部署平台为 ' + status.platformLabel + '，请补全以下必需变量后再尝试重新部署或相关管理操作。');

    let varsHtml = '';
    if (!status.requiredVars || status.requiredVars.length === 0) {
        varsHtml = '<div class="deploy-env-status-hint">该部署平台无需额外配置 <span class="deploy-env-code">DEPLOY_PLATFROM_*</span> 相关变量。</div>';
    } else {
        varsHtml = '<div class="deploy-env-status-grid">' +
            status.requiredVars.map(function(k) {
                const missing = status.missingVars && status.missingVars.indexOf(k) !== -1;
                return '<div class="deploy-env-var-item">' +
                        '<div class="deploy-env-var-name">' + k + '</div>' +
                        '<div class="deploy-env-var-status ' + (missing ? 'missing' : 'ok') + '">' +
                            (missing ? '未配置' : '已配置') +
                        '</div>' +
                    '</div>';
            }).join('') +
        '</div>';
    }

    let missingHint = '';
    if (!ok && status.missingVars && status.missingVars.length > 0 && status.missingVars[0] !== 'UNKNOWN') {
        missingHint = '<div class="deploy-env-status-hint">缺失项：' +
            status.missingVars.map(function(v) { return '<span class="deploy-env-code">' + v + '</span>'; }).join(' ') +
        '</div>';
    }

    if (!ok && status.missingVars && status.missingVars.length > 0 && status.missingVars[0] === 'UNKNOWN') {
        missingHint = '<div class="deploy-env-status-hint">当前无法获取配置状态，请检查网络或 API 端点是否可访问。</div>';
    }

    body.innerHTML =
        '<div class="' + heroClass + '">' +
            '<div class="deploy-env-status-hero-content">' +
                '<div class="deploy-env-status-hero-icon">' + (ok ? iconSvgOk : iconSvgBad) + '</div>' +
                '<div>' +
                    '<p class="deploy-env-status-hero-title">' + heroTitle + '</p>' +
                    '<div class="deploy-env-status-hero-subtitle">' + heroSubtitle + '</div>' +
                    '<div class="deploy-env-status-chip">' +
                        '<span>平台：</span><strong>' + status.platformLabel + '</strong>' +
                        '<span style="margin-left: 8px;">状态：</span><strong>' + (ok ? '✅ 正常' : '❌ 异常') + '</strong>' +
                    '</div>' +
                '</div>' +
            '</div>' +
        '</div>' +
        varsHtml +
        missingHint;

    modal.classList.add('active');

    const modalContainer = modal.querySelector('.modal-container');
    if (modalContainer) {
        modalContainer.style.animation = 'modalSlideIn 0.4s cubic-bezier(0.34, 1.56, 0.64, 1)';
    }
}

/* ========================================
   侧边栏切换
   ======================================== */
function toggleSidebar() {
    const sidebar = document.getElementById('sidebar');
    const isActive = sidebar.classList.contains('active');
    
    sidebar.classList.toggle('active');
    
    // 点击遮罩关闭侧边栏
    if (!isActive) {
        const overlay = document.createElement('div');
        overlay.className = 'sidebar-overlay';
        overlay.style.cssText = \`
            position: fixed;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background: rgba(0,0,0,0.6);
            backdrop-filter: blur(4px);
            z-index: 999;
            animation: overlayFadeIn 0.3s ease-out;
        \`;
        overlay.onclick = toggleSidebar;
        document.body.appendChild(overlay);
        
        // 添加动画样式
        if (!document.getElementById('overlay-animation-styles')) {
            const style = document.createElement('style');
            style.id = 'overlay-animation-styles';
            style.textContent = \`
                @keyframes overlayFadeIn {
                    from { opacity: 0; }
                    to { opacity: 1; }
                }
                @keyframes overlayFadeOut {
                    from { opacity: 1; }
                    to { opacity: 0; }
                }
            \`;
            document.head.appendChild(style);
        }
    } else {
        const overlay = document.querySelector('.sidebar-overlay');
        if (overlay) {
            overlay.style.animation = 'overlayFadeOut 0.3s ease-out';
            setTimeout(() => overlay.remove(), 300);
        }
    }
}

/* ========================================
   导航切换
   ======================================== */
function switchSection(section) {
    // 检查是否尝试访问受token保护的section
    if (section === 'logs' || section === 'api' || section === 'env' || section === 'push') {
        const urlPath = window.location.pathname;
        const pathParts = urlPath.split('/').filter(part => part !== '');
        const urlToken = pathParts.length > 0 ? pathParts[0] : '';
        
        if (!urlToken && originalToken !== "87654321") {
            setTimeout(() => {
                const protocol = window.location.protocol;
                const host = window.location.host;
                customAlert('请在URL中配置相应的TOKEN以访问此功能！\\n\\n访问方式：' + protocol + '//' + host + '/{TOKEN}', '🔒 需要认证');
            }, 100);
            return;
        }
        
        if (section === 'env') {
            checkDeployPlatformConfig().then(result => {
                if (!result.success) {
                    setTimeout(() => {
                        customAlert(result.message, '⚙️ 配置提示');
                    }, 100);
                } else {
                    performSectionSwitch(section);
                }
            });
            return;
        }
    }
    
    performSectionSwitch(section);
}

function performSectionSwitch(section, isInitialLoad = false) {
    // 移除所有active类
    document.querySelectorAll('.content-section').forEach(s => {
        s.classList.remove('active');
        s.style.opacity = '0';
    });
    document.querySelectorAll('.nav-item').forEach(b => b.classList.remove('active'));
    
    // 添加active类
    const targetSection = document.getElementById(section + '-section');
    if (targetSection) {
        targetSection.classList.add('active');
        // 如果是初始化加载，直接显示，不走淡入动画避免闪烁
        targetSection.style.opacity = isInitialLoad ? '1' : '1'; 
    }
    
    const activeNav = document.querySelector(\`[data-section="\${section}"]\`);
    if (activeNav) activeNav.classList.add('active');
    
    // 更新移动端标题
    const titles = {
        preview: { main: '配置预览', sub: 'Configuration' },
        logs: { main: '日志查看', sub: 'System Logs' },
        api: { main: '接口调试', sub: 'API Testing' },
        push: { main: '推送弹幕', sub: 'Push Danmu' },
        env: { main: '系统配置', sub: 'Settings' }
    };
    const mobileTitle = document.getElementById('mobile-title');
    const mobileSubtitle = document.getElementById('mobile-subtitle');
    if (mobileTitle && titles[section]) {
        mobileTitle.textContent = titles[section].main;
        if (mobileSubtitle) {
            mobileSubtitle.textContent = titles[section].sub;
        }
    }
    
    // 只有在非初始化加载且是移动端时才触发侧边栏切换
    if (!isInitialLoad && window.innerWidth <= 768) {
        toggleSidebar();
    }
    
    // 滚动到顶部
    if (!isInitialLoad) {
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }
    
    const sectionTitle = (titles && titles[section] && titles[section].main) ? titles[section].main : section;
    if (!isInitialLoad) {
        addLog(\`切换到\${sectionTitle}模块 📍\`, 'info');
    }
    
    // 保存当前页面到存储，以便刷新后恢复
    // 安全优化：受 TOKEN/ADMIN_TOKEN 保护的页面仅使用 sessionStorage 记忆，避免关闭页面后仍“卡在管理页”
    const protectedSections = ['logs', 'api', 'env', 'push'];
    try {
        if (protectedSections.includes(section)) {
            sessionStorage.setItem('activeSection', section);
            localStorage.removeItem('activeSection');
        } else {
            localStorage.setItem('activeSection', section);
            sessionStorage.setItem('activeSection', section);
        }
    } catch (e) {
        // 忽略存储异常（隐私模式/禁用存储等）
    }
}

/* ========================================
   类别切换
   ======================================== */
function switchCategory(category) {
    currentCategory = category;
    
    // 添加切换动画
    const envList = document.getElementById('env-list');
    envList.style.opacity = '0';
    envList.style.transform = 'translateY(20px)';
    
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    event.target.classList.add('active');
    
    setTimeout(() => {
        renderEnvList();
        envList.style.transition = 'all 0.3s ease';
        envList.style.opacity = '1';
        envList.style.transform = 'translateY(0)';
    }, 150);
}

/* ========================================
   自定义弹窗组件
   ======================================== */
function createCustomAlert() {
    if (document.getElementById('custom-alert-overlay')) {
        return;
    }

    const alertHTML = \`
        <div class="modal-overlay" id="custom-alert-overlay">
            <div class="modal-container" style="max-width: 480px;">
                <div class="modal-header">
                    <h3 class="modal-title" id="custom-alert-title">💡 提示</h3>
                    <button class="modal-close" id="custom-alert-close">×</button>
                </div>
                <div class="modal-body">
                    <p id="custom-alert-message" style="color: var(--text-secondary); margin: 0; line-height: 1.7;"></p>
                </div>
                <div class="modal-footer">
                    <button class="btn btn-primary" id="custom-alert-confirm">
                        <span>确定</span>
                    </button>
                </div>
            </div>
        </div>
    \`;

    document.body.insertAdjacentHTML('beforeend', alertHTML);

    const overlay = document.getElementById('custom-alert-overlay');
    const closeBtn = document.getElementById('custom-alert-close');
    const confirmBtn = document.getElementById('custom-alert-confirm');

    function closeAlert() {
        overlay.classList.remove('active');
        setTimeout(() => {
            document.getElementById('custom-alert-title').textContent = '💡 提示';
        }, 300);
    }

    closeBtn.addEventListener('click', closeAlert);
    confirmBtn.addEventListener('click', closeAlert);

    overlay.addEventListener('click', function(e) {
        if (e.target === overlay) {
            closeAlert();
        }
    });
}

function customAlert(message, title = '💡 提示') {
    createCustomAlert();
    initMobileViewportFixes();
    const overlay = document.getElementById('custom-alert-overlay');
    const titleElement = document.getElementById('custom-alert-title');
    const messageElement = document.getElementById('custom-alert-message');

    titleElement.textContent = title;
    messageElement.textContent = message;
    overlay.classList.add('active');
}

function customConfirm(message, title = '❓ 确认') {
    return new Promise((resolve) => {
        createCustomAlert();
    initMobileViewportFixes();
        const overlay = document.getElementById('custom-alert-overlay');
        const titleElement = document.getElementById('custom-alert-title');
        const messageElement = document.getElementById('custom-alert-message');
        const confirmBtn = document.getElementById('custom-alert-confirm');

        const newConfirmBtn = confirmBtn.cloneNode(true);
        confirmBtn.parentNode.replaceChild(newConfirmBtn, confirmBtn);

        titleElement.textContent = title;
        messageElement.textContent = message;

        newConfirmBtn.addEventListener('click', () => {
            overlay.classList.remove('active');
            resolve(true);
        });

        document.getElementById('custom-alert-close').addEventListener('click', () => {
            overlay.classList.remove('active');
            resolve(false);
        });

        overlay.addEventListener('click', function(e) {
            if (e.target === overlay) {
                overlay.classList.remove('active');
                resolve(false);
            }
        });

        overlay.classList.add('active');
    });
}

/* ========================================
   构建API URL
   ======================================== */
function buildApiUrl(path, isSystemPath = false) {
    if (isSystemPath && currentAdminToken && currentAdminToken.trim() !== '' && currentAdminToken.trim() !== '*'.repeat(currentAdminToken.length)) {
        return '/' + currentAdminToken + path;
    }
    return (currentToken ? '/' + currentToken : "") + path;
}

/* ========================================
   加载环境变量
   ======================================== */
function loadEnvVariables() {
    showLoadingIndicator('env-list');
    
    fetch(buildApiUrl('/api/config', true))
        .then(response => response.json())
        .then(config => {
            currentAdminToken = config.originalEnvVars?.ADMIN_TOKEN || '';
            originalToken = config.originalEnvVars?.TOKEN || '87654321';
            
            const originalEnvVars = config.originalEnvVars || {};
            envVariables = {};
            
            Object.keys(originalEnvVars).forEach(key => {
                const varConfig = config.envVarConfig?.[key] || { category: 'system', type: 'text', description: '未分类配置项' };
                const category = varConfig.category || 'system';
                
                if (!envVariables[category]) {
                    envVariables[category] = [];
                }
                
                envVariables[category].push({
                    key: key,
                    value: originalEnvVars[key],
                    description: varConfig.description || '',
                    type: varConfig.type || 'text',
                    min: varConfig.min,
                    max: varConfig.max,
                    options: varConfig.options || []
                });
            });
            
            hideLoadingIndicator('env-list');
            renderEnvList();
        })
        .catch(error => {
            console.error('Failed to load env variables:', error);
            hideLoadingIndicator('env-list');
            showErrorMessage('env-list', '加载配置失败: ' + error.message);
        });
}

/* ========================================
   显示加载指示器
   ======================================== */
function showLoadingIndicator(containerId) {
    const container = document.getElementById(containerId);
    if (container) {
        container.innerHTML = \`
            <div style="text-align: center; padding: 3rem;">
                <div class="loading-spinner" style="margin: 0 auto;"></div>
                <p style="margin-top: 1rem; color: var(--text-secondary); font-weight: 500;">加载中...</p>
            </div>
        \`;
    }
}

function hideLoadingIndicator(containerId) {
    // 加载指示器会被实际内容替换
}

function showErrorMessage(containerId, message) {
    const container = document.getElementById(containerId);
    if (container) {
        container.innerHTML = \`
            <div style="text-align: center; padding: 3rem; color: var(--danger-color);">
                <div style="font-size: 3rem; margin-bottom: 1rem;">⚠️</div>
                <p style="font-weight: 600;">\${message}</p>
            </div>
        \`;
    }
}

/* ========================================
   更新API端点信息
   ======================================== */
function updateApiEndpoint() {
    return fetch(buildApiUrl('/api/config', true))
        .then(response => response.json())
        .then(config => {
            const protocol = window.location.protocol;
            const host = window.location.host;
            const token = config.originalEnvVars?.TOKEN || '87654321';
            const adminToken = config.originalEnvVars?.ADMIN_TOKEN;

            originalToken = token;
            currentAdminToken = adminToken || '';

            const urlPath = window.location.pathname;
            const pathParts = urlPath.split('/').filter(part => part !== '');
            const urlToken = pathParts.length > 0 ? pathParts[0] : '';
            
            let apiEndpoint;
            
            if (token === '87654321') {
                apiEndpoint = protocol + '//' + host;
            } else {
                let apiToken = '********';
                if (urlToken === token || (adminToken !== "" && urlToken === adminToken)) {
                    apiToken = token;
                }
                apiEndpoint = protocol + '//' + host + '/' + apiToken;
            }
            
            const apiEndpointElement = document.getElementById('api-endpoint');
            if (apiEndpointElement) {
                apiEndpointElement.textContent = apiEndpoint;
            }
            return config;
        })
        .catch(error => {
            console.error('获取配置信息失败:', error);
            const protocol = window.location.protocol;
            const host = window.location.host;
            const apiEndpoint = protocol + '//' + host + '/********';
            const apiEndpointElement = document.getElementById('api-endpoint');
            if (apiEndpointElement) {
                apiEndpointElement.textContent = apiEndpoint;
            }
            throw error;
        });
}

/* ========================================
   获取Docker版本并检查更新
   ======================================== */
function getDockerVersion() {
    const url = "https://img.shields.io/docker/v/logvar/danmu-api?sort=semver";

    fetch(url)
        .then(response => response.text())
        .then(svgContent => {
            const versionMatch = svgContent.match(/version<\\/text><text.*?>(v[\\d\\.]+)/);

            if (versionMatch && versionMatch[1]) {
                const latestVersion = versionMatch[1];
                const latestVersionElement = document.getElementById('latest-version');
                
                if (latestVersionElement) {
                    latestVersionElement.textContent = latestVersion;
                    
                    // 添加版本号动画
                    latestVersionElement.style.animation = 'pulse 0.6s ease-out';
                }
                
                // 检查是否有新版本
                checkForUpdate(latestVersion);
            }
        })
        .catch(error => {
            console.error("Error fetching the SVG:", error);
            const latestVersionElement = document.getElementById('latest-version');
            if (latestVersionElement) {
                latestVersionElement.textContent = '获取失败';
            }
        });
}

/* ========================================
   检查版本更新
   ======================================== */
function checkForUpdate(latestVersion) {
    const currentVersionElement = document.getElementById('current-version');
    if (!currentVersionElement) return;
    
    const currentVersion = currentVersionElement.textContent.trim();
    
    // 比较版本号
    if (compareVersions(latestVersion, currentVersion) > 0) {
        showUpdateNotice(currentVersion, latestVersion);
        addLog(\`🎉 发现新版本: \${latestVersion} (当前: \${currentVersion})\`, 'info');
    } else {
        addLog(\`✅ 当前已是最新版本: \${currentVersion}\`, 'success');
    }
}

/* ========================================
   版本号比较函数
   ======================================== */
function compareVersions(v1, v2) {
    // 移除 'v' 前缀
    const cleanV1 = v1.replace(/^v/, '');
    const cleanV2 = v2.replace(/^v/, '');
    
    const parts1 = cleanV1.split('.').map(Number);
    const parts2 = cleanV2.split('.').map(Number);
    
    for (let i = 0; i < Math.max(parts1.length, parts2.length); i++) {
        const part1 = parts1[i] || 0;
        const part2 = parts2[i] || 0;
        
        if (part1 > part2) return 1;
        if (part1 < part2) return -1;
    }
    
    return 0;
}

/* ========================================
   显示更新提示
   ======================================== */
function showUpdateNotice(currentVersion, latestVersion) {
    const updateNotice = document.getElementById('version-update-notice');
    const updateDesc = document.getElementById('update-desc');
    
    if (updateNotice && updateDesc) {
        updateDesc.textContent = \`\${currentVersion} → \${latestVersion}\`;
        updateNotice.style.display = 'flex';
    }
}

/* ========================================
   显示更新指南
   ======================================== */
function showUpdateGuide() {
    const currentVersion = document.getElementById('current-version').textContent.trim();
    const latestVersion = document.getElementById('latest-version').textContent.trim();
    
    const guideMessage = \`
📦 版本更新提示

当前版本: \${currentVersion}
最新版本: \${latestVersion}

更新方法：

🐳 Docker 部署：
1. 停止当前容器: docker stop danmu-api
2. 拉取最新镜像: docker pull logvar/danmu-api:latest
3. 重新启动容器

☁️ 云平台部署 (Vercel/Netlify/Cloudflare)：
1. 进入项目仓库
2. 拉取最新代码: git pull origin main
3. 推送到部署分支触发自动部署

💡 提示：更新前请备份重要配置！
    \`.trim();
    
    customAlert(guideMessage, '🔄 更新指南');
}

/* ========================================
   复制API端点
   ======================================== */
function copyApiEndpoint() {
    const apiEndpointElement = document.getElementById('api-endpoint');
    if (apiEndpointElement) {
        const apiEndpoint = apiEndpointElement.textContent;
        navigator.clipboard.writeText(apiEndpoint)
            .then(() => {
                const originalText = apiEndpointElement.textContent;
                apiEndpointElement.textContent = '✓ 已复制!';
                apiEndpointElement.style.color = '#10b981';
                
                // 添加复制成功动画
                const card = apiEndpointElement.closest('.api-endpoint-card');
                if (card) {
                    card.style.transform = 'scale(1.05)';
                    setTimeout(() => {
                        card.style.transform = '';
                    }, 300);
                }
                
                setTimeout(() => {
                    apiEndpointElement.textContent = originalText;
                    apiEndpointElement.style.color = '';
                }, 2000);
                
                addLog('API端点已复制到剪贴板 📋: ' + apiEndpoint, 'success');
            })
            .catch(err => {
                console.error('复制失败:', err);
                customAlert('复制失败: ' + err, '❌ 复制失败');
                addLog('复制API端点失败: ' + err, 'error');
            });
    }
}

/* ========================================
   初始化
   ======================================== */
async function init() {
    // 注意：页面恢复逻辑已移至 DOMContentLoaded 以消除闪烁
    try {
        await updateApiEndpoint();
        updateCurrentModeDisplay();
        getDockerVersion();
        const config = await fetchAndSetConfig();
        updateDeployEnvStatusBadgeFromConfig(config);
        setDefaultPushUrl(config);
        checkAndHandleAdminToken();
        loadEnvVariables();
        renderEnvList();
        renderPreview();
        addLog('🎉 系统初始化完成', 'success');
        fetchRealLogs();
    } catch (error) {
        console.error('初始化失败:', error);
        addLog('❌ 系统初始化失败: ' + error.message, 'error');
        fetchRealLogs();
    }
    // 初始化弹幕测试相关功能
    if (document.getElementById('danmu-heatmap-canvas')) {
        // 预加载画布
        const canvas = document.getElementById('danmu-heatmap-canvas');
        canvas.width = canvas.offsetWidth;
        canvas.height = 150;

        // 初始化热力图交互（鼠标提示 / 点击查看区间弹幕数）
        if (typeof initDanmuHeatmapInteraction === 'function') {
            initDanmuHeatmapInteraction();
        }
    }
}

/* ========================================
   页面加载完成后初始化
   ======================================== */
document.addEventListener('DOMContentLoaded', function() {
    createCustomAlert();
    initMobileViewportFixes();
    
    // 1. 优先初始化主题 (防止颜色闪烁)
    initTheme();

    // 2. 无闪烁页面恢复逻辑 (核心优化)
    let savedSection = sessionStorage.getItem('activeSection') || localStorage.getItem('activeSection');
    // 没有 URL token 时，避免恢复到受保护页面（例如 /ADMIN_TOKEN 进入后直接关闭导致下次仍停留在管理页）
    const urlPath = window.location.pathname;
    const pathParts = urlPath.split('/').filter(part => part !== '');
    const urlToken = pathParts.length > 0 ? pathParts[0] : '';
    const protectedSections = ['logs', 'api', 'env', 'push'];
    if (!urlToken && savedSection && protectedSections.includes(savedSection)) {
        try {
            sessionStorage.removeItem('activeSection');
            localStorage.removeItem('activeSection');
        } catch (e) {}
        savedSection = null;
    }

    // 如果保存的页面存在且不是默认的 'preview'
    if (savedSection && savedSection !== 'preview') {
        // [关键步骤 A] 临时注入样式，强制禁用所有过渡动画，防止"淡出淡入"的视觉残留
        const noTransitionStyle = document.createElement('style');
        noTransitionStyle.id = 'temp-no-transition';
        noTransitionStyle.innerHTML = '* { transition: none !important; animation: none !important; }';
        document.head.appendChild(noTransitionStyle);

        // [关键步骤 B] 暴力移除所有默认 active 状态，防止主页露头
        document.querySelectorAll('.content-section.active').forEach(el => {
            el.classList.remove('active');
            el.style.display = 'none'; // 强制隐藏默认页面
        });
        document.querySelectorAll('.nav-item.active').forEach(el => {
            el.classList.remove('active');
        });

        // [关键步骤 C] 立即渲染目标页面
        performSectionSwitch(savedSection, true);

        // [关键步骤 D] 下一帧恢复动画和布局
        requestAnimationFrame(() => {
            setTimeout(() => {
                // 移除禁用动画的样式
                const style = document.getElementById('temp-no-transition');
                if (style) style.remove();
                
                // 清理强制添加的 display: none，交还给 CSS 类控制
                document.querySelectorAll('.content-section').forEach(el => {
                    el.style.display = ''; 
                });
            }, 50); // 极短的延迟确保渲染完成
        });
    }

    // 3. 执行数据加载等异步逻辑
    init();
});

/* ========================================
   添加键盘快捷键
   ======================================== */
document.addEventListener('keydown', function(e) {
    // Alt + T: 切换主题
    if (e.altKey && e.key === 't') {
        e.preventDefault();
        toggleTheme();
    }
    
    // Alt + 数字: 快速切换导航
    if (e.altKey && e.key >= '1' && e.key <= '5') {
        e.preventDefault();
        const sections = ['preview', 'logs', 'api', 'push', 'env'];
        const index = parseInt(e.key) - 1;
        if (sections[index]) {
            switchSection(sections[index]);
        }
    }
});
/* ========================================
   数字动画函数
   ======================================== */
function animateNumber(elementId, start, end, duration) {
    const element = document.getElementById(elementId);
    if (!element) return;
    
    const range = end - start;
    const increment = range / (duration / 16);
    let current = start;
    
    const timer = setInterval(() => {
        current += increment;
        if ((increment > 0 && current >= end) || (increment < 0 && current <= end)) {
            current = end;
            clearInterval(timer);
        }
        element.textContent = Math.round(current);
    }, 16);
}
`;