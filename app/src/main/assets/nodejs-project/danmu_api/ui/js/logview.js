// language=JavaScript
export const logviewJsContent = /* javascript */ `
/* ========================================
   日志过滤状态
   ======================================== */
let currentLogFilter = 'all';
let autoRefreshInterval = null;
let isAutoRefreshing = false;

/* ========================================
   添加日志
   ======================================== */
function addLog(message, type = 'info') {
    const timestamp = new Date().toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
    logs.push({ timestamp, message, type });
    if (logs.length > 500) logs.shift();
    renderLogs();
    updateLogFilterBadges();
}

/* ========================================
   渲染日志 - 优化版
   ======================================== */
function renderLogs() {
    const container = document.getElementById('log-container');
    if (!container) return;
    
    const filteredLogs = currentLogFilter === 'all' 
        ? logs 
        : logs.filter(log => log.type === currentLogFilter);
    
    if (filteredLogs.length === 0) {
        container.innerHTML = \`
            <div class="log-empty-state">
                <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                    <path d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" stroke-width="2"/>
                </svg>
                <p class="empty-text">\${currentLogFilter === 'all' ? '暂无日志' : '暂无' + getLogTypeText(currentLogFilter) + '日志'}</p>
            </div>
        \`;
        return;
    }
    
    container.innerHTML = filteredLogs.map((log, index) => {
        const typeIcon = getLogTypeIcon(log.type);
        const escapedMessage = escapeHtml(log.message);
        const shortTime = log.timestamp.substring(0, 5); // 只显示 HH:MM
        const isLongMessage = escapedMessage.length > 120;
        const displayMessage = isLongMessage ? escapedMessage.substring(0, 120) + '...' : escapedMessage;
        const logId = \`log-\${index}\`;
        
        return \`
            <div class="log-entry log-\${log.type}" data-type="\${log.type}" data-full="\${isLongMessage}">
                <div class="log-meta">
                    <span class="log-icon">\${typeIcon}</span>
                    <span class="log-time">\${shortTime}</span>
                    <span class="log-type-tag log-type-\${log.type}">\${getLogTypeText(log.type)}</span>
                </div>
                <div class="log-content">
                    <div class="log-message" id="\${logId}-short">\${displayMessage}</div>
                    \${isLongMessage ? \`
                        <div class="log-message-full" id="\${logId}-full" style="display: none;">\${escapedMessage}</div>
                        <button class="log-expand-btn" onclick="toggleLogMessage('\${logId}')">
                            <span class="expand-text">展开</span>
                            <svg class="expand-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <polyline points="6 9 12 15 18 9"></polyline>
                            </svg>
                        </button>
                    \` : ''}
                </div>
            </div>
        \`;
    }).join('');
    
    container.scrollTop = container.scrollHeight;
}

/* ========================================
   获取日志类型图标
   ======================================== */
function getLogTypeIcon(type) {
    const icons = {
        'error': '❌',
        'warn': '⚠️',
        'info': 'ℹ️',
        'success': '✅'
    };
    return icons[type] || 'ℹ️';
}

/* ========================================
   获取日志类型文本
   ======================================== */
function getLogTypeText(type) {
    const texts = {
        'error': '错误',
        'warn': '警告',
        'info': '信息',
        'success': '成功'
    };
    return texts[type] || '信息';
}

/* ========================================
   设置日志过滤器
   ======================================== */
function setLogFilter(filter) {
    currentLogFilter = filter;
    
    // 更新按钮状态
    document.querySelectorAll('.log-filter-btn').forEach(btn => {
        btn.classList.remove('active');
        if (btn.dataset.filter === filter) {
            btn.classList.add('active');
        }
    });
    
    renderLogs();
}

/* ========================================
   更新过滤器徽章数量
   ======================================== */
function updateLogFilterBadges() {
    const counts = {
        all: logs.length,
        error: logs.filter(l => l.type === 'error').length,
        warn: logs.filter(l => l.type === 'warn').length,
        info: logs.filter(l => l.type === 'info').length,
        success: logs.filter(l => l.type === 'success').length
    };
    
    Object.keys(counts).forEach(type => {
        const btn = document.querySelector(\`.log-filter-btn[data-filter="\${type}"]\`);
        if (btn) {
            const badge = btn.querySelector('.filter-badge');
            if (badge) {
                badge.textContent = counts[type];
                badge.style.display = counts[type] > 0 ? 'block' : 'none';
            }
        }
    });
}

/* ========================================
   从API获取真实日志
   ======================================== */
async function fetchRealLogs() {
    try {
        const response = await fetch(buildApiUrl('/api/logs'));
        if (!response.ok) {
            throw new Error(\`HTTP error! status: \${response.status}\`);
        }
        const logText = await response.text();
        const logLines = logText.split('\\n').filter(line => line.trim() !== '');
        
        logs = logLines.map(line => {
            const match = line.match(/\\[([^\\]]+)\\] (\\w+): (.*)/);
            if (match) {
                return {
                    timestamp: match[1],
                    type: match[2].toLowerCase(),
                    message: match[3]
                };
            }
            return {
                timestamp: new Date().toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' }),
                type: 'info',
                message: line
            };
        });
        renderLogs();
        updateLogFilterBadges();
    } catch (error) {
        console.error('Failed to fetch logs:', error);
        addLog(\`获取日志失败: \${error.message}\`, 'error');
    }
}

/* ========================================
   刷新日志
   ======================================== */
function refreshLogs() {
    const btn = event.target.closest('.btn');
    const originalHTML = btn.innerHTML;
    
    btn.innerHTML = '<span class="loading-spinner-small"></span> 刷新中...';
    btn.disabled = true;
    
    fetchRealLogs().finally(() => {
        btn.innerHTML = originalHTML;
        btn.disabled = false;
    });
}

/* ========================================
   切换自动刷新
   ======================================== */
function toggleAutoRefresh() {
    const btn = document.getElementById('autoRefreshBtn');
    
    if (isAutoRefreshing) {
        // 停止自动刷新
        if (autoRefreshInterval) {
            clearInterval(autoRefreshInterval);
            autoRefreshInterval = null;
        }
        isAutoRefreshing = false;
        btn.innerHTML = \`
            <svg class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                <path d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" stroke-width="2"/>
                <path d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" stroke-width="2"/>
            </svg>
            自动刷新
        \`;
        btn.classList.remove('btn-success');
        btn.classList.add('btn-secondary');
    } else {
        // 开始自动刷新
        isAutoRefreshing = true;
        btn.innerHTML = \`
            <svg class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                <path d="M10 9v6m4-6v6m7-3a9 9 0 11-18 0 9 9 0 0118 0z" stroke-width="2"/>
            </svg>
            停止刷新
        \`;
        btn.classList.remove('btn-secondary');
        btn.classList.add('btn-success');
        
        // 立即刷新一次
        fetchRealLogs();
        
        // 每3秒自动刷新
        autoRefreshInterval = setInterval(() => {
            fetchRealLogs();
        }, 3000);
    }
}

/* ========================================
   导出日志
   ======================================== */
function exportLogs() {
    const filteredLogs = currentLogFilter === 'all' 
        ? logs 
        : logs.filter(log => log.type === currentLogFilter);
    
    if (filteredLogs.length === 0) {
        customAlert('没有可导出的日志');
        return;
    }
    
    const logText = filteredLogs.map(log => 
        \`[\${log.timestamp}] \${getLogTypeText(log.type).toUpperCase()}: \${log.message}\`
    ).join('\\n');
    
    const blob = new Blob([logText], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = \`logs_\${currentLogFilter}_\${new Date().toISOString().slice(0, 19).replace(/:/g, '-')}.txt\`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    
    customAlert('日志已导出');
}

/* ========================================
   清空日志
   ======================================== */
async function clearLogs() {
    const configCheck = await checkDeployPlatformConfig();
    if (!configCheck.success) {
        customAlert(configCheck.message);
        return;
    }

    customConfirm('确定要清空所有日志吗?', '清空确认').then(async confirmed => {
        if (confirmed) {
            try {
                const response = await fetch(buildApiUrl('/api/logs/clear', true), {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    }
                });
                
                if (!response.ok) {
                    throw new Error(\`HTTP error! status: \${response.status}\`);
                }
                
                const result = await response.json();
                if (result.success) {
                    logs = [];
                    renderLogs();
                    updateLogFilterBadges();
                    addLog('日志已清空', 'warn');
                } else {
                    addLog(\`清空日志失败: \${result.message}\`, 'error');
                }
            } catch (error) {
                console.error('Failed to clear logs:', error);
                addLog(\`清空日志失败: \${error.message}\`, 'error');
            }
        }
    });
}

/* ========================================
   JSON高亮函数
   ======================================== */
function highlightJSON(obj) {
    let json = JSON.stringify(obj, null, 2);
    json = json.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    
    return json.replace(/("(\\\\u[a-zA-Z0-9]{4}|\\\\[^u]|[^\\\\"])*"(\\s*:)?|\\b(true|false|null)\\b|-?\\d+(?:\\.\\d*)?(?:[eE][+\\-]?\\d+)?)/g, function (match) {
        let cls = 'json-number';
        if (/^"/.test(match)) {
            if (/:$/.test(match)) {
                cls = 'json-key';
            } else {
                cls = 'json-string';
            }
        } else if (/true|false/.test(match)) {
            cls = 'json-boolean';
        } else if (/null/.test(match)) {
            cls = 'json-null';
        }
        return '<span class="' + cls + '">' + match + '</span>';
    });
}
/* ========================================
   切换日志消息展开/收起
   ======================================== */
function toggleLogMessage(logId) {
    const shortMsg = document.getElementById(\`\${logId}-short\`);
    const fullMsg = document.getElementById(\`\${logId}-full\`);
    const btn = event.target.closest('.log-expand-btn');
    const expandText = btn.querySelector('.expand-text');
    const expandIcon = btn.querySelector('.expand-icon');
    
    if (fullMsg.style.display === 'none') {
        // 展开
        shortMsg.style.display = 'none';
        fullMsg.style.display = 'block';
        expandText.textContent = '收起';
        expandIcon.style.transform = 'rotate(180deg)';
        btn.classList.add('expanded');
    } else {
        // 收起
        shortMsg.style.display = 'block';
        fullMsg.style.display = 'none';
        expandText.textContent = '展开';
        expandIcon.style.transform = 'rotate(0deg)';
        btn.classList.remove('expanded');
    }
}
`;
