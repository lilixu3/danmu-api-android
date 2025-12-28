// language=JavaScript
export const apitestJsContent = /* javascript */ `
/* ========================================
   弹幕测试全局变量
   ======================================== */
let currentDanmuData = null;
let filteredDanmuData = null;
let currentEpisodeId = null;
let danmuLoadSeq = 0;
let activeDanmuLoadSeq = 0;

// 热力图交互状态
let heatmapState = null;
let heatmapSelectedIndex = null;
let heatmapTooltipEl = null;
let heatmapInteractionInited = false;

/* ========================================
   弹幕列表分页配置
   ======================================== */
const DANMU_PAGE_SIZE = 50;
let currentDanmuPage = 0;
/* ========================================
   API配置
   ======================================== */
const apiConfigs = {
    searchAnime: {
        name: '搜索动漫',
        icon: '🔍',
        method: 'GET',
        path: '/api/v2/search/anime',
        description: '根据关键词搜索动漫',
        params: [
            { 
                name: 'keyword', 
                label: '关键词 或 播放链接URL', 
                type: 'text', 
                required: true, 
                placeholder: '示例: 生万物 或 http://v.qq.com/x/cover/rjae621myqca41h/j0032ubhl9s.html',
                description: '输入动漫名称，或直接输入播放链接URL进行解析'
            }
        ]
    },
    searchEpisodes: {
        name: '搜索剧集',
        icon: '📺',
        method: 'GET',
        path: '/api/v2/search/episodes',
        description: '搜索指定动漫的剧集列表',
        params: [
            { 
                name: 'anime', 
                label: '动漫名称', 
                type: 'text', 
                required: true, 
                placeholder: '示例: 生万物',
                description: '输入完整的动漫名称'
            }
        ]
    },
    matchAnime: {
        name: '匹配动漫',
        icon: '🎯',
        method: 'POST',
        path: '/api/v2/match',
        description: '根据文件名智能匹配动漫',
        params: [
            { 
                name: 'fileName', 
                label: '文件名', 
                type: 'text', 
                required: true, 
                placeholder: '示例: 生万物 S02E08',
                description: '支持多种命名格式，如: 无忧渡.S02E08.2160p.WEB-DL.H265.DDP.5.1'
            }
        ]
    },
    getBangumi: {
        name: '获取番剧详情',
        icon: '📋',
        method: 'GET',
        path: '/api/v2/bangumi/:animeId',
        description: '获取指定番剧的详细信息',
        params: [
            { 
                name: 'animeId', 
                label: '动漫ID', 
                type: 'text', 
                required: true, 
                placeholder: '示例: 236379',
                description: '从搜索结果中获取的动漫ID'
            }
        ]
    },
    getComment: {
        name: '获取弹幕',
        icon: '💬',
        method: 'GET',
        path: '/api/v2/comment/:commentId',
        description: '获取指定剧集的弹幕数据',
        params: [
            { 
                name: 'commentId', 
                label: '弹幕ID', 
                type: 'text', 
                required: true, 
                placeholder: '示例: 10009',
                description: '从剧集列表中获取的弹幕ID'
            },
            { 
                name: 'format', 
                label: '格式', 
                type: 'select', 
                required: false, 
                placeholder: '默认: json', 
                options: ['json', 'xml'],
                default: 'json',
                description: '选择返回数据的格式'
            },
            { 
                name: 'segmentflag', 
                label: '分片标志', 
                type: 'select', 
                required: false, 
                placeholder: '默认: 不启用（完整弹幕）', 
                options: ['true', 'false'],
                description: '是否启用分片弹幕（部分源支持）。不选择时获取完整弹幕列表'
            }
        ]
    },
    getCommentByUrl: {
        name: '通过URL获取弹幕',
        icon: '🔗',
        method: 'GET',
        path: '/api/v2/comment',
        description: '通过视频URL直接获取弹幕（兼容第三方弹幕服务器格式）',
        params: [
            {
                name: 'url',
                label: '视频URL',
                type: 'text',
                required: true,
                placeholder: '示例: https://example.com/video.mp4',
                description: '输入视频直链/播放地址，后端将自动解析并获取弹幕'
            },
            {
                name: 'format',
                label: '格式',
                type: 'select',
                required: false,
                placeholder: '默认: json',
                options: ['json', 'xml'],
                default: 'json',
                description: '建议使用 json 便于查看；如需 xml 可切换'
            }
        ]
    },
    getSegmentComment: {
        name: '获取分片弹幕',
        icon: '🧩',
        method: 'POST',
        path: '/api/v2/segmentcomment',
        description: '通过请求体获取分片弹幕（用于分段/区间弹幕）',
        params: [
            { 
                name: 'format', 
                label: '格式', 
                type: 'select', 
                required: false, 
                placeholder: '默认: json', 
                options: ['json', 'xml'],
                default: 'json',
                description: '选择返回数据的格式'
            }
        ],
        hasBody: true,
        bodyType: 'json'
    }
};

/* ========================================
   加载API参数
   ======================================== */
function loadApiParams() {
    const select = document.getElementById('api-select');
    const apiKey = select.value;
    const paramsDiv = document.getElementById('api-params');
    const formDiv = document.getElementById('params-form');
    const responseContainer = document.getElementById('api-response-container');

    if (!apiKey) {
        paramsDiv.style.display = 'none';
        responseContainer.style.display = 'none';
        return;
    }

    const config = apiConfigs[apiKey];
    paramsDiv.style.display = 'block';
    
    // 显示API信息卡片
    const apiInfoHTML = \`
        <div class="api-info-card">
            <div class="api-info-header">
                <span class="api-icon">\${config.icon}</span>
                <div class="api-info-content">
                    <h4 class="api-name">\${config.name}</h4>
                    <p class="api-description">\${config.description}</p>
                </div>
            </div>
            <div class="api-info-details">
                <div class="api-detail-item">
                    <span class="detail-label">请求方法</span>
                    <span class="method-badge method-\${config.method.toLowerCase()}">\${config.method}</span>
                </div>
                <div class="api-detail-item">
                    <span class="detail-label">接口路径</span>
                    <code class="api-path">\${config.path}</code>
                </div>
            </div>
        </div>
    \`;

    const hasParams = config.params && config.params.length > 0;
    const hasBody = !!config.hasBody;

    // 没有查询参数且没有请求体
    if (!hasParams && !hasBody) {
        formDiv.innerHTML = apiInfoHTML + \`
            <div class="no-params-message">
                <span class="message-icon">ℹ️</span>
                <p>此接口无需参数</p>
            </div>
        \`;
        return;
    }

    let formHtml = apiInfoHTML;

    // 渲染查询参数
    if (hasParams) {
        formHtml += config.params.map((param, index) => {
            let inputHTML = '';
            
            if (param.type === 'select') {
                // 支持默认值：如果配置了 default，则不强制用户再手动选择
                let optionsHtml = param.default ? '' : '<option value="">-- 请选择 --</option>';
                if (param.options) {
                    optionsHtml += param.options.map(opt => {
                        const selected = (param.default !== undefined && String(param.default) === String(opt)) ? 'selected' : '';
                        return \`<option value="\${opt}" \${selected}>\${opt}</option>\`;
                    }).join('');
                }
                inputHTML = \`
                    <select class="form-select" id="param-\${param.name}" \${param.required ? 'required' : ''}>
                        \${optionsHtml}
                    </select>
                \`;
            } else {
                const placeholder = param.placeholder || "请输入" + param.label;
                const defaultAttr = (param.default !== undefined && param.default !== null) ? \`value="\${String(param.default).replace(/\"/g, '&quot;')}"\` : '';
                inputHTML = \`
                    <input 
                        type="\${param.type}" 
                        class="form-input" 
                        id="param-\${param.name}" 
                        placeholder="\${placeholder}" 
                        \${defaultAttr}
                        \${param.required ? 'required' : ''}
                    >
                \`;
            }
            
            return \`
                <div class="form-group" style="animation: fadeInUp 0.3s ease-out \${index * 0.1}s backwards;">
                    <label class="form-label \${param.required ? 'required' : ''}">
                        <span class="param-icon">🔸</span>
                        \${param.label}
                    </label>
                    \${inputHTML}
                    \${param.description ? \`
                        <small class="form-help">
                            <span class="help-icon">💡</span>
                            \${param.description}
                        </small>
                    \` : ''}
                </div>
            \`;
        }).join('');
    }

    // 渲染请求体（上游更新点）
    if (hasBody) {
        formHtml += \`
            <div class="form-group" style="margin-top: 1rem;">
                <label class="form-label required">
                    <span class="param-icon">🧾</span>
                    请求体内容 (JSON)
                </label>
                <textarea 
                    class="form-textarea" 
                    id="body-content" 
                    rows="6" 
                    placeholder='输入JSON格式的请求体，例如：{"type":"qq","segment_start":0,"segment_end":30000,"url":"https://dm.video.qq.com/barrage/segment/j0032ubhl9s/t/v1/0/30000"}'
                    required
                ></textarea>
                <small class="form-help">
                    <span class="help-icon">💡</span>
                    该接口为 POST，请在此处填写请求体 JSON
                </small>
            </div>
        \`;
    }

    formDiv.innerHTML = formHtml;
}

/* ========================================
   测试API
   ======================================== */
function testApi() {
    const select = document.getElementById('api-select');
    const apiKey = select.value;
    const sendButton = event.target;

    if (!apiKey) {
        customAlert('请先选择接口', '⚠️ 提示');
        return;
    }

    const originalText = sendButton.innerHTML;
    sendButton.innerHTML = '<span class="loading-spinner-small"></span> <span>发送中...</span>';
    sendButton.disabled = true;

    const config = apiConfigs[apiKey];
    const params = {};

    // 验证必填参数
    let hasError = false;
    config.params.forEach(param => {
        const input = document.getElementById(\`param-\${param.name}\`);
        const value = input.value.trim();
        
        if (param.required && !value) {
            input.classList.add('error');
            input.focus();
            hasError = true;
        } else {
            input.classList.remove('error');
            if (value) params[param.name] = value;
        }
    });

    if (hasError) {
        sendButton.innerHTML = originalText;
        sendButton.disabled = false;
        customAlert('请填写所有必填参数', '⚠️ 参数错误');
        return;
    }

    // segmentflag 兼容：只有显式选择 true 才传给后端；否则按“完整弹幕”处理
    if (apiKey === 'getComment') {
        if (params.segmentflag !== 'true') {
            delete params.segmentflag;
        }
    }

    addLog(\`🚀 调用接口: \${config.name} (\${config.method} \${config.path})\`, 'info');
    addLog(\`📤 请求参数: \${JSON.stringify(params)}\`, 'info');

    const startTime = performance.now();
    let url = config.path;
    const isPathParameterApi = config.path.includes(':');
    
    if (isPathParameterApi) {
        const pathParams = {};
        const queryParams = {};
        
        for (const [key, value] of Object.entries(params)) {
            if (config.path.includes(':' + key)) {
                pathParams[key] = value;
            } else {
                queryParams[key] = value;
            }
        }
        
        for (const [key, value] of Object.entries(pathParams)) {
            url = url.replace(':' + key, encodeURIComponent(value));
        }
        
        if (config.method === 'GET' && Object.keys(queryParams).length > 0) {
            const queryString = new URLSearchParams(queryParams).toString();
            url = url + '?' + queryString;
        }
    } else {
        if (config.method === 'GET') {
            const queryString = new URLSearchParams(params).toString();
            url = url + '?' + queryString;
        } else if (config.method === 'POST' && apiKey === 'getSegmentComment') {
            // 对于 getSegmentComment 接口，需要将 format 参数添加到 URL 查询参数中
            const queryParams = {};
            if (params.format) {
                queryParams.format = params.format;
            }
            if (Object.keys(queryParams).length > 0) {
                const queryString = new URLSearchParams(queryParams).toString();
                url = url + '?' + queryString;
            }
        }
    }

    const requestOptions = {
        method: config.method,
        headers: {
            'Content-Type': 'application/json'
        }
    };

    // 处理请求体（上游更新点）
    if (config.hasBody) {
        const bodyEl = document.getElementById('body-content');
        const bodyContent = bodyEl ? bodyEl.value.trim() : '';

        if (!bodyContent) {
            sendButton.innerHTML = originalText;
            sendButton.disabled = false;
            customAlert('请填写请求体内容 (JSON)', '⚠️ 参数错误');
            return;
        }

        try {
            const bodyData = JSON.parse(bodyContent);
            requestOptions.body = JSON.stringify(bodyData);
        } catch (e) {
            sendButton.innerHTML = originalText;
            sendButton.disabled = false;
            customAlert('请求体JSON格式错误: ' + e.message, '⚠️ 参数错误');
            return;
        }
    } else if (config.method === 'POST') {
        requestOptions.body = JSON.stringify(params);
    }
    fetch(buildApiUrl(url), requestOptions)
        .then(response => {
            const endTime = performance.now();
            const responseTime = Math.round(endTime - startTime);
            
            if (!response.ok) {
                throw new Error(\`HTTP error! status: \${response.status}\`);
            }
            
            // 检查format参数以确定如何处理响应
            const formatParam = params.format || 'json';
            
            if (formatParam.toLowerCase() === 'xml') {
                return response.text().then(text => ({
                    data: text,
                    format: 'xml',
                    responseTime: responseTime,
                    status: response.status
                }));
            } else {
                return response.json().then(json => ({
                    data: json,
                    format: 'json',
                    responseTime: responseTime,
                    status: response.status
                }));
            }
        })
        .then(result => {
            const responseContainer = document.getElementById('api-response-container');
            const responseDiv = document.getElementById('api-response');
            
            responseContainer.style.display = 'block';
            
            // 创建响应头部
            const responseHeaderDiv = document.createElement('div');
            responseHeaderDiv.className = 'response-header';
            responseHeaderDiv.innerHTML = \`
                <span class="response-status success">
                    <span>✅</span>
                    <span>成功 (\${result.status})</span>
                </span>
                <span class="response-time">
                    <span>⏱️</span>
                    <span>\${result.responseTime}ms</span>
                </span>
            \`;
            
            // 创建复制按钮
            const copyBtn = document.createElement('button');
            copyBtn.className = 'btn btn-secondary btn-sm copy-response-btn';
            copyBtn.innerHTML = \`
                <svg class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                    <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                </svg>
                <span>复制响应</span>
            \`;
            copyBtn.onclick = function() {
                copyApiResponse(result.data, result.format, this);
            };
            responseHeaderDiv.appendChild(copyBtn);
            
            // 清空并添加新内容
            responseDiv.innerHTML = '';
            responseDiv.appendChild(responseHeaderDiv);
            
            // 创建响应内容
            const codeBlock = document.createElement('div');
            codeBlock.className = 'response-content';
            
            if (result.format === 'xml') {
                codeBlock.classList.add('xml');
                codeBlock.textContent = result.data;
            } else {
                codeBlock.innerHTML = highlightJSON(result.data);
            }
            
            responseDiv.appendChild(codeBlock);
            
            addLog(\`✅ 接口调用成功 - 耗时 \${result.responseTime}ms\`, 'success');
            
            // 滚动到响应区域
            setTimeout(() => {
                responseContainer.scrollIntoView({ 
                    behavior: 'smooth', 
                    block: 'nearest' 
                });
            }, 100);
        })
        .catch(error => {
            const endTime = performance.now();
            const responseTime = Math.round(endTime - startTime);
            
            const errorMessage = \`❌ API请求失败: \${error.message}\`;
            const responseContainer = document.getElementById('api-response-container');
            const responseDiv = document.getElementById('api-response');
            
            responseContainer.style.display = 'block';
            responseDiv.innerHTML = \`
                <div class="response-header">
                    <span class="response-status error">
                        <span>❌</span>
                        <span>失败</span>
                    </span>
                    <span class="response-time">
                        <span>⏱️</span>
                        <span>\${responseTime}ms</span>
                    </span>
                </div>
                <div class="response-content error">\${escapeHtml(errorMessage)}</div>
            \`;
            
            addLog(errorMessage, 'error');
        })
        .finally(() => {
            sendButton.innerHTML = originalText;
            sendButton.disabled = false;
        });
}

/* ========================================
   复制API响应
   ======================================== */
function copyApiResponse(data, format, buttonElement) {
    const text = format === 'xml' ? data : JSON.stringify(data, null, 2);
    
    navigator.clipboard.writeText(text)
        .then(() => {
            const btn = buttonElement;
            const originalHTML = btn.innerHTML;
            
            btn.innerHTML = \`
                <svg class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                    <polyline points="20 6 9 17 4 12"></polyline>
                </svg>
                <span>已复制!</span>
            \`;
            btn.classList.add('copied');
            btn.disabled = true;
            
            setTimeout(() => {
                btn.innerHTML = originalHTML;
                btn.classList.remove('copied');
                btn.disabled = false;
            }, 2000);
            
            addLog('📋 响应内容已复制到剪贴板', 'success');
        })
        .catch(err => {
            console.error('复制失败:', err);
            customAlert('复制失败: ' + err.message, '❌ 复制失败');
            addLog('❌ 复制失败: ' + err.message, 'error');
        });
}
/* ========================================
   API 模式切换
   ======================================== */
function switchApiMode(mode) {
    // 更新标签状态
    document.querySelectorAll('.api-mode-tab').forEach(tab => {
        tab.classList.remove('active');
        if (tab.dataset.mode === mode) {
            tab.classList.add('active');
        }
    });
    
    // 切换显示内容
    if (mode === 'api-test') {
        document.getElementById('api-test-mode').style.display = 'block';
        document.getElementById('danmu-test-mode').style.display = 'none';
        addLog('📋 切换到接口调试模式', 'info');
    } else if (mode === 'danmu-test') {
        document.getElementById('api-test-mode').style.display = 'none';
        document.getElementById('danmu-test-mode').style.display = 'block';
        // 每次进入弹幕测试时，先让用户选择测试方式（自动匹配 / 手动搜索）
        resetDanmuTestUI();
        addLog('💬 切换到弹幕测试模式', 'info');
    }
}

/* ========================================
   弹幕测试 - 方式选择与界面重置
   ======================================== */
let currentDanmuTestMethod = null;

function resetDanmuTestUI() {
    // 清理搜索结果与展示区域
    const results = document.getElementById('danmu-search-results');
    const displayArea = document.getElementById('danmu-display-area');
    if (results) {
        results.style.display = 'none';
        results.innerHTML = '';
    }
    if (displayArea) {
        displayArea.style.display = 'none';
    }

    // 清理方式选择状态
    currentDanmuTestMethod = null;
    document.querySelectorAll('.danmu-method-tab').forEach(tab => tab.classList.remove('active'));

    // 显示“请选择方式”的占位内容，隐藏面板
    const empty = document.getElementById('danmu-method-empty');
    const autoPanel = document.getElementById('danmu-method-auto');
    const manualPanel = document.getElementById('danmu-method-manual');

    if (empty) empty.style.display = 'block';
    if (autoPanel) autoPanel.style.display = 'none';
    if (manualPanel) manualPanel.style.display = 'none';
}

function switchDanmuTestMethod(method) {
    if (!method) return;
    currentDanmuTestMethod = method;

    // 切换激活态
    document.querySelectorAll('.danmu-method-tab').forEach(tab => {
        tab.classList.toggle('active', tab.dataset.method === method);
    });

    // 切换面板显示
    const empty = document.getElementById('danmu-method-empty');
    const autoPanel = document.getElementById('danmu-method-auto');
    const manualPanel = document.getElementById('danmu-method-manual');
    if (empty) empty.style.display = 'none';
    if (autoPanel) autoPanel.style.display = method === 'auto' ? 'block' : 'none';
    if (manualPanel) manualPanel.style.display = method === 'manual' ? 'block' : 'none';

    // 切换方式后，避免界面混淆：隐藏之前的搜索结果/弹幕展示
    const results = document.getElementById('danmu-search-results');
    const displayArea = document.getElementById('danmu-display-area');
    if (results) results.style.display = 'none';
    if (displayArea) displayArea.style.display = 'none';

    // 自动聚焦输入框
    setTimeout(() => {
        if (method === 'auto') {
            const input = document.getElementById('auto-match-filename');
            if (input) input.focus();
        } else {
            const input = document.getElementById('manual-search-keyword');
            if (input) input.focus();
        }
    }, 50);
}
/* ========================================
   自动匹配弹幕
   ======================================== */
function autoMatchDanmu() {
    const filename = document.getElementById('auto-match-filename').value.trim();
    const searchBtn = event.target.closest('.btn') || event.target;
    
    if (!filename) {
        customAlert('请输入文件名', '⚠️ 提示');
        document.getElementById('auto-match-filename').focus();
        return;
    }
    
    const originalText = searchBtn.innerHTML;
    searchBtn.innerHTML = '<span class="loading-spinner-small"></span> <span>匹配中...</span>';
    searchBtn.disabled = true;
    
    addLog(\`🎯 开始自动匹配: \${filename}\`, 'info');
    
    fetch(buildApiUrl('/api/v2/match'), {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ fileName: filename })
    })
        .then(response => {
            if (!response.ok) {
                throw new Error(\`HTTP error! status: \${response.status}\`);
            }
            return response.json();
        })
        .then(data => {
            // 兼容多种返回格式
            // 格式1: {isMatched: true, matches: [{animeId, animeTitle, episodeId, episodeTitle}, ...]}
            // 格式2: {success: true, match: {animeTitle, episodeNumber, episodeId}}
            // 格式3: {matches: [...]}
            
            let matchResult = null;
            
            if (data.isMatched && data.matches && data.matches.length > 0) {
                // 弹弹Play 标准格式
                const firstMatch = data.matches[0];
                matchResult = {
                    animeTitle: firstMatch.animeTitle || firstMatch.anime || '',
                    episodeTitle: firstMatch.episodeTitle || firstMatch.episode || '',
                    episodeId: firstMatch.episodeId,
                    episodeNumber: extractEpisodeNumber(firstMatch.episodeTitle || firstMatch.episode || '')
                };
            } else if (data.success && data.match) {
                // 自定义格式
                matchResult = data.match;
            } else if (data.matches && data.matches.length > 0) {
                // 简化格式
                const firstMatch = data.matches[0];
                matchResult = {
                    animeTitle: firstMatch.animeTitle || firstMatch.anime || '',
                    episodeTitle: firstMatch.episodeTitle || firstMatch.episode || '',
                    episodeId: firstMatch.episodeId,
                    episodeNumber: extractEpisodeNumber(firstMatch.episodeTitle || firstMatch.episode || '')
                };
            }
            
            if (matchResult && matchResult.episodeId) {
                const displayTitle = matchResult.episodeTitle 
                    ? \`\${matchResult.animeTitle} - \${matchResult.episodeTitle}\`
                    : \`\${matchResult.animeTitle} - 第\${matchResult.episodeNumber || 1}集\`;
                addLog(\`✅ 匹配成功: \${displayTitle}\`, 'success');
                loadDanmuData(matchResult.episodeId, displayTitle);
            } else {
                throw new Error(data.errorMessage || data.message || '未找到匹配结果');
            }
        })
        .catch(error => {
            console.error('自动匹配失败:', error);
            addLog(\`❌ 自动匹配失败: \${error.message}\`, 'error');
            customAlert('自动匹配失败: ' + error.message, '❌ 匹配失败');
        })
        .finally(() => {
            searchBtn.innerHTML = originalText;
            searchBtn.disabled = false;
        });
}

/* ========================================
   从剧集标题提取集数
   ======================================== */
function extractEpisodeNumber(episodeTitle) {
    if (!episodeTitle) return 1;
    // 尝试匹配 "第X集"、"第X话"、"EP X"、"E X" 等格式
    const patterns = [
        /第(\\d+)[集话]/,
        /[Ee][Pp]?\\s*(\\d+)/,
        /^(\\d+)$/,
        /(\\d+)$/
    ];
    for (const pattern of patterns) {
        const match = episodeTitle.match(pattern);
        if (match) {
            return parseInt(match[1], 10);
        }
    }
    return 1;
}

/* ========================================
   手动搜索弹幕
   ======================================== */
function manualSearchDanmu() {
    const keyword = document.getElementById('manual-search-keyword').value.trim();
    const searchBtn = event.target.closest('.btn') || event.target;
    
    if (!keyword) {
        customAlert('请输入搜索关键词', '⚠️ 提示');
        document.getElementById('manual-search-keyword').focus();
        return;
    }
    
    const originalText = searchBtn.innerHTML;
    searchBtn.innerHTML = '<span class="loading-spinner-small"></span> <span>搜索中...</span>';
    searchBtn.disabled = true;
    
    addLog(\`🔍 开始搜索: \${keyword}\`, 'info');
    
    const searchUrl = buildApiUrl('/api/v2/search/anime?keyword=' + encodeURIComponent(keyword));
    
    fetch(searchUrl)
        .then(response => {
            if (!response.ok) {
                throw new Error(\`HTTP error! status: \${response.status}\`);
            }
            return response.json();
        })
        .then(data => {
            // 兼容多种返回格式
            // 格式1: {animes: [{animeId, animeTitle, ...}, ...]}
            // 格式2: {success: true, animes: [...]}
            // 格式3: {hasMore: false, animes: [...]}
            // 格式4: 直接是数组 [{animeId, animeTitle, ...}, ...]
            
            let animes = null;
            
            if (Array.isArray(data)) {
                animes = data;
            } else if (data.animes && Array.isArray(data.animes)) {
                animes = data.animes;
            } else if (data.data && Array.isArray(data.data)) {
                animes = data.data;
            }
            
            if (animes && animes.length > 0) {
                addLog(\`✅ 找到 \${animes.length} 个搜索结果\`, 'success');
                displayDanmuSearchResults(animes);
            } else {
                throw new Error(data.errorMessage || data.message || '未找到相关动漫');
            }
        })
        .catch(error => {
            console.error('搜索失败:', error);
            addLog(\`❌ 搜索失败: \${error.message}\`, 'error');
            customAlert('搜索失败: ' + error.message, '❌ 搜索失败');
            document.getElementById('danmu-search-results').style.display = 'none';
        })
        .finally(() => {
            searchBtn.innerHTML = originalText;
            searchBtn.disabled = false;
        });
}

/* ========================================
   显示搜索结果
   ======================================== */
function displayDanmuSearchResults(animes) {
    const container = document.getElementById('danmu-search-results');
    
    let html = \`
        <div class="form-card">
            <h3 class="card-title">
                <svg class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                    <path d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
                </svg>
                <span>搜索结果 (\${animes.length} 个)</span>
            </h3>
            <div class="anime-grid">
    \`;
    
    animes.forEach((anime, index) => {
        const imageUrl = anime.imageUrl || 'https://placehold.co/150x200?text=No+Image';
        html += \`
            <div class="anime-card" onclick="selectAnimeForDanmu(\${anime.animeId}, '\${escapeHtml(anime.animeTitle).replace(/'/g, "\\\\'")}', \${anime.episodeCount})"
                 style="animation: fadeInUp 0.4s ease-out \${index * 0.05}s backwards;">
                <div class="anime-card-image-wrapper">
                    <img src="\${imageUrl}" 
                         alt="\${escapeHtml(anime.animeTitle)}" 
                         referrerpolicy="no-referrer" 
                         class="anime-image"
                         loading="lazy">
                    <div class="anime-card-overlay">
                        <span class="view-icon">👁️</span>
                        <span class="view-text">查看剧集</span>
                    </div>
                </div>
                <div class="anime-info">
                    <h4 class="anime-title" title="\${escapeHtml(anime.animeTitle)}">
                        \${escapeHtml(anime.animeTitle)}
                    </h4>
                    <div class="anime-meta">
                        <span class="episode-count">
                            <span class="meta-icon">📺</span>
                            共 \${anime.episodeCount} 集
                        </span>
                    </div>
                </div>
            </div>
        \`;
    });
    
    html += '</div></div>';
    
    container.innerHTML = html;
    container.style.display = 'block';
    
    // 隐藏弹幕显示区域
    document.getElementById('danmu-display-area').style.display = 'none';
    
    // 滚动到结果区域
    setTimeout(() => {
        container.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }, 100);
}

/* ========================================
   选择动漫并显示集数列表
   ======================================== */
function selectAnimeForDanmu(animeId, animeTitle, episodeCount) {
    addLog(\`📺 选择动漫: \${animeTitle} (ID: \${animeId})\`, 'info');
    
    const container = document.getElementById('danmu-search-results');
    
    // 显示加载状态
    container.innerHTML = \`
        <div class="form-card">
            <div class="loading-state">
                <div class="loading-spinner" style="margin: 0 auto;"></div>
                <p style="margin-top: 1rem; color: var(--text-secondary); font-weight: 600;">加载剧集列表中...</p>
            </div>
        </div>
    \`;
    
    const bangumiUrl = buildApiUrl('/api/v2/bangumi/' + animeId);
    
    fetch(bangumiUrl)
        .then(response => {
            if (!response.ok) {
                throw new Error(\`HTTP error! status: \${response.status}\`);
            }
            return response.json();
        })
        .then(data => {
            // 兼容多种返回格式
            // 格式1: {bangumi: {animeTitle, episodes: [{episodeId, episodeTitle}, ...]}}
            // 格式2: {success: true, bangumi: {...}}
            // 格式3: {episodes: [...]}
            // 格式4: 直接是 {animeTitle, episodes: [...]}
            
            let episodes = null;
            let resolvedAnimeTitle = animeTitle;
            
            if (data.bangumi && data.bangumi.episodes) {
                episodes = data.bangumi.episodes;
                resolvedAnimeTitle = data.bangumi.animeTitle || animeTitle;
            } else if (data.episodes && Array.isArray(data.episodes)) {
                episodes = data.episodes;
                resolvedAnimeTitle = data.animeTitle || animeTitle;
            } else if (Array.isArray(data)) {
                episodes = data;
            }
            
            if (episodes && episodes.length > 0) {
                addLog(\`✅ 成功加载 \${episodes.length} 个剧集\`, 'success');
                displayEpisodeList(resolvedAnimeTitle, episodes);
            } else {
                throw new Error(data.errorMessage || data.message || '获取剧集列表失败或无剧集');
            }
        })
        .catch(error => {
            console.error('获取剧集失败:', error);
            addLog(\`❌ 获取剧集失败: \${error.message}\`, 'error');
            customAlert('获取剧集失败: ' + error.message, '❌ 加载失败');
            
            container.innerHTML = \`
                <div class="form-card">
                    <div class="search-error">
                        <div class="error-icon">❌</div>
                        <h3>加载失败</h3>
                        <p>\${escapeHtml(error.message)}</p>
                        <button class="btn btn-primary" onclick="selectAnimeForDanmu(\${animeId}, '\${escapeHtml(animeTitle).replace(/'/g, "\\\\'")}', \${episodeCount})">重试</button>
                    </div>
                </div>
            \`;
        });
}

/* ========================================
   显示剧集列表
   ======================================== */
function displayEpisodeList(animeTitle, episodes) {
    const container = document.getElementById('danmu-search-results');
    
    let html = \`
        <div class="form-card">
            <div class="episode-list-header">
                <h3 class="episode-anime-title">
                    <span class="episode-anime-icon">🎬</span>
                    \${escapeHtml(animeTitle)}
                </h3>
                <div class="episode-stats">
                    <span class="episode-stat-item">
                        <span class="episode-stat-icon">📺</span>
                        <span>共 \${episodes.length} 集</span>
                    </span>
                </div>
            </div>
            <div class="episode-grid">
    \`;
    
    episodes.forEach((episode, index) => {
        // 兼容不同的字段名
        const episodeId = episode.episodeId || episode.id || episode.cid;
        const episodeNumber = episode.episodeNumber || episode.episode || (index + 1);
        const episodeTitle = episode.episodeTitle || episode.title || episode.name || '';
        const displayTitle = episodeTitle || \`第 \${episodeNumber} 集\`;
        const fullTitle = \`\${animeTitle} - \${displayTitle}\`;
        
        html += \`
            <div class="episode-item" style="animation: fadeInUp 0.3s ease-out \${index * 0.03}s backwards;">
                <div class="episode-info">
                    <div class="episode-number">
                        <span class="episode-icon">📺</span>
                        第 \${episodeNumber} 集
                    </div>
                    <div class="episode-title">\${escapeHtml(episodeTitle || '无标题')}</div>
                </div>
                <button class="btn btn-primary btn-sm" onclick="loadDanmuData('\${episodeId}', '\${escapeHtml(fullTitle).replace(/'/g, "\\\\'")}')">
                    <svg class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                        <path d="M7 8h10M7 12h4m1 8l-4-4H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-3l-4 4z"/>
                    </svg>
                    <span>加载弹幕</span>
                </button>
            </div>
        \`;
    });
    
    html += '</div></div>';
    
    container.innerHTML = html;
    
    // 滚动到剧集列表
    setTimeout(() => {
        container.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }, 100);
}

/* ========================================
   加载弹幕数据
   ======================================== */
function loadDanmuData(episodeId, title) {
    addLog(\`💬 开始加载弹幕: \${title} (ID: \${episodeId})\`, 'info');

    // 生成本次加载序号，用于防止并发/快速切换导致旧数据覆盖
    const mySeq = ++danmuLoadSeq;
    activeDanmuLoadSeq = mySeq;

    // 显示弹幕展示区域
    const displayArea = document.getElementById('danmu-display-area');
    displayArea.style.display = 'block';

    // 更新标题
    document.getElementById('danmu-title').textContent = title;
    document.getElementById('danmu-subtitle').textContent = '正在加载弹幕数据...';

    // 立即清空旧数据（避免加载过程中显示旧统计/旧热力图）
    currentDanmuData = null;
    filteredDanmuData = null;
    currentDanmuPage = 0;
    heatmapState = null;
    heatmapSelectedIndex = null;

    // 统计信息占位
    document.getElementById('danmu-total-count').textContent = '--';
    document.getElementById('danmu-duration').textContent = '--:--';
    document.getElementById('danmu-density').textContent = '--';
    document.getElementById('danmu-peak-time').textContent = '--:--';

    // 清空热力图并显示加载提示
    drawHeatmapMessage('加载弹幕中...');
    updateHeatmapNodeInfo('正在加载弹幕数据...');

    // 禁用导出按钮，避免导出旧数据
    setDanmuExportEnabled(false);

    // 清空之前的列表
    document.getElementById('danmu-list-container').innerHTML = \`
        <div class="loading-state" style="padding: 2rem;">
            <div class="loading-spinner" style="margin: 0 auto;"></div>
            <p style="margin-top: 1rem; color: var(--text-secondary);">加载弹幕中...</p>
        </div>
    \`;

    // 使用全局遮罩（更明显的“正在加载中”提示）
    if (typeof showLoading === 'function') {
        showLoading('💬 正在加载弹幕...', title);
    }

    // 滚动到显示区域
    setTimeout(() => {
        displayArea.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 100);

    // 保存当前 episodeId 用于导出
    currentEpisodeId = episodeId;

    // 构建查询参数：默认 format=json，如果有其他参数也一并携带
    const queryParams = new URLSearchParams({ format: 'json' });
    const commentUrl = buildApiUrl('/api/v2/comment/' + episodeId + '?' + queryParams.toString());

    fetch(commentUrl)
        .then(response => {
            if (!response.ok) {
                throw new Error(\`HTTP error! status: \${response.status}\`);
            }
            return response.json();
        })
        .then(data => {
            // 如果期间又触发了新的加载，直接丢弃本次结果
            if (mySeq !== activeDanmuLoadSeq) {
                return;
            }

            // 兼容多种返回格式
            // 格式1: {count: 123, comments: [{p: "...", m: "..."}, ...]}
            // 格式2: {success: true, comments: [...]}
            // 格式3: 直接是数组 [{p: "...", m: "..."}, ...]
            // 格式4: {code: 0, data: [...]}

            let comments = null;

            if (Array.isArray(data)) {
                // 直接是数组格式
                comments = data;
            } else if (data.comments && Array.isArray(data.comments)) {
                // 标准格式: {comments: [...]} 或 {count: x, comments: [...]}
                comments = data.comments;
            } else if (data.data && Array.isArray(data.data)) {
                // {code: 0, data: [...]} 格式
                comments = data.data;
            } else if (data.success && data.comments) {
                // {success: true, comments: [...]} 格式
                comments = data.comments;
            }

            if (comments && Array.isArray(comments)) {
                // 标准化弹幕格式，确保每条弹幕都有 p 和 m 属性
                currentDanmuData = comments.map(item => {
                    if (typeof item === 'string') {
                        // 如果是纯文本，转换为标准格式
                        return { p: '0,1,25,16777215,0', m: item };
                    }
                    return {
                        p: item.p || item.time || '0,1,25,16777215,0',
                        m: item.m || item.text || item.content || ''
                    };
                });

                addLog(\`✅ 成功加载 \${currentDanmuData.length} 条弹幕\`, 'success');
                setDanmuExportEnabled(true);
                displayDanmuData(title, currentDanmuData);
            } else {
                throw new Error('弹幕数据格式错误或无弹幕数据');
            }
        })
        .catch(error => {
            // 如果期间又触发了新的加载，直接丢弃本次错误
            if (mySeq !== activeDanmuLoadSeq) {
                return;
            }

            console.error('加载弹幕失败:', error);
            addLog(\`❌ 加载弹幕失败: \${error.message}\`, 'error');
            customAlert('加载弹幕失败: ' + error.message, '❌ 加载失败');

            drawHeatmapMessage('加载失败');
            updateHeatmapNodeInfo('加载失败：请重试或检查接口返回');

            document.getElementById('danmu-list-container').innerHTML = \`
                <div class="search-error">
                    <div class="error-icon">❌</div>
                    <h3>加载失败</h3>
                    <p>\${escapeHtml(error.message)}</p>
                </div>
            \`;
        })
        .finally(() => {
            if (mySeq !== activeDanmuLoadSeq) return;

            if (typeof hideLoading === 'function') {
                hideLoading();
            }

            // 加载完成后更新 subtitle（成功场景会在 displayDanmuData 中覆盖为真实数量）
            if (!currentDanmuData) {
                document.getElementById('danmu-subtitle').textContent = '加载完成（无可用弹幕数据）';
            }
        });
}


/* ========================================
   显示弹幕数据
   ======================================== */
function displayDanmuData(title, comments) {
    // 更新标题
    document.getElementById('danmu-subtitle').textContent = \`共 \${comments.length} 条弹幕\`;
    
    // 计算统计数据
    const stats = calculateDanmuStats(comments);
    
    // 更新统计信息
    document.getElementById('danmu-total-count').textContent = stats.totalCount;
    document.getElementById('danmu-duration').textContent = stats.duration;
    document.getElementById('danmu-density').textContent = stats.density;
    document.getElementById('danmu-peak-time').textContent = stats.peakTime;
    
    // 绘制热力图
    drawHeatmap(comments, stats.maxTime);
    
    // 显示弹幕列表
    filteredDanmuData = comments;
    renderDanmuList(comments);
}

/* ========================================
   计算弹幕统计数据
   ======================================== */
function calculateDanmuStats(comments) {
    const totalCount = comments.length;
    
    // 找出最大时间
    const maxTime = Math.max(...comments.map(c => c.p.split(',')[0]), 0);
    const duration = formatTime(maxTime);
    
    // 计算密度（每分钟）
    const durationMinutes = maxTime / 60;
    const density = durationMinutes > 0 ? Math.round(totalCount / durationMinutes) : 0;
    
    // 找出高能时刻（弹幕最密集的时间段）
    const peakTime = findPeakTime(comments, maxTime);
    
    return {
        totalCount,
        duration,
        density,
        peakTime,
        maxTime
    };
}

/* ========================================
   找出高能时刻
   ======================================== */
function findPeakTime(comments, maxTime) {
    if (comments.length === 0) return '--:--';
    
    // 将时间轴分成30秒的区间
    const interval = 30;
    const intervals = Math.ceil(maxTime / interval);
    const counts = new Array(intervals).fill(0);
    
    comments.forEach(comment => {
        const time = parseFloat(comment.p.split(',')[0]);
        const index = Math.floor(time / interval);
        if (index < intervals) {
            counts[index]++;
        }
    });
    
    // 找出最大值的索引
    const maxCount = Math.max(...counts);
    const maxIndex = counts.indexOf(maxCount);
    
    // 返回该区间的中间时间
    const peakTime = (maxIndex * interval) + (interval / 2);
    return formatTime(peakTime);
}

/* ========================================
   格式化时间
   ======================================== */
function formatTime(seconds) {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = Math.floor(seconds % 60);
    
    if (hours > 0) {
        return \`\${hours}:\${minutes.toString().padStart(2, '0')}:\${secs.toString().padStart(2, '0')}\`;
    } else {
        return \`\${minutes}:\${secs.toString().padStart(2, '0')}\`;
    }
}


/* ========================================
   热力图辅助工具
   ======================================== */
function getCssVarColor(varName, fallback) {
    try {
        const value = getComputedStyle(document.documentElement).getPropertyValue(varName).trim();
        return value || fallback;
    } catch (e) {
        return fallback;
    }
}

function setDanmuExportEnabled(enabled) {
    const jsonBtn = document.getElementById('btn-export-json');
    const xmlBtn = document.getElementById('btn-export-xml');

    [jsonBtn, xmlBtn].forEach(btn => {
        if (!btn) return;
        btn.disabled = !enabled;
        if (enabled) {
            btn.classList.remove('disabled');
        } else {
            btn.classList.add('disabled');
        }
    });
}

function drawHeatmapMessage(message) {
    const canvas = document.getElementById('danmu-heatmap-canvas');
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    canvas.width = canvas.offsetWidth;
    canvas.height = 150;

    const width = canvas.width;
    const height = canvas.height;

    ctx.clearRect(0, 0, width, height);

    const textColor = getCssVarColor('--text-secondary', '#6b7280');
    ctx.fillStyle = textColor;
    ctx.font = '14px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(message, width / 2, height / 2);
}

function updateHeatmapNodeInfo(text) {
    const infoEl = document.getElementById('heatmap-node-info');
    if (!infoEl) return;
    infoEl.innerHTML = text;
}

function ensureHeatmapTooltip() {
    if (heatmapTooltipEl) return heatmapTooltipEl;

    const card = document.querySelector('.danmu-heatmap-card');
    if (!card) return null;

    const tooltip = document.createElement('div');
    tooltip.className = 'heatmap-tooltip';
    tooltip.id = 'heatmap-tooltip';
    tooltip.innerHTML = '';
    card.appendChild(tooltip);

    heatmapTooltipEl = tooltip;
    return tooltip;
}

function showHeatmapTooltip(x, y, html) {
    const tooltip = ensureHeatmapTooltip();
    if (!tooltip) return;

    tooltip.innerHTML = html;
    tooltip.style.left = x + 'px';
    tooltip.style.top = y + 'px';
    tooltip.classList.add('visible');
}

function hideHeatmapTooltip() {
    if (!heatmapTooltipEl) return;
    heatmapTooltipEl.classList.remove('visible');
}

function getHeatmapSegmentIndexByEvent(canvas, event) {
    if (!heatmapState || !canvas) return null;

    const rect = canvas.getBoundingClientRect();
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;

    // label 区域不响应（避免误触）
    if (y > heatmapState.barAreaHeight) return null;

    const index = Math.floor((x / rect.width) * heatmapState.segments);
    if (index < 0 || index >= heatmapState.segments) return null;
    return index;
}

function formatHeatmapRangeText(start, end) {
    if (end <= 0) return '0:00';
    if (Math.floor(start) === Math.floor(end)) return formatTime(start);
    return formatTime(start) + ' - ' + formatTime(end);
}

/* ========================================
   初始化热力图交互
   ======================================== */
function initDanmuHeatmapInteraction() {
    if (heatmapInteractionInited) return;
    heatmapInteractionInited = true;

    const canvas = document.getElementById('danmu-heatmap-canvas');
    if (!canvas) return;

    canvas.addEventListener('mousemove', function(e) {
        if (!heatmapState) return;

        const index = getHeatmapSegmentIndexByEvent(canvas, e);
        if (index === null) {
            hideHeatmapTooltip();
            return;
        }

        const start = index * heatmapState.segmentDuration;
        const end = Math.min((index + 1) * heatmapState.segmentDuration, heatmapState.maxTime);
        const count = heatmapState.counts[index] || 0;

        const rect = canvas.getBoundingClientRect();
        const localX = e.clientX - rect.left;
        const localY = e.clientY - rect.top;

        // tooltip 在 card 内定位，需要加上 canvas 在 card 内的偏移
        const canvasOffsetLeft = canvas.offsetLeft;
        const canvasOffsetTop = canvas.offsetTop;

        const tooltipX = Math.min(canvasOffsetLeft + localX + 12, (canvasOffsetLeft + canvas.clientWidth) - 20);
        const tooltipY = Math.max(canvasOffsetTop + localY - 40, 8);

        showHeatmapTooltip(tooltipX, tooltipY, \`<div><strong>\${formatHeatmapRangeText(start, end)}</strong></div><div>弹幕数：<strong>\${count}</strong></div>\`);
    });

    canvas.addEventListener('mouseleave', function() {
        hideHeatmapTooltip();
    });

    canvas.addEventListener('click', function(e) {
        if (!heatmapState) return;

        const index = getHeatmapSegmentIndexByEvent(canvas, e);
        if (index === null) return;

        heatmapSelectedIndex = index;
        drawHeatmap(heatmapState.originalComments, heatmapState.maxTime);

        const start = index * heatmapState.segmentDuration;
        const end = Math.min((index + 1) * heatmapState.segmentDuration, heatmapState.maxTime);
        const count = heatmapState.counts[index] || 0;

        updateHeatmapNodeInfo(\`已选区间：<strong>\${formatHeatmapRangeText(start, end)}</strong>，弹幕数：<strong>\${count}</strong>\`);
    });

    // 处理窗口尺寸变化（避免缩放后坐标错位）
    window.addEventListener('resize', function() {
        if (!heatmapState || !heatmapState.originalComments) return;
        drawHeatmap(heatmapState.originalComments, heatmapState.maxTime);
    });
}

/* ========================================
   绘制热力图
   ======================================== */
function drawHeatmap(comments, maxTime) {
    const canvas = document.getElementById('danmu-heatmap-canvas');
    if (!canvas) return;

    const ctx = canvas.getContext('2d');

    // 设置 canvas 尺寸（同时提升时间标记可读性，预留 label 区域）
    canvas.width = canvas.offsetWidth;
    canvas.height = 150;

    const width = canvas.width;
    const height = canvas.height;

    const labelAreaHeight = 26;
    const barAreaHeight = height - labelAreaHeight;

    // 清空画布
    ctx.clearRect(0, 0, width, height);

    // 解析主题色（canvas 不支持直接使用 var(--xx)）
    const borderColor = getCssVarColor('--border-color', '#e5e7eb');
    const textColor = getCssVarColor('--text-secondary', '#6b7280');
    const textStrong = getCssVarColor('--text-primary', '#111827');
    const bgSecondary = getCssVarColor('--bg-secondary', '#f3f4f6');
    const primaryColor = getCssVarColor('--primary-color', '#3b82f6');

    // 如果没有弹幕，显示提示
    if (!comments || comments.length === 0) {
        ctx.fillStyle = textColor;
        ctx.font = '14px sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText('暂无弹幕数据', width / 2, height / 2);

        heatmapState = null;
        heatmapSelectedIndex = null;
        updateHeatmapNodeInfo('暂无弹幕数据');
        return;
    }

    // 将时间轴分成若干段（段数跟随宽度变化，保持可读性）
    const safeMaxTime = Math.max(parseFloat(maxTime) || 0, 1);
    const segments = Math.min(Math.ceil(width / 6), 240);
    const segmentDuration = safeMaxTime / segments;
    const counts = new Array(segments).fill(0);

    // 统计每段的弹幕数量
    comments.forEach(comment => {
        const t = parseFloat((comment.p || '0').split(',')[0]) || 0;
        const index = Math.min(Math.floor(t / segmentDuration), segments - 1);
        counts[index]++;
    });

    // 找出最大值用于归一化
    const maxCount = Math.max(...counts, 1);

    // 绘制 label 区域背景（提升时间标记可读性）
    ctx.save();
    ctx.globalAlpha = 0.92;
    ctx.fillStyle = bgSecondary;
    ctx.fillRect(0, barAreaHeight, width, labelAreaHeight);
    ctx.restore();

    // 绘制热力柱
    const segmentWidth = width / segments;

    counts.forEach((count, index) => {
        const ratio = count / maxCount;
        const barHeight = Math.max(ratio * (barAreaHeight - 8), 2);
        const x = index * segmentWidth;
        const y = barAreaHeight - barHeight;

        // 根据密度选择颜色（保持原逻辑但提升对比度）
        let color;
        if (ratio < 0.25) {
            color = \`rgba(59, 130, 246, \${0.25 + ratio * 0.75})\`;
        } else if (ratio < 0.5) {
            color = \`rgba(139, 92, 246, \${0.35 + ratio * 0.65})\`;
        } else if (ratio < 0.75) {
            color = \`rgba(236, 72, 153, \${0.45 + ratio * 0.55})\`;
        } else {
            color = \`rgba(239, 68, 68, \${0.55 + ratio * 0.45})\`;
        }

        ctx.fillStyle = color;
        ctx.fillRect(x, y, segmentWidth, barHeight);

        // 选中高亮
        if (heatmapSelectedIndex === index) {
            ctx.strokeStyle = primaryColor;
            ctx.lineWidth = 2;
            ctx.strokeRect(x + 0.5, y + 0.5, Math.max(segmentWidth - 1, 1), Math.max(barHeight - 1, 1));
        }
    });

    // 绘制基准线
    ctx.strokeStyle = borderColor;
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(0, barAreaHeight + 0.5);
    ctx.lineTo(width, barAreaHeight + 0.5);
    ctx.stroke();

    // 添加时间标记（根据宽度自适应，避免重叠）
    const minLabelGap = 70;
    const timeMarkers = Math.max(4, Math.min(8, Math.floor(width / minLabelGap)));

    ctx.fillStyle = textColor;
    ctx.font = '12px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'alphabetic';

    for (let i = 0; i <= timeMarkers; i++) {
        const x = (width / timeMarkers) * i;
        const time = (safeMaxTime / timeMarkers) * i;

        // 刻度线
        ctx.strokeStyle = borderColor;
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(x + 0.5, barAreaHeight);
        ctx.lineTo(x + 0.5, barAreaHeight + 6);
        ctx.stroke();

        // 文本（更清晰）
        ctx.fillStyle = textColor;
        ctx.fillText(formatTime(time), x, barAreaHeight + 20);
    }

    // 更新热力图状态（用于交互）
    heatmapState = {
        originalComments: comments,
        counts,
        segments,
        segmentDuration,
        maxTime: safeMaxTime,
        barAreaHeight
    };

    // 初始化交互
    initDanmuHeatmapInteraction();

    // 默认提示
    if (heatmapSelectedIndex === null) {
        updateHeatmapNodeInfo(\`提示：鼠标悬停可查看区间；点击柱状条可锁定。每个节点约 <strong>\${Math.max(Math.round(segmentDuration), 1)}</strong> 秒\`);
    } else {
        const start = heatmapSelectedIndex * segmentDuration;
        const end = Math.min((heatmapSelectedIndex + 1) * segmentDuration, safeMaxTime);
        const selectedCount = counts[heatmapSelectedIndex] || 0;
        updateHeatmapNodeInfo(\`已选区间：<strong>\${formatHeatmapRangeText(start, end)}</strong>，弹幕数：<strong>\${selectedCount}</strong>\`);
    }
}


/* ========================================
   渲染弹幕列表（分页优化版）
   ======================================== */
function renderDanmuList(comments) {
    const container = document.getElementById('danmu-list-container');
    
    if (comments.length === 0) {
        container.innerHTML = \`
            <div class="danmu-list-empty">
                <span class="empty-icon">💬</span>
                <p>暂无弹幕数据</p>
            </div>
        \`;
        return;
    }
    
    // 统计各类型弹幕数量
    const typeCounts = {
        all: comments.length,
        scroll: 0,
        top: 0,
        bottom: 0
    };
    
    comments.forEach(comment => {
        const mode = parseInt(comment.p.split(',')[1]);
        if (mode === 5) typeCounts.top++;
        else if (mode === 4) typeCounts.bottom++;
        else typeCounts.scroll++;
    });
    
    // 更新过滤器计数
    document.getElementById('filter-all-count').textContent = typeCounts.all;
    document.getElementById('filter-scroll-count').textContent = typeCounts.scroll;
    document.getElementById('filter-top-count').textContent = typeCounts.top;
    document.getElementById('filter-bottom-count').textContent = typeCounts.bottom;
    
    // 重置分页并清空容器
    currentDanmuPage = 0;
    container.innerHTML = '';
    
    // 渲染第一页
    loadMoreDanmu(comments, container);
}

/* ========================================
   加载更多弹幕（分页）
   ======================================== */
function loadMoreDanmu(comments, container) {
    const start = currentDanmuPage * DANMU_PAGE_SIZE;
    const end = Math.min(start + DANMU_PAGE_SIZE, comments.length);
    const pageComments = comments.slice(start, end);
    
    // 移除之前的"加载更多"按钮和结束提示
    const oldLoadMoreBtn = container.querySelector('.load-more-btn');
    if (oldLoadMoreBtn) oldLoadMoreBtn.remove();
    const oldEndDiv = container.querySelector('.danmu-list-end');
    if (oldEndDiv) oldEndDiv.remove();
    
    // 使用 DocumentFragment 优化 DOM 操作
    const fragment = document.createDocumentFragment();
    
    pageComments.forEach((comment) => {
        const parts = comment.p.split(',');
        const time = parts[0];
        const mode = parts[1];
        const modeInt = parseInt(mode);
        
        // 正确解析颜色值
        // 后端返回格式：时间,类型,颜色,来源 (4字段)
        // 示例：5.0,1,16777215,[qq]
        let colorInt = 16777215; // 默认白色
        
        // 直接从第3个字段（索引2）读取颜色
        const colorField = parts[2];
        
        if (colorField) {
            // 尝试解析为十进制数字
            const parsed = parseInt(colorField, 10);
            if (!isNaN(parsed) && parsed >= 0 && parsed <= 16777215) {
                colorInt = parsed;
            } else {
                // 尝试解析十六进制格式
                const hexMatch = String(colorField).trim()
                    .replace(/^0x/i, '')
                    .replace(/^#/, '');
                if (/^[0-9a-fA-F]{6}$/.test(hexMatch)) {
                    colorInt = parseInt(hexMatch, 16);
                }
            }
        }        
        // 转换为十六进制颜色字符串
        const hexColor = '#' + colorInt.toString(16).padStart(6, '0').toUpperCase();
        
        let typeClass = '';
        let typeName = '滚动';
        
        if (modeInt === 5) {
            typeClass = 'type-top';
            typeName = '顶部';
        } else if (modeInt === 4) {
            typeClass = 'type-bottom';
            typeName = '底部';
        }
        
        const itemDiv = document.createElement('div');
        itemDiv.className = 'danmu-item ' + typeClass;
        itemDiv.innerHTML = \`
            <div class="danmu-item-time">\${formatTime(parseFloat(time))}</div>
            <div class="danmu-item-content">
                <div class="danmu-item-text">\${escapeHtml(comment.m)}</div>
                <div class="danmu-item-meta">
                    <span class="danmu-item-type">\${typeName}</span>
                    <span style="display: inline-flex; align-items: center; gap: 0.35rem;">
                        <span style="width: 10px; height: 10px; border-radius: 999px; background-color: \${hexColor} !important; border: 1px solid rgba(0,0,0,0.15);"></span>
                        <span style="color: \${hexColor} !important;">\${hexColor}</span>
                    </span>
                </div>
            </div>
        \`;
        
        fragment.appendChild(itemDiv);
    });

    
    container.appendChild(fragment);
    
    // 更新页码
    currentDanmuPage++;
    
    // 如果还有更多数据，添加"加载更多"按钮
    if (end < comments.length) {
        const remaining = comments.length - end;
        const loadMoreBtn = document.createElement('div');
        loadMoreBtn.className = 'load-more-btn';
        loadMoreBtn.style.cssText = 'padding: 1rem; text-align: center;';
        loadMoreBtn.innerHTML = \`
            <button class="btn btn-secondary" onclick="loadMoreDanmuClick()" style="width: 100%; max-width: 300px;">
                <svg class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                    <path d="M19 9l-7 7-7-7"/>
                </svg>
                <span>加载更多 (还剩 \${remaining} 条)</span>
            </button>
        \`;
        container.appendChild(loadMoreBtn);
    } else {
        // 显示已加载完毕
        const endDiv = document.createElement('div');
        endDiv.className = 'danmu-list-end';
        endDiv.style.cssText = 'padding: 1.5rem; text-align: center; color: var(--text-tertiary); font-size: 0.875rem;';
        endDiv.innerHTML = \`<span>— 已加载全部 \${comments.length} 条弹幕 —</span>\`;
        container.appendChild(endDiv);
    }
}



/* ========================================
   加载更多按钮点击事件
   ======================================== */
function loadMoreDanmuClick() {
    const container = document.getElementById('danmu-list-container');
    loadMoreDanmu(filteredDanmuData, container);
}
/* ========================================
   过滤弹幕列表
   ======================================== */
function filterDanmuList(type) {
    // 更新按钮状态
    document.querySelectorAll('.danmu-filter-btn').forEach(btn => {
        btn.classList.remove('active');
        if (btn.dataset.type === type) {
            btn.classList.add('active');
        }
    });
    
    if (!currentDanmuData) return;
    
    let filtered = currentDanmuData;
    
    if (type !== 'all') {
        filtered = currentDanmuData.filter(comment => {
            const mode = parseInt(comment.p.split(',')[1]);
            if (type === 'scroll') return mode !== 4 && mode !== 5;
            if (type === 'top') return mode === 5;
            if (type === 'bottom') return mode === 4;
            return true;
        });
    }
    
    filteredDanmuData = filtered;
    currentDanmuPage = 0;  // 重置分页
    renderDanmuList(filtered);
    
    addLog(\`🔍 筛选弹幕: \${type} (\${filtered.length}条)\`, 'info');
}
/* ========================================
   格式化弹幕文件名
   ======================================== */
function formatDanmuFilename(rawTitle, format) {
    // 原始格式示例: 奇迹(2025)【电视剧】from tencent - 【qq】 01闯南关(上)_01
    // 目标格式示例: 奇迹(2025) - 01 - 闯南关(上).xml
    
    // 移除来源信息（from xxx - 【xxx】）
    let cleaned = rawTitle.replace(/\\s*from\\s+[^-]+\\s*-\\s*【[^】]+】\\s*/g, '');
    
    // 移除【电视剧】【电影】等类型标签
    cleaned = cleaned.replace(/【[^】]*剧[^】]*】/g, '');
    cleaned = cleaned.replace(/【电影】/g, '');
    
    // 提取剧名(年份)
    const nameYearMatch = cleaned.match(/^(.+?)\\((\\d{4})\\)/);
    if (!nameYearMatch) {
        // 如果无法解析年份，使用简化的清理逻辑
        cleaned = cleaned.replace(/\\s+/g, '_');
        cleaned = cleaned.replace(/[\\\\/:*?"<>|]/g, '');
        cleaned = cleaned.replace(/_+/g, '_');
        cleaned = cleaned.replace(/^_|_$/g, '');
        return \`\${cleaned}.\${format}\`;
    }
    
    const animeName = nameYearMatch[1].trim();
    const year = nameYearMatch[2];
    const nameWithYear = \`\${animeName}(\${year})\`;
    
    // 移除剧名(年份)部分，获取剩余内容
    let remaining = cleaned.substring(nameYearMatch[0].length).trim();
    remaining = remaining.replace(/^[_\\s-]+/, ''); // 移除开头的分隔符
    
    if (!remaining) {
        return \`\${nameWithYear}.\${format}\`;
    }
    
    // 提取集数（第一个连续的数字）
    const episodeMatch = remaining.match(/^(\\d+)/);
    if (!episodeMatch) {
        // 没有集数，直接返回
        const cleaned2 = remaining.replace(/[\\\\/:*?"<>|]/g, '').replace(/_+/g, '_').replace(/^_|_$/g, '');
        return \`\${nameWithYear} - \${cleaned2}.\${format}\`;
    }
    
    const episodeNum = episodeMatch[1];
    
    // 移除集数部分，获取集标题
    let episodeTitle = remaining.substring(episodeNum.length).trim();
    episodeTitle = episodeTitle.replace(/^[_\\s-]+/, ''); // 移除开头的分隔符
    
    // 移除集标题末尾重复的集数（如 _01, _1 等）
    episodeTitle = episodeTitle.replace(/_\\d+$/, '');
    episodeTitle = episodeTitle.trim();
    
    // 清理集标题中的非法文件名字符
    episodeTitle = episodeTitle.replace(/[\\\\/:*?"<>|]/g, '');
    
    // 组合最终文件名
    if (episodeTitle) {
        return \`\${nameWithYear} - \${episodeNum} - \${episodeTitle}.\${format}\`;
    } else {
        return \`\${nameWithYear} - \${episodeNum}.\${format}\`;
    }
}

/* ========================================
   导出弹幕
   ======================================== */
function exportDanmu(format) {
    // 如果有 episodeId，优先从后端直接获取对应格式
    if (currentEpisodeId) {
        const title = document.getElementById('danmu-title').textContent;
        const filename = formatDanmuFilename(title, format);
        
        addLog(\`📥 开始导出弹幕: \${filename}\`, 'info');
        
        const exportUrl = buildApiUrl('/api/v2/comment/' + currentEpisodeId + '?format=' + format);
        
        fetch(exportUrl)
            .then(response => {
                if (!response.ok) {
                    throw new Error(\`HTTP error! status: \${response.status}\`);
                }
                return response.text();
            })
            .then(content => {
                const mimeType = format === 'xml' ? 'application/xml' : 'application/json';
                const blob = new Blob([content], { type: mimeType + ';charset=utf-8' });
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = filename;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                URL.revokeObjectURL(url);
                
                addLog(\`✅ 导出弹幕成功: \${filename}\`, 'success');
                customAlert(\`弹幕已导出为 \${format.toUpperCase()} 格式\`, '✅ 导出成功');
            })
            .catch(error => {
                console.error('导出弹幕失败:', error);
                addLog(\`❌ 导出弹幕失败: \${error.message}\`, 'error');
                // 如果后端导出失败，尝试使用本地数据
                exportDanmuFromLocal(format);
            });
        return;
    }
    
    // 如果没有 episodeId，使用本地数据导出
    exportDanmuFromLocal(format);
}

/* ========================================
   从本地数据导出弹幕（备用方案）
   ======================================== */
function exportDanmuFromLocal(format) {
    if (!currentDanmuData || currentDanmuData.length === 0) {
        customAlert('没有可导出的弹幕数据', '⚠️ 提示');
        return;
    }
    
    const title = document.getElementById('danmu-title').textContent;
    const filename = formatDanmuFilename(title, format);
    
    let content = '';
    let mimeType = '';
    
    if (format === 'json') {
        content = JSON.stringify(currentDanmuData, null, 2);
        mimeType = 'application/json';
    } else if (format === 'xml') {
        content = convertToXML(currentDanmuData);
        mimeType = 'application/xml';
    }
    
    const blob = new Blob([content], { type: mimeType + ';charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    
    addLog(\`📥 导出弹幕: \${filename}\`, 'success');
    customAlert(\`弹幕已导出为 \${format.toUpperCase()} 格式\`, '✅ 导出成功');
}

/* ========================================
   转换为 XML 格式
   ======================================== */
function convertToXML(comments) {
    let xml = '<?xml version="1.0" encoding="UTF-8"?>\\n';
    xml += '<i>\\n';
    xml += '  <chatserver>chat.bilibili.com</chatserver>\\n';
    xml += '  <chatid>0</chatid>\\n';
    xml += '  <mission>0</mission>\\n';
    xml += \`  <maxlimit>\${comments.length}</maxlimit>\\n\`;
    xml += '  <state>0</state>\\n';
    xml += '  <real_name>0</real_name>\\n';
    xml += '  <source>logvar-danmu-api</source>\\n';
    
    comments.forEach(comment => {
        const p = comment.p;
        const m = escapeXml(comment.m);
        xml += \`  <d p="\${p}">\${m}</d>\\n\`;
    });
    
    xml += '</i>';
    return xml;
}

/* ========================================
   转义 XML 特殊字符
   ======================================== */
function escapeXml(text) {
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&apos;'
    };
    return String(text).replace(/[&<>"']/g, m => map[m]);
}
`;