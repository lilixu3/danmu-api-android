// language=JavaScript
export const previewJsContent = /* javascript */ `
/* ========================================
   渲染配置预览
   ======================================== */
function renderPreview() {
    const preview = document.getElementById('preview-area');
    
    // 显示加载状态
    showLoadingIndicator('preview-area');
    
    fetch('/api/config')
        .then(response => response.json())
        .then(config => {
            const categorizedVars = config.categorizedEnvVars || {};
            
            let html = '';
            
            // 按类别顺序排列
            const categoryOrder = ['api', 'source', 'match', 'danmu', 'cache', 'system'];
            const sortedCategories = categoryOrder.filter(cat => categorizedVars[cat] && categorizedVars[cat].length > 0);
            
            // 更新统计信息（在这里，数据已经获取到了）
            const totalConfigs = sortedCategories.reduce((sum, cat) => sum + categorizedVars[cat].length, 0);
            const totalCategories = sortedCategories.length;
            
            // 计算已手动配置的数量（originalEnvVars中值不为空字符串的项）
            const originalEnvVars = config.originalEnvVars || {};
            const manualConfigs = Object.values(originalEnvVars).filter(value => value !== '' && value !== null && value !== undefined).length;
            
            const totalConfigsEl = document.getElementById('total-configs');
            const totalCategoriesEl = document.getElementById('total-categories');
            const manualConfigsEl = document.getElementById('manual-configs');
            
            if (totalConfigsEl) {
                animateNumber('total-configs', 0, totalConfigs, 800);
            }
            
            if (totalCategoriesEl) {
                animateNumber('total-categories', 0, totalCategories, 600);
            }
            
            if (manualConfigsEl) {
                animateNumber('manual-configs', 0, manualConfigs, 700);
            }
            
            // 检测系统状态
            checkSystemStatus();
            
            sortedCategories.forEach((category, index) => {
                const items = categorizedVars[category];
                const categoryIcon = getCategoryIcon(category);
                const categoryName = getCategoryName(category);
                const categoryColor = getCategoryColor(category);
                
                html += \`
                    <div class="preview-category" style="animation: fadeInUp 0.4s ease-out \${index * 0.1}s backwards;">
                        <div class="preview-category-header">
                            <h3 class="preview-category-title">
                                <span class="category-icon" style="background: \${categoryColor};">\${categoryIcon}</span>
                                <span>\${categoryName}</span>
                                <span class="category-badge">\${items.length} 项</span>
                            </h3>
                        </div>
                        <div class="preview-items">
                            \${items.map((item, itemIndex) => \`
                                <div class="preview-item" style="animation: fadeInUp 0.3s ease-out \${(index * 0.1) + (itemIndex * 0.05)}s backwards;">
                                    <div class="preview-item-header">
                                        <strong class="preview-key">
                                            <span class="key-icon">🔑</span>
                                            \${escapeHtml(item.key)}
                                        </strong>
                                        <span class="preview-type-badge">\${getTypeBadge(item.type || 'text')}</span>
                                    </div>
                                    <div class="preview-value-container">
                                        <code class="preview-value">\${escapeHtml(formatValue(item.value))}</code>
                                        <button class="preview-copy-btn" onclick="copyPreviewValue('\${escapeHtml(String(item.value)).replace(/'/g, "\\\\'")}', this)" title="复制值">
                                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                                <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                                                <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                                            </svg>
                                        </button>
                                    </div>
                                    \${item.description ? \`
                                        <div class="preview-desc">
                                            <span class="desc-icon">💡</span>
                                            \${escapeHtml(item.description)}
                                        </div>
                                    \` : ''}
                                </div>
                            \`).join('')}
                        </div>
                    </div>
                \`;
            });
            
            if (html === '') {
                html = \`
                    <div class="preview-empty">
                        <div class="empty-icon">📭</div>
                        <h3>暂无配置</h3>
                        <p>还没有配置任何环境变量</p>
                    </div>
                \`;
            }
            
            preview.innerHTML = html;
            
            addLog('✅ 配置预览加载完成，共 ' + sortedCategories.length + ' 个类别', 'success');
        })
        .catch(error => {
            console.error('Failed to load config for preview:', error);
            preview.innerHTML = \`
                <div class="preview-error">
                    <div class="error-icon">⚠️</div>
                    <h3>加载失败</h3>
                    <p>\${escapeHtml(error.message)}</p>
                    <button class="btn btn-primary" onclick="renderPreview()">
                        <svg class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                            <path d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"/>
                        </svg>
                        重新加载
                    </button>
                </div>
            \`;
            addLog('❌ 配置预览加载失败: ' + error.message, 'error');
        });
}

/* ========================================
   复制预览值
   ======================================== */
function copyPreviewValue(value, button) {
    // 确保value是字符串
    const textToCopy = String(value);
    
    navigator.clipboard.writeText(textToCopy)
        .then(() => {
            const originalHTML = button.innerHTML;
            button.innerHTML = \`
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="20 6 9 17 4 12"></polyline>
                </svg>
            \`;
            button.style.background = 'var(--success-color)';
            button.style.borderColor = 'var(--success-color)';
            button.style.animation = 'pulse 0.4s ease-out';
            
            setTimeout(() => {
                button.innerHTML = originalHTML;
                button.style.background = '';
                button.style.borderColor = '';
                button.style.animation = '';
            }, 1500);
            
            addLog('📋 已复制配置值到剪贴板', 'success');
        })
        .catch(err => {
            console.error('复制失败:', err);
            addLog('❌ 复制失败: ' + err.message, 'error');
        });
}

/* ========================================
   格式化值显示
   ======================================== */
function formatValue(value) {
    // 确保value是字符串
    const stringValue = String(value);
    if (stringValue.length > 200) {
        return stringValue.substring(0, 200) + '...';
    }
    return stringValue;
}

/* ========================================
   获取类型徽章
   ======================================== */
function getTypeBadge(type) {
    const badges = {
        text: '文本',
        boolean: '布尔',
        number: '数字',
        select: '单选',
        'multi-select': '多选'
    };
    return badges[type] || '文本';
}

/* ========================================
   获取类别名称
   ======================================== */
function getCategoryName(category) {
    const names = {
        api: 'API 配置',
        source: '源配置',
        match: '匹配配置',
        danmu: '弹幕配置',
        cache: '缓存配置',
        system: '系统配置'
    };
    return names[category] || category;
}

/* ========================================
   获取类别图标
   ======================================== */
function getCategoryIcon(category) {
    const icons = {
        api: '🔗',
        source: '📜',
        match: '🔍',
        danmu: '🔣',
        cache: '💾',
        system: '⚙️'
    };
    return icons[category] || '📋';
}

/* ========================================
   获取类别颜色
   ======================================== */
function getCategoryColor(category) {
    const colors = {
        api: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
        source: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
        match: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
        danmu: 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
        cache: 'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
        system: 'linear-gradient(135deg, #30cfd0 0%, #330867 100%)'
    };
    return colors[category] || 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)';
}

/* ========================================
   转义HTML
   ======================================== */
function escapeHtml(text) {
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    return String(text).replace(/[&<>"']/g, m => map[m]);
}

/* ========================================
   检测系统状态
   ======================================== */
function checkSystemStatus() {
    const statusEl = document.getElementById('system-status');
    const statusIconWrapper = document.getElementById('status-icon-wrapper');
    const statusCard = document.getElementById('system-status-card');
    
    if (!statusEl) return;
    
    // 设置检测中状态
    statusEl.textContent = '检测中...';
    statusEl.className = 'stat-value stat-value-status';
    
    // 检测API是否正常
    fetch('/api/config', { method: 'GET' })
        .then(response => {
            if (response.ok) {
                updateSystemStatusUI('running', '运行正常');
            } else {
                updateSystemStatusUI('warning', '部分异常');
            }
        })
        .catch(error => {
            updateSystemStatusUI('error', '连接失败');
            console.error('System status check failed:', error);
        });
}

/* ========================================
   更新系统状态UI
   ======================================== */
function updateSystemStatusUI(status, text) {
    const statusEl = document.getElementById('system-status');
    const statusIconWrapper = document.getElementById('status-icon-wrapper');
    const statusCard = document.getElementById('system-status-card');
    
    if (!statusEl) return;
    
    // 更新文本
    statusEl.textContent = text;
    
    // 更新状态类名
    statusEl.className = 'stat-value stat-value-status status-' + status;
    
    if (statusIconWrapper) {
        statusIconWrapper.className = 'stat-icon-wrapper stat-icon-status status-' + status;
        
        // 更新图标
        const icons = {
            'running': '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>',
            'warning': '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>',
            'error': '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>'
        };
        
        if (icons[status]) {
            statusIconWrapper.innerHTML = icons[status];
        }
    }
    
    if (statusCard) {
        statusCard.className = 'preview-stat-card stat-card-compact status-' + status;
    }
    
    // 记录日志
    const logTypes = {
        'running': 'success',
        'warning': 'warning',
        'error': 'error'
    };
    
    addLog('🔍 系统状态: ' + text, logTypes[status] || 'info');
    // 同步更新移动端状态指示器
    updateMobileStatusIndicator(status);
}

/* ========================================
   更新当前模式显示
   ======================================== */
function updateCurrentModeDisplay() {
    const modeEl = document.getElementById('current-mode');
    const modeIconWrapper = document.getElementById('mode-icon-wrapper');
    
    if (!modeEl) return;
    
    const urlPath = window.location.pathname;
    const pathParts = urlPath.split('/').filter(part => part !== '');
    const urlToken = pathParts.length > 0 ? pathParts[0] : '';
    
    let modeName = '预览模式';
    let modeClass = 'mode-preview';
    
    if (urlToken) {
        if (currentAdminToken && currentAdminToken.trim() !== '' && urlToken === currentAdminToken) {
            modeName = '管理员模式';
            modeClass = 'mode-admin';
        } else if (originalToken && originalToken !== '87654321') {
            modeName = '用户模式';
            modeClass = 'mode-user';
        } else if (urlToken) {
            modeName = '用户模式';
            modeClass = 'mode-user';
        }
    }
    
    modeEl.textContent = modeName;
    
    if (modeIconWrapper) {
        modeIconWrapper.className = 'stat-icon-wrapper stat-icon-mode ' + modeClass;
    }
    
    addLog('🔐 当前模式: ' + modeName, 'info');
}
/* ========================================
   更新移动端状态指示器
   ======================================== */
function updateMobileStatusIndicator(status) {
    const mobileStatus = document.getElementById('mobile-status');
    if (!mobileStatus) return;
    
    const statusDot = mobileStatus.querySelector('.status-dot');
    if (!statusDot) return;
    
    // 移除所有状态类
    statusDot.classList.remove('status-running', 'status-warning', 'status-error');
    
    // 添加对应状态类
    statusDot.classList.add('status-' + status);
    
    // 更新提示文本
    const statusTexts = {
        'running': '系统运行正常',
        'warning': '系统部分异常',
        'error': '系统连接失败'
    };
    
    mobileStatus.title = statusTexts[status] || '系统状态未知';
}
`;