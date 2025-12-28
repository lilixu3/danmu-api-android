// language=CSS
export const dynamicCssContent = /* css */ `
/* ========================================
   内容区块样式
   ======================================== */
.content-section {
    display: none;
    animation: fadeInUp 0.4s ease-out;
}

.content-section.active {
    display: block;
}

.section-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 2rem;
    padding-bottom: 1.5rem;
    border-bottom: 2px solid var(--border-color);
}

.section-title {
    position: relative;
    font-size: 1.875rem;
    font-weight: 700;
    color: var(--text-primary);
    background: var(--gradient-primary);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
    padding-left: 1.25rem;
}

.section-title::before {
    content: '';
    position: absolute;
    left: 0;
    top: 50%;
    transform: translateY(-50%);
    width: 5px;
    height: 75%;
    background: linear-gradient(180deg, 
        rgba(99, 102, 241, 0.9) 0%, 
        rgba(139, 92, 246, 0.9) 50%,
        rgba(236, 72, 153, 0.9) 100%);
    border-radius: 3px;
    box-shadow: 
        0 0 12px rgba(99, 102, 241, 0.4),
        0 0 24px rgba(139, 92, 246, 0.2);
    animation: titlePulse 3s ease-in-out infinite;
}

[data-theme="dark"] .section-title::before {
    background: linear-gradient(180deg, 
        rgba(129, 140, 248, 1) 0%, 
        rgba(167, 139, 250, 1) 35%,
        rgba(192, 132, 252, 1) 65%,
        rgba(236, 72, 153, 1) 100%);
    box-shadow: 
        0 0 20px rgba(129, 140, 248, 0.8),
        0 0 40px rgba(167, 139, 250, 0.4),
        0 0 60px rgba(192, 132, 252, 0.2);
}

[data-theme="dark"] .section-title::after {
    content: '';
    position: absolute;
    left: 0;
    top: 50%;
    transform: translateY(-50%);
    width: 5px;
    height: 75%;
    background: inherit;
    border-radius: 3px;
    filter: blur(8px);
    opacity: 0.6;
    z-index: -1;
}

@keyframes titlePulse {
    0%, 100% {
        opacity: 1;
        filter: brightness(1);
    }
    50% {
        opacity: 0.8;
        filter: brightness(1.2);
    }
}

.section-desc {
    color: var(--text-secondary);
    font-size: 0.9375rem;
    margin-top: 0.5rem;
    line-height: 1.6;
}

.header-actions {
    display: flex;
    gap: 0.75rem;
    flex-wrap: wrap;
}

/* ========================================
   配置预览组件
   ======================================== */
.preview-hero-card {
    background: rgba(255, 255, 255, 1);
    border-radius: var(--radius-xl);
    padding: 2rem;
    margin-bottom: 2rem;
    border: 1px solid var(--border-color);
    box-shadow: none;
}
/* 深色模式预览卡片增强 */
[data-theme="dark"] .preview-hero-card {
    background: rgba(17, 24, 39, 0.8);
    border: 1px solid rgba(99, 102, 241, 0.25);
    box-shadow: 0 8px 40px rgba(0, 0, 0, 0.6),
                0 0 60px rgba(99, 102, 241, 0.1),
                inset 0 1px 0 rgba(255, 255, 255, 0.05);
}

[data-theme="dark"] .preview-stat-card {
    background: rgba(17, 24, 39, 0.7);
    border: 1px solid rgba(99, 102, 241, 0.2);
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.4),
                inset 0 1px 0 rgba(255, 255, 255, 0.05);
}

[data-theme="dark"] .preview-stat-card:hover {
    border-color: rgba(129, 140, 248, 0.4);
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5),
                0 0 40px rgba(129, 140, 248, 0.2),
                inset 0 1px 0 rgba(255, 255, 255, 0.08);
    transform: translateY(-6px);
}

[data-theme="dark"] .preview-category {
    background: rgba(17, 24, 39, 0.7);
    border: 1px solid rgba(99, 102, 241, 0.2);
}

[data-theme="dark"] .preview-category:hover {
    border-color: rgba(129, 140, 248, 0.4);
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5),
                0 0 40px rgba(99, 102, 241, 0.15);
}

.preview-hero-content {
    display: flex;
    flex-direction: column;
    gap: 2rem;
}

.preview-hero-header {
    display: flex;
    align-items: center;
    gap: 1.5rem;
}

.preview-hero-icon {
    width: 64px;
    height: 64px;
    background: var(--gradient-primary);
    border-radius: var(--radius-lg);
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 8px 24px rgba(59, 130, 246, 0.3);
}

.preview-hero-icon svg {
    width: 36px;
    height: 36px;
    color: white;
    stroke-width: 2;
}

.preview-hero-titles {
    flex: 1;
}

.preview-hero-title {
    font-size: 1.75rem;
    font-weight: 700;
    color: var(--text-primary);
    margin-bottom: 0.5rem;
}

.preview-hero-subtitle {
    color: var(--text-secondary);
    font-size: 1rem;
}

.preview-stats-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
    gap: 1rem;
}

.preview-stat-card {
    background: rgba(248, 250, 252, 1);
    backdrop-filter: none;
    border-radius: var(--radius-lg);
    padding: 1.5rem;
    border: 1px solid var(--border-color);
    transition: all var(--transition-fast);
    display: flex;
    align-items: center;
    gap: 1rem;
}

.preview-stat-card:hover {
    transform: translateY(-4px);
    box-shadow: var(--shadow-md);
    border-color: var(--primary-color);
}

.stat-card-compact {
    padding: 1.25rem;
}

.stat-icon-wrapper {
    width: 48px;
    height: 48px;
    border-radius: var(--radius-md);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
}

.stat-icon-wrapper svg {
    width: 24px;
    height: 24px;
    color: white;
    stroke-width: 2;
}

.stat-icon-primary {
    background: var(--gradient-primary);
    box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.stat-icon-success {
    background: var(--gradient-success);
    box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
}

.stat-icon-warning {
    background: var(--gradient-warning);
    box-shadow: 0 4px 12px rgba(245, 158, 11, 0.3);
}

.stat-icon-deploy {
    background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
    box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.stat-icon-status {
    background: linear-gradient(135deg, #10b981 0%, #059669 100%);
    box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
    transition: all var(--transition-fast);
}

.stat-icon-status.status-warning {
    background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
    box-shadow: 0 4px 12px rgba(245, 158, 11, 0.3);
}

.stat-icon-status.status-error {
    background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
    box-shadow: 0 4px 12px rgba(239, 68, 68, 0.3);
}

.stat-icon-mode {
    background: linear-gradient(135deg, #ec4899 0%, #8b5cf6 100%);
    box-shadow: 0 4px 12px rgba(236, 72, 153, 0.3);
}

.stat-content {
    flex: 1;
    min-width: 0;
}

.stat-value {
    font-size: 1.75rem;
    font-weight: 700;
    color: var(--text-primary);
    line-height: 1.2;
    font-family: 'Courier New', monospace;
}

.stat-value-text {
    font-size: 1rem;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
}

.stat-value-status {
    font-size: 0.875rem;
}

.stat-label {
    font-size: 0.8125rem;
    color: var(--text-secondary);
    margin-top: 0.25rem;
    font-weight: 500;
}

.preview-grid {
    display: flex;
    flex-direction: column;
    gap: 1.5rem;
}

.preview-category {
    background: var(--bg-primary);
    backdrop-filter: var(--blur-md);
    border-radius: var(--radius-lg);
    padding: 1.5rem;
    border: 1px solid var(--border-color);
    box-shadow: var(--shadow-sm);
    transition: all var(--transition-fast);
}

.preview-category:hover {
    box-shadow: var(--shadow-md);
    transform: translateY(-2px);
}

.preview-category-header {
    margin-bottom: 1.5rem;
    padding-bottom: 1rem;
    border-bottom: 1px solid var(--border-color);
}

.preview-category-title {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    font-size: 1.25rem;
    font-weight: 600;
    color: var(--text-primary);
}

.category-icon {
    width: 40px;
    height: 40px;
    border-radius: var(--radius-md);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 1.25rem;
    color: white;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.category-badge {
    margin-left: auto;
    padding: 0.25rem 0.75rem;
    background: var(--bg-tertiary);
    backdrop-filter: var(--blur-sm);
    border-radius: var(--radius-sm);
    font-size: 0.8125rem;
    font-weight: 500;
    color: var(--text-secondary);
}

.preview-items {
    display: flex;
    flex-direction: column;
    gap: 1rem;
}

.preview-item {
    padding: 1rem;
    background: var(--bg-secondary);
    backdrop-filter: var(--blur-sm);
    border-radius: var(--radius-md);
    border: 1px solid var(--border-color);
    transition: all var(--transition-fast);
}

.preview-item:hover {
    background: var(--bg-tertiary);
    border-color: var(--primary-color);
    transform: translateX(4px);
}

.preview-item-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 0.75rem;
    gap: 1rem;
}

.preview-key {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    font-size: 0.9375rem;
    font-weight: 600;
    color: var(--text-primary);
}

.key-icon {
    font-size: 1rem;
}

.preview-type-badge {
    padding: 0.25rem 0.625rem;
    background: var(--primary-color);
    color: white;
    border-radius: var(--radius-sm);
    font-size: 0.75rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.5px;
}

.preview-value-container {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    margin-bottom: 0.5rem;
}

.preview-value {
    flex: 1;
    padding: 0.75rem;
    background: var(--bg-tertiary);
    backdrop-filter: var(--blur-sm);
    border-radius: var(--radius-sm);
    font-family: 'Courier New', monospace;
    font-size: 0.875rem;
    color: var(--text-primary);
    border: 1px solid var(--border-color);
    word-break: break-all;
}

.preview-copy-btn {
    flex-shrink: 0;
    width: 36px;
    height: 36px;
    background: var(--bg-primary);
    backdrop-filter: var(--blur-sm);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-sm);
    cursor: pointer;
    transition: all var(--transition-fast);
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--text-secondary);
}

.preview-copy-btn:hover {
    background: var(--primary-color);
    border-color: var(--primary-color);
    color: white;
    transform: scale(1.1);
}

.preview-copy-btn svg {
    width: 16px;
    height: 16px;
}

.preview-desc {
    display: flex;
    align-items: flex-start;
    gap: 0.5rem;
    font-size: 0.8125rem;
    color: var(--text-tertiary);
    line-height: 1.6;
}

.desc-icon {
    font-size: 1rem;
    flex-shrink: 0;
    margin-top: 2px;
}

.preview-empty,
.preview-error {
    text-align: center;
    padding: 4rem 2rem;
}

.empty-icon,
.error-icon {
    font-size: 4rem;
    margin-bottom: 1rem;
    opacity: 0.5;
}

.preview-empty h3,
.preview-error h3 {
    font-size: 1.25rem;
    font-weight: 600;
    color: var(--text-primary);
    margin-bottom: 0.5rem;
}

.preview-empty p,
.preview-error p {
    color: var(--text-secondary);
    font-size: 0.9375rem;
}

/* ========================================
   日志查看组件
   ======================================== */
.log-filters {
    display: flex;
    gap: 0.75rem;
    margin-bottom: 1.5rem;
    flex-wrap: wrap;
}

.log-filter-btn {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.75rem 1.25rem;
    background: var(--bg-primary);
    backdrop-filter: var(--blur-md);
    border: 2px solid var(--border-color);
    border-radius: var(--radius-md);
    cursor: pointer;
    transition: all var(--transition-fast);
    font-weight: 500;
    color: var(--text-secondary);
}

.log-filter-btn:hover {
    background: var(--bg-secondary);
    border-color: var(--primary-color);
    transform: translateY(-2px);
    box-shadow: var(--shadow-sm);
}

.log-filter-btn.active {
    background: var(--gradient-primary);
    border-color: var(--primary-color);
    color: white;
    box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.filter-icon {
    font-size: 1.25rem;
}

.filter-text {
    font-size: 0.9375rem;
}

.filter-badge {
    display: none;
    padding: 0.125rem 0.5rem;
    background: rgba(255, 255, 255, 0.2);
    border-radius: var(--radius-sm);
    font-size: 0.75rem;
    font-weight: 600;
    min-width: 24px;
    text-align: center;
}

.log-filter-btn.active .filter-badge {
    background: rgba(255, 255, 255, 0.3);
}

.log-terminal {
    background: var(--bg-primary);
    backdrop-filter: var(--blur-md);
    border-radius: var(--radius-lg);
    padding: 1.5rem;
    border: 1px solid var(--border-color);
    box-shadow: var(--shadow-md);
    max-height: 600px;
    overflow-y: auto;
    font-family: 'Courier New', monospace;
}

.log-entry {
    display: flex;
    gap: 1rem;
    padding: 0.875rem;
    margin-bottom: 0.5rem;
    background: var(--bg-secondary);
    backdrop-filter: var(--blur-sm);
    border-radius: var(--radius-md);
    border-left: 3px solid var(--border-color);
    transition: all var(--transition-fast);
    animation: fadeInUp 0.3s ease-out;
}

.log-entry:hover {
    background: var(--bg-tertiary);
    transform: translateX(4px);
}

.log-entry.log-error {
    border-left-color: var(--danger-color);
    background: rgba(239, 68, 68, 0.05);
}

.log-entry.log-warn {
    border-left-color: var(--warning-color);
    background: rgba(245, 158, 11, 0.05);
}

.log-entry.log-success {
    border-left-color: var(--success-color);
    background: rgba(16, 185, 129, 0.05);
}

.log-entry.log-info {
    border-left-color: var(--primary-color);
    background: rgba(59, 130, 246, 0.05);
}

.log-meta {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    flex-shrink: 0;
}

.log-icon {
    font-size: 1rem;
}

.log-time {
    font-size: 0.8125rem;
    color: var(--text-tertiary);
    font-weight: 600;
}

.log-type-tag {
    padding: 0.125rem 0.5rem;
    border-radius: var(--radius-sm);
    font-size: 0.6875rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.5px;
}

.log-type-error {
    background: var(--danger-color);
    color: white;
}

.log-type-warn {
    background: var(--warning-color);
    color: white;
}

.log-type-success {
    background: var(--success-color);
    color: white;
}

.log-type-info {
    background: var(--primary-color);
    color: white;
}

.log-content {
    flex: 1;
    min-width: 0;
}

.log-message {
    font-size: 0.875rem;
    color: var(--text-primary);
    line-height: 1.6;
    word-break: break-word;
}

.log-message-full {
    font-size: 0.875rem;
    color: var(--text-primary);
    line-height: 1.6;
    word-break: break-word;
    margin-top: 0.5rem;
    padding: 0.75rem;
    background: var(--bg-tertiary);
    border-radius: var(--radius-sm);
}

.log-expand-btn {
    display: flex;
    align-items: center;
    gap: 0.375rem;
    margin-top: 0.5rem;
    padding: 0.375rem 0.75rem;
    background: var(--bg-primary);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-sm);
    color: var(--text-secondary);
    font-size: 0.8125rem;
    cursor: pointer;
    transition: all var(--transition-fast);
}

.log-expand-btn:hover {
    background: var(--bg-tertiary);
    border-color: var(--primary-color);
    color: var(--primary-color);
}

.expand-icon {
    width: 14px;
    height: 14px;
    transition: transform var(--transition-fast);
}

.log-expand-btn.expanded .expand-icon {
    transform: rotate(180deg);
}

.log-empty-state {
    text-align: center;
    padding: 4rem 2rem;
    color: var(--text-tertiary);
}

.log-empty-state .empty-icon {
    width: 64px;
    height: 64px;
    margin: 0 auto 1rem;
    color: var(--text-tertiary);
    opacity: 0.5;
}

.log-empty-state .empty-text {
    font-size: 1rem;
    font-weight: 500;
}

/* ========================================
   环境变量配置组件
   ======================================== */
.category-tabs {
    display: flex;
    gap: 0.5rem;
    margin-bottom: 2rem;
    flex-wrap: wrap;
    padding: 0.5rem;
    background: var(--bg-primary);
    backdrop-filter: var(--blur-md);
    border-radius: var(--radius-lg);
    border: 1px solid var(--border-color);
}

.tab-btn {
    flex: 1;
    min-width: 120px;
    padding: 0.875rem 1.5rem;
    background: var(--bg-secondary);
    backdrop-filter: var(--blur-sm);
    border: 2px solid transparent;
    border-radius: var(--radius-md);
    color: var(--text-secondary);
    font-weight: 500;
    font-size: 0.9375rem;
    cursor: pointer;
    transition: all var(--transition-fast);
    white-space: nowrap;
}

.tab-btn:hover {
    background: var(--bg-tertiary);
    color: var(--text-primary);
    transform: translateY(-2px);
}

.tab-btn.active {
    background: var(--gradient-primary);
    border-color: var(--primary-color);
    color: white;
    box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.env-grid {
    display: flex;
    flex-direction: column;
    gap: 1rem;
}

.env-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 1.5rem;
    padding: 1.5rem;
    background: var(--bg-primary);
    backdrop-filter: var(--blur-md);
    border-radius: var(--radius-lg);
    border: 1px solid var(--border-color);
    box-shadow: var(--shadow-sm);
    transition: all var(--transition-fast);
}

.env-item:hover {
    box-shadow: var(--shadow-md);
    transform: translateY(-2px);
    border-color: var(--primary-color);
}

.env-info {
    flex: 1;
    min-width: 0;
}

.env-key {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    margin-bottom: 0.75rem;
    flex-wrap: wrap;
}

.env-key strong {
    font-size: 1rem;
    font-weight: 600;
    color: var(--text-primary);
}

.value-type-badge {
    padding: 0.25rem 0.625rem;
    background: var(--primary-color);
    color: white;
    border-radius: var(--radius-sm);
    font-size: 0.75rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.5px;
}

.value-type-badge.multi {
    background: var(--gradient-warning);
}

.value-type-badge.color {
    background: linear-gradient(135deg, #ec4899 0%, #8b5cf6 100%);
}

.env-value {
    display: block;
    padding: 0.75rem;
    background: var(--bg-secondary);
    backdrop-filter: var(--blur-sm);
    border-radius: var(--radius-md);
    font-family: 'Courier New', monospace;
    font-size: 0.875rem;
    color: var(--text-primary);
    border: 1px solid var(--border-color);
    margin-bottom: 0.5rem;
    word-break: break-all;
}

.env-desc {
    font-size: 0.8125rem;
    color: var(--text-tertiary);
    line-height: 1.6;
}

.env-actions {
    display: flex;
    gap: 0.5rem;
    flex-shrink: 0;
}

.env-empty-state {
    text-align: center;
    padding: 4rem 2rem;
}

.env-empty-state .empty-icon {
    font-size: 4rem;
    margin-bottom: 1rem;
    opacity: 0.5;
}

.env-empty-state h3 {
    font-size: 1.25rem;
    font-weight: 600;
    color: var(--text-primary);
    margin-bottom: 0.5rem;
}

.env-empty-state p {
    color: var(--text-secondary);
    font-size: 0.9375rem;
}

/* ========================================
   响应式JSON显示
   ======================================== */
.response-card {
    background: var(--bg-primary);
    backdrop-filter: var(--blur-md);
    border-radius: var(--radius-lg);
    padding: 1.5rem;
    border: 1px solid var(--border-color);
    box-shadow: var(--shadow-sm);
    margin-top: 1.5rem;
}

.response-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 1rem;
    margin-bottom: 1rem;
    padding-bottom: 1rem;
    border-bottom: 1px solid var(--border-color);
    flex-wrap: wrap;
}

.response-status {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.5rem 1rem;
    border-radius: var(--radius-md);
    font-weight: 600;
    font-size: 0.9375rem;
}

.response-status.success {
    background: rgba(16, 185, 129, 0.1);
    color: var(--success-color);
    border: 1px solid var(--success-color);
}

.response-status.error {
    background: rgba(239, 68, 68, 0.1);
    color: var(--danger-color);
    border: 1px solid var(--danger-color);
}

.response-time {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    color: var(--text-secondary);
    font-size: 0.875rem;
}

.copy-response-btn {
    margin-left: auto;
}

.copy-response-btn.copied {
    background: var(--success-color) !important;
    border-color: var(--success-color) !important;
}

.response-content {
    padding: 1.5rem;
    background: var(--bg-secondary);
    backdrop-filter: var(--blur-sm);
    border-radius: var(--radius-md);
    font-family: 'Courier New', monospace;
    font-size: 0.875rem;
    line-height: 1.8;
    overflow-x: auto;
    max-height: 600px;
    overflow-y: auto;
    border: 1px solid var(--border-color);
    white-space: pre-wrap;
    word-break: break-word;
}

.response-content.xml {
    white-space: pre-wrap;
    word-break: break-all;
    color: var(--text-primary);
}

.response-content.error {
    color: var(--danger-color);
}

/* JSON语法高亮 */
.json-key {
    color: #8b5cf6;
    font-weight: 600;
}

.json-string {
    color: #10b981;
}

.json-number {
    color: #f59e0b;
}

.json-boolean {
    color: #3b82f6;
    font-weight: 600;
}

.json-null {
    color: #ef4444;
    font-weight: 600;
}

/* ========================================
   自定义对话框
   ======================================== */
.custom-dialog-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.6);
    backdrop-filter: var(--blur-lg);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 10000;
    padding: 1rem;
    animation: fadeIn 0.3s ease;
}

.custom-dialog-container {
    background: var(--bg-primary);
    backdrop-filter: var(--blur-md);
    border-radius: var(--radius-xl);
    box-shadow: var(--shadow-lg);
    max-width: 480px;
    width: 100%;
    border: 1px solid var(--border-color);
    animation: modalSlideIn 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.custom-dialog-header {
    padding: 1.5rem;
    border-bottom: 1px solid var(--border-color);
}

.custom-dialog-header h3 {
    font-size: 1.25rem;
    font-weight: 600;
    color: var(--text-primary);
}

.custom-dialog-body {
    padding: 1.5rem;
}

.custom-dialog-body p {
    color: var(--text-secondary);
    line-height: 1.7;
    margin-bottom: 1rem;
}

.custom-dialog-body p:last-child {
    margin-bottom: 0;
}

.custom-dialog-body strong {
    color: var(--text-primary);
    font-weight: 600;
}

.custom-dialog-actions {
    padding: 1.5rem;
    border-top: 1px solid var(--border-color);
    display: flex;
    gap: 1rem;
    justify-content: flex-end;
}

.custom-dialog-actions .btn {
    flex: 1;
}

/* ========================================
   部署平台徽章
   ======================================== */
.deploy-badge-node {
    background: linear-gradient(135deg, #10b981 0%, #059669 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
}

.deploy-badge-vercel {
    background: linear-gradient(135deg, #000000 0%, #434343 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
}

.deploy-badge-netlify {
    background: linear-gradient(135deg, #00c7b7 0%, #00a896 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
}

.deploy-badge-cloudflare {
    background: linear-gradient(135deg, #f38020 0%, #f6821f 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
}

.deploy-badge-edgeone {
    background: linear-gradient(135deg, #006eff 0%, #0052cc 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
}

.deploy-badge-docker {
    background: linear-gradient(135deg, #2496ed 0%, #1d7fc1 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
}

/* ========================================
   系统状态样式
   ======================================== */
.status-running {
    animation: pulse 2s ease-in-out infinite;
}

.status-warning {
    animation: pulse 2s ease-in-out infinite;
}

.status-error {
    animation: shake 0.5s ease-in-out;
}

/* ========================================
   模式徽章样式
   ======================================== */
.mode-preview .stat-icon-wrapper {
    background: linear-gradient(135deg, #94a3b8 0%, #64748b 100%);
}

.mode-user .stat-icon-wrapper {
    background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
}

.mode-admin .stat-icon-wrapper {
    background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
}

/* ========================================
   API测试组件
   ======================================== */
.api-test-container {
    display: flex;
    flex-direction: column;
    gap: 1.5rem;
}

.api-info-card {
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.1), rgba(139, 92, 246, 0.1));
    backdrop-filter: var(--blur-sm);
    border-radius: var(--radius-lg);
    padding: 1.5rem;
    border: 1px solid rgba(59, 130, 246, 0.3);
    margin-bottom: 1.5rem;
}

.api-info-header {
    display: flex;
    align-items: center;
    gap: 1rem;
    margin-bottom: 1rem;
}

.api-icon {
    font-size: 2rem;
}

.api-info-content {
    flex: 1;
}

.api-name {
    font-size: 1.25rem;
    font-weight: 600;
    color: var(--text-primary);
    margin-bottom: 0.25rem;
}

.api-description {
    font-size: 0.9375rem;
    color: var(--text-secondary);
    line-height: 1.6;
}

.api-info-details {
    display: flex;
    gap: 2rem;
    flex-wrap: wrap;
}

.api-detail-item {
    display: flex;
    align-items: center;
    gap: 0.75rem;
}

.detail-label {
    font-size: 0.875rem;
    color: var(--text-tertiary);
    font-weight: 500;
}

.method-badge {
    padding: 0.375rem 0.875rem;
    border-radius: var(--radius-sm);
    font-size: 0.8125rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.5px;
}

.method-get {
    background: var(--gradient-success);
    color: white;
}

.method-post {
    background: var(--gradient-primary);
    color: white;
}

.api-path {
    padding: 0.375rem 0.875rem;
    background: var(--bg-tertiary);
    backdrop-filter: var(--blur-sm);
    border-radius: var(--radius-sm);
    font-family: 'Courier New', monospace;
    font-size: 0.875rem;
    color: var(--text-primary);
    border: 1px solid var(--border-color);
}

.no-params-message {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 0.75rem;
    padding: 2rem;
    background: var(--bg-secondary);
    backdrop-filter: var(--blur-sm);
    border-radius: var(--radius-md);
    border: 2px dashed var(--border-color);
    color: var(--text-secondary);
}

.message-icon {
    font-size: 1.5rem;
}

.param-icon {
    font-size: 1rem;
}

.form-help .help-icon {
    color: var(--primary-color);
}

/* ========================================
   推送弹幕组件
   ======================================== */
.push-container {
    display: flex;
    flex-direction: column;
    gap: 1.5rem;
}

.push-presets-section,
.lan-scan-section {
    margin-top: 1rem;
    padding: 1rem;
    background: var(--bg-tertiary);
    backdrop-filter: var(--blur-sm);
    border-radius: var(--radius-md);
    border: 1px solid var(--border-color);
}

.presets-header,
.lan-scan-header {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    margin-bottom: 1rem;
    font-size: 0.875rem;
    font-weight: 600;
    color: var(--text-primary);
}

.presets-header svg,
.lan-scan-header svg {
    color: var(--primary-color);
}

.presets-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
    gap: 0.75rem;
}

.preset-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 0.5rem;
    padding: 0.75rem;
    white-space: nowrap;
}

.preset-btn svg {
    flex-shrink: 0;
}

.lan-scan-controls {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
}

.lan-input-group {
    display: flex;
    gap: 0.5rem;
    align-items: center;
}

.lan-subnet-input {
    flex: 1;
    min-width: 0;
}

.lan-input-separator {
    color: var(--text-tertiary);
    font-weight: 600;
    font-size: 1.25rem;
}

.lan-port-input {
    width: 100px;
}

.lan-scan-btn {
    white-space: nowrap;
}

.scan-btn-text {
    display: inline;
}

.lan-scan-progress {
    padding: 1.5rem;
    text-align: center;
}

.scan-progress-bar {
    width: 100%;
    height: 8px;
    background: var(--bg-tertiary);
    border-radius: 4px;
    overflow: hidden;
    margin-bottom: 1rem;
}

.scan-progress-fill {
    height: 100%;
    background: var(--gradient-primary);
    border-radius: 4px;
    transition: width 0.3s ease;
    width: 0;
}

.scan-progress-text {
    font-size: 0.875rem;
    color: var(--text-secondary);
}

.lan-devices-list {
    margin-top: 1rem;
}

.lan-devices-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 1rem;
}

.devices-count {
    font-size: 0.875rem;
    font-weight: 600;
    color: var(--text-primary);
}

.lan-devices-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 0.75rem;
}

.lan-device-card {
    display: flex;
    align-items: center;
    gap: 1rem;
    padding: 1rem;
    background: var(--bg-primary);
    backdrop-filter: var(--blur-sm);
    border: 2px solid var(--border-color);
    border-radius: var(--radius-md);
    cursor: pointer;
    transition: all var(--transition-fast);
}

.lan-device-card:hover {
    background: var(--bg-secondary);
    border-color: var(--primary-color);
    transform: translateY(-2px);
    box-shadow: var(--shadow-sm);
}

.device-icon {
    font-size: 1.5rem;
    flex-shrink: 0;
}

.device-info {
    flex: 1;
    min-width: 0;
}

.device-ip {
    font-family: 'Courier New', monospace;
    font-size: 0.875rem;
    font-weight: 600;
    color: var(--text-primary);
    margin-bottom: 0.25rem;
}

.device-type {
    font-size: 0.75rem;
    color: var(--text-secondary);
}

.device-select-icon {
    flex-shrink: 0;
    color: var(--text-tertiary);
}

.lan-scan-empty {
    text-align: center;
    padding: 2rem;
}

.lan-scan-empty .empty-icon {
    font-size: 3rem;
    margin-bottom: 1rem;
    opacity: 0.5;
}

.lan-scan-empty p {
    color: var(--text-primary);
    font-weight: 500;
    margin-bottom: 0.5rem;
}

.empty-hint {
    font-size: 0.8125rem;
    color: var(--text-tertiary);
}

/* 深色模式 - 推送弹幕卡片增强（快速预设 / 局域网扫描） */
[data-theme="dark"] .push-presets-section,
[data-theme="dark"] .lan-scan-section {
    background: linear-gradient(135deg, rgba(17, 24, 39, 0.75), rgba(31, 41, 55, 0.55));
    border: 1px solid rgba(129, 140, 248, 0.18);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35),
                0 0 0 1px rgba(129, 140, 248, 0.06) inset;
}

[data-theme="dark"] .preset-btn.btn-secondary {
    background: rgba(10, 15, 30, 0.55);
    border-color: rgba(129, 140, 248, 0.22);
}

[data-theme="dark"] .preset-btn.btn-secondary:hover:not(:disabled) {
    background: rgba(17, 24, 39, 0.75);
    border-color: rgba(129, 140, 248, 0.45);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.45),
                0 0 30px rgba(129, 140, 248, 0.12);
}

[data-theme="dark"] .scan-progress-bar {
    background: rgba(10, 15, 30, 0.55);
    border: 1px solid rgba(129, 140, 248, 0.12);
}

[data-theme="dark"] .lan-device-card {
    background: rgba(10, 15, 30, 0.65);
    border-color: rgba(129, 140, 248, 0.18);
}

[data-theme="dark"] .lan-device-card:hover {
    background: rgba(17, 24, 39, 0.85);
    border-color: rgba(129, 140, 248, 0.55);
    box-shadow: 0 12px 28px rgba(0, 0, 0, 0.55),
                0 0 40px rgba(129, 140, 248, 0.12);
}

[data-theme="dark"] .device-select-icon {
    color: rgba(226, 232, 240, 0.55);
}

.search-results-header {
    margin-bottom: 1.5rem;
}

.results-title {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    font-size: 1.25rem;
    font-weight: 600;
    color: var(--text-primary);
}

.title-icon {
    font-size: 1.5rem;
}

.results-count {
    padding: 0.25rem 0.75rem;
    background: var(--primary-color);
    color: white;
    border-radius: var(--radius-sm);
    font-size: 0.875rem;
    font-weight: 600;
}

.results-hint {
    margin-top: 0.5rem;
    font-size: 0.875rem;
    color: var(--text-secondary);
}

.anime-grid,
.anime-grid-container {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
    gap: 1.5rem;
}

.anime-card {
    background: var(--bg-primary);
    backdrop-filter: var(--blur-md);
    border-radius: var(--radius-lg);
    overflow: hidden;
    border: 1px solid var(--border-color);
    box-shadow: var(--shadow-sm);
    cursor: pointer;
    transition: all var(--transition-fast);
}

.anime-card:hover {
    transform: translateY(-4px);
    box-shadow: var(--shadow-md);
    border-color: var(--primary-color);
}

.anime-card-image-wrapper {
    position: relative;
    width: 100%;
    padding-top: 133.33%;
    overflow: hidden;
    background: var(--bg-tertiary);
}

.anime-image {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
}

.anime-card-overlay {
    position: absolute;
    inset: 0;
    background: linear-gradient(to top, rgba(0,0,0,0.8), transparent);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 0.5rem;
    opacity: 0;
    transition: opacity var(--transition-fast);
}

.anime-card:hover .anime-card-overlay {
    opacity: 1;
}

.view-icon {
    font-size: 2rem;
}

.view-text {
    font-weight: 600;
    color: white;
    font-size: 0.9375rem;
}

.anime-info {
    padding: 1rem;
}

.anime-title {
    font-size: 0.9375rem;
    font-weight: 600;
    color: var(--text-primary);
    margin-bottom: 0.5rem;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    line-height: 1.4;
}

.anime-meta {
    display: flex;
    align-items: center;
    gap: 0.5rem;
}

.episode-count {
    display: flex;
    align-items: center;
    gap: 0.375rem;
    font-size: 0.8125rem;
    color: var(--text-secondary);
}

.meta-icon {
    font-size: 1rem;
}

.episode-grid {
    display: flex;
    flex-direction: column;
    gap: 1rem;
    margin-top: 2rem;
}

.episode-list-header {
    background: var(--bg-primary);
    backdrop-filter: var(--blur-md);
    border-radius: var(--radius-lg);
    padding: 1.5rem;
    border: 1px solid var(--border-color);
    margin-bottom: 1rem;
}

.episode-anime-title {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    font-size: 1.5rem;
    font-weight: 700;
    color: var(--text-primary);
    margin-bottom: 1rem;
}

.episode-anime-icon {
    font-size: 1.75rem;
}

.episode-stats {
    display: flex;
    gap: 1.5rem;
    flex-wrap: wrap;
}

.episode-stat-item {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.5rem 1rem;
    background: var(--bg-secondary);
    backdrop-filter: var(--blur-sm);
    border-radius: var(--radius-md);
    font-size: 0.875rem;
    color: var(--text-secondary);
}

.episode-stat-icon {
    font-size: 1.125rem;
}

.episode-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 1rem;
    padding: 1.25rem;
    background: var(--bg-primary);
    backdrop-filter: var(--blur-md);
    border-radius: var(--radius-lg);
    border: 1px solid var(--border-color);
    box-shadow: var(--shadow-sm);
    transition: all var(--transition-fast);
}

.episode-item:hover {
    box-shadow: var(--shadow-md);
    transform: translateY(-2px);
    border-color: var(--primary-color);
}

.episode-info {
    flex: 1;
    min-width: 0;
    position: relative;
}

.episode-number {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    font-weight: 600;
    font-size: 1rem;
    color: var(--text-primary);
    margin-bottom: 0.375rem;
}

.episode-icon {
    font-size: 1.125rem;
    color: var(--primary-color);
}

.episode-title {
    font-size: 0.875rem;
    color: var(--text-secondary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.episode-push-btn {
    flex-shrink: 0;
}

.episode-push-btn.pushed {
    background: var(--success-color) !important;
    border-color: var(--success-color) !important;
    cursor: not-allowed;
}

.push-success-badge {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    padding: 0.25rem 0.625rem;
    background: rgba(16, 185, 129, 0.1);
    color: var(--success-color);
    border-radius: var(--radius-sm);
    font-size: 0.75rem;
    font-weight: 600;
    margin-top: 0.5rem;
    border: 1px solid var(--success-color);
}

.search-empty,
.search-error {
    text-align: center;
    padding: 3rem 2rem;
    background: var(--bg-primary);
    backdrop-filter: var(--blur-md);
    border-radius: var(--radius-lg);
    border: 1px solid var(--border-color);
}

.search-empty .empty-icon,
.search-error .error-icon {
    font-size: 4rem;
    margin-bottom: 1rem;
    opacity: 0.5;
}

.search-empty h3,
.search-error h3 {
    font-size: 1.25rem;
    font-weight: 600;
    color: var(--text-primary);
    margin-bottom: 0.5rem;
}

.search-empty p,
.search-error p {
    color: var(--text-secondary);
    font-size: 0.9375rem;
    margin-bottom: 1rem;
}

.loading-state {
    text-align: center;
    padding: 3rem 2rem;
    background: var(--bg-primary);
    backdrop-filter: var(--blur-md);
    border-radius: var(--radius-lg);
    border: 1px solid var(--border-color);
}
/* ========================================
   动漫搜索结果 - 移动端适配
   ======================================== */
@media (max-width: 767px) {
    .anime-grid,
    .anime-grid-container {
        grid-template-columns: repeat(3, 1fr) !important;
        gap: 0.75rem;
    }

    .anime-card {
        border-radius: var(--radius-md);
    }

    .anime-card-image-wrapper {
        padding-top: 140%;
    }

    .anime-info {
        padding: 0.5rem;
    }

    .anime-title {
        font-size: 0.75rem;
        margin-bottom: 0.25rem;
        /* 移动端：标题允许自动换行，避免省略号截断 */
        overflow: visible;
        text-overflow: clip;
        display: block;
        -webkit-line-clamp: unset;
        -webkit-box-orient: unset;
        white-space: normal;
        word-break: break-word;
        overflow-wrap: anywhere;
    }

    .anime-meta {
        gap: 0.25rem;
    }

    .episode-count {
        font-size: 0.6875rem;
    }

    .meta-icon {
        font-size: 0.75rem;
    }

    /* 搜索结果标题优化 */
    .search-results-header {
        margin-bottom: 1rem;
    }

    .results-title {
        font-size: 1rem;
        gap: 0.5rem;
    }

    .title-icon {
        font-size: 1.25rem;
    }

    .results-count {
        font-size: 0.75rem;
        padding: 0.125rem 0.5rem;
    }

    .results-hint {
        font-size: 0.75rem;
    }
}

@media (max-width: 479px) {
    .anime-grid,
    .anime-grid-container {
        grid-template-columns: repeat(3, 1fr) !important;
        gap: 0.5rem;
    }

    .anime-info {
        padding: 0.375rem;
    }

    .anime-title {
        font-size: 0.6875rem;
    }

    .episode-count {
        font-size: 0.625rem;
    }

    .meta-icon {
        display: none;
    }
}
/* ========================================
   API 模式切换标签
   ======================================== */
.api-mode-tabs {
    display: flex;
    gap: 1rem;
    flex-wrap: wrap;
}

.api-mode-tab {
    display: inline-flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.75rem 1.5rem;
    background: var(--bg-secondary);
    backdrop-filter: var(--blur-sm);
    border: 2px solid var(--border-color);
    border-radius: var(--radius-lg);
    color: var(--text-secondary);
    font-weight: 600;
    font-size: 0.9375rem;
    cursor: pointer;
    transition: all var(--transition-fast);
}

.api-mode-tab:hover {
    background: var(--bg-tertiary);
    border-color: var(--primary-color);
    color: var(--text-primary);
    transform: translateY(-2px);
    box-shadow: var(--shadow-sm);
}

.api-mode-tab.active {
    background: var(--gradient-primary);
    border-color: var(--primary-color);
    color: white;
    box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.api-mode-tab .btn-icon {
    width: 18px;
    height: 18px;
}

/* ========================================
   弹幕测试容器
   ======================================== */
.danmu-test-container {
    display: flex;
    flex-direction: column;
    gap: 1.5rem;
}

/* ========================================
   弹幕测试方式切换（避免两个输入框同时出现）
   ======================================== */
.danmu-method-switcher {
    border: 2px solid var(--border-color);
}

.danmu-method-switcher-header {
    margin-bottom: 1rem;
}

.danmu-method-tabs {
    display: flex;
    gap: 0.75rem;
    flex-wrap: wrap;
    margin-bottom: 1rem;
}

.danmu-method-tab {
    display: inline-flex;
    align-items: center;
    gap: 0.5rem;
    padding: 0.625rem 1.25rem;
    background: var(--bg-secondary);
    border: 2px solid var(--border-color);
    border-radius: var(--radius-lg);
    color: var(--text-secondary);
    font-weight: 700;
    font-size: 0.9375rem;
    cursor: pointer;
    transition: all var(--transition-fast);
}

.danmu-method-tab:hover {
    background: var(--bg-tertiary);
    border-color: var(--primary-color);
    color: var(--text-primary);
    transform: translateY(-2px);
    box-shadow: var(--shadow-sm);
}

.danmu-method-tab.active {
    background: var(--gradient-primary);
    border-color: var(--primary-color);
    color: #fff;
    box-shadow: 0 4px 12px rgba(59, 130, 246, 0.25);
}

.danmu-method-tab .tab-icon {
    font-size: 1.1rem;
    line-height: 1;
}

.danmu-method-empty {
    padding: 1.25rem;
    border: 1px dashed var(--border-color);
    border-radius: var(--radius-lg);
    background: var(--bg-secondary);
    text-align: center;
}

.danmu-method-empty .empty-icon {
    font-size: 2rem;
    margin-bottom: 0.5rem;
}

.danmu-method-empty .empty-title {
    font-weight: 800;
    color: var(--text-primary);
    margin-bottom: 0.25rem;
}

.danmu-method-empty .empty-desc {
    font-size: 0.875rem;
    color: var(--text-secondary);
}

.danmu-method-panel {
    margin-top: 0.75rem;
}

.danmu-test-methods {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
    gap: 1.5rem;
}

.danmu-method-card {
    border: 2px solid var(--border-color);
    transition: all var(--transition-fast);
}

.danmu-method-card:hover {
    border-color: var(--primary-color);
    transform: translateY(-4px);
    box-shadow: var(--shadow-lg);
}

.method-header {
    display: flex;
    align-items: center;
    gap: 1rem;
    margin-bottom: 1.5rem;
    padding-bottom: 1rem;
    border-bottom: 1px solid var(--border-color);
}

.method-icon-wrapper {
    width: 56px;
    height: 56px;
    border-radius: var(--radius-lg);
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    flex-shrink: 0;
}

.method-icon-wrapper svg {
    width: 28px;
    height: 28px;
    color: white;
}

.method-info {
    flex: 1;
}

.method-title {
    font-size: 1.125rem;
    font-weight: 600;
    color: var(--text-primary);
    margin-bottom: 0.25rem;
}

.method-desc {
    font-size: 0.875rem;
    color: var(--text-secondary);
}

/* ========================================
   弹幕信息卡片
   ======================================== */
.danmu-info-card {
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.05), rgba(139, 92, 246, 0.05));
    border: 1px solid rgba(59, 130, 246, 0.2);
}

.danmu-info-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 1rem;
    margin-bottom: 1.5rem;
    flex-wrap: wrap;
}

.danmu-title-section {
    flex: 1;
    min-width: 200px;
}

.danmu-title {
    font-size: 1.5rem;
    font-weight: 700;
    color: var(--text-primary);
    margin-bottom: 0.25rem;
}

.danmu-subtitle {
    font-size: 0.875rem;
    color: var(--text-secondary);
}

.danmu-actions {
    display: flex;
    gap: 0.75rem;
    flex-wrap: wrap;
}

.danmu-actions .btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
    transform: none !important;
    box-shadow: none !important;
}

.danmu-stats-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
    gap: 1rem;
}

.danmu-stat-item {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    padding: 1rem;
    background: var(--bg-primary);
    backdrop-filter: var(--blur-sm);
    border-radius: var(--radius-md);
    border: 1px solid var(--border-color);
    transition: all var(--transition-fast);
}

.danmu-stat-item:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-sm);
}

.danmu-stat-item .stat-icon {
    font-size: 1.75rem;
    flex-shrink: 0;
}

.danmu-stat-item .stat-content {
    flex: 1;
    min-width: 0;
}

.danmu-stat-item .stat-value {
    font-size: 1.5rem;
    font-weight: 700;
    color: var(--text-primary);
    line-height: 1.2;
}

.danmu-stat-item .stat-label {
    font-size: 0.75rem;
    color: var(--text-secondary);
    margin-top: 0.25rem;
}

/* ========================================
   弹幕热力图
   ======================================== */
.danmu-heatmap-card {
    position: relative;
    border: 1px solid rgba(139, 92, 246, 0.2);
}

.heatmap-legend {
    display: flex;
    align-items: center;
    gap: 1rem;
    margin-bottom: 1rem;
    padding: 0.75rem;
    background: var(--bg-secondary);
    border-radius: var(--radius-md);
}

.legend-label {
    font-size: 0.875rem;
    font-weight: 600;
    color: var(--text-secondary);
}

.legend-gradient {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    flex: 1;
}

.legend-low,
.legend-high {
    font-size: 0.75rem;
    color: var(--text-tertiary);
}

.legend-bar {
    flex: 1;
    height: 20px;
    border-radius: var(--radius-sm);
    background: linear-gradient(to right, 
        rgba(59, 130, 246, 0.2), 
        rgba(139, 92, 246, 0.4), 
        rgba(236, 72, 153, 0.6),
        rgba(239, 68, 68, 0.8));
    border: 1px solid var(--border-color);
}

#danmu-heatmap-canvas {
    width: 100%;
    height: 150px;
    border-radius: var(--radius-md);
    background: var(--bg-secondary);
    border: 1px solid var(--border-color);
    cursor: pointer;
}

.heatmap-node-info {
    margin-top: 0.75rem;
    padding: 0.75rem 1rem;
    background: var(--bg-secondary);
    border: 1px dashed var(--border-color);
    border-radius: var(--radius-md);
    font-size: 0.875rem;
    color: var(--text-secondary);
    line-height: 1.4;
}

.heatmap-node-info strong {
    color: var(--text-primary);
    font-weight: 700;
}

.heatmap-tooltip {
    position: absolute;
    z-index: 10;
    pointer-events: none;
    display: none;
    max-width: 260px;
    padding: 0.5rem 0.75rem;
    background: var(--bg-primary);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-md);
    box-shadow: var(--shadow-md);
    font-size: 0.75rem;
    color: var(--text-secondary);
    backdrop-filter: var(--blur-md);
}

.heatmap-tooltip.visible {
    display: block;
}

/* ========================================
   弹幕列表
   ======================================== */
.danmu-list-card {
    border: 1px solid rgba(16, 185, 129, 0.2);
}

.danmu-list-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 1rem;
    margin-bottom: 1rem;
    flex-wrap: wrap;
}

.danmu-list-filters {
    display: flex;
    gap: 0.5rem;
    flex-wrap: wrap;
}

.danmu-filter-btn {
    padding: 0.5rem 1rem;
    background: var(--bg-secondary);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-md);
    font-size: 0.875rem;
    font-weight: 500;
    color: var(--text-secondary);
    cursor: pointer;
    transition: all var(--transition-fast);
}

.danmu-filter-btn:hover {
    background: var(--bg-tertiary);
    border-color: var(--primary-color);
    color: var(--text-primary);
}

.danmu-filter-btn.active {
    background: var(--primary-color);
    border-color: var(--primary-color);
    color: white;
}

.danmu-list-container {
    max-height: 500px;
    overflow-y: auto;
    border-radius: var(--radius-md);
    background: var(--bg-secondary);
    padding: 1rem;
}

.danmu-list-empty {
    text-align: center;
    padding: 3rem;
    color: var(--text-tertiary);
}

.danmu-list-empty .empty-icon {
    font-size: 3rem;
    margin-bottom: 1rem;
    display: block;
}

.danmu-item {
    display: flex;
    align-items: flex-start;
    gap: 1rem;
    padding: 0.875rem;
    margin-bottom: 0.5rem;
    background: var(--bg-primary);
    border-radius: var(--radius-md);
    border-left: 3px solid var(--primary-color);
    transition: all var(--transition-fast);
    animation: fadeInUp 0.3s ease-out backwards;
}

.danmu-item:hover {
    background: var(--bg-tertiary);
    transform: translateX(4px);
    box-shadow: var(--shadow-sm);
}

.danmu-item-time {
    font-family: 'Courier New', monospace;
    font-size: 0.8125rem;
    font-weight: 600;
    color: var(--primary-color);
    min-width: 60px;
    padding: 0.25rem 0.625rem;
    background: rgba(59, 130, 246, 0.1);
    border-radius: var(--radius-sm);
    text-align: center;
}

.danmu-item-content {
    flex: 1;
    min-width: 0;
}

.danmu-item-text {
    color: var(--text-primary);
    font-size: 0.9375rem;
    line-height: 1.6;
    word-break: break-word;
}

.danmu-item-meta {
    display: flex;
    gap: 0.75rem;
    margin-top: 0.5rem;
    font-size: 0.75rem;
    color: var(--text-tertiary);
}

.danmu-item-type {
    padding: 0.125rem 0.5rem;
    background: var(--bg-tertiary);
    border-radius: var(--radius-sm);
    font-weight: 600;
}

.danmu-item.type-top {
    border-left-color: var(--success-color);
}

.danmu-item.type-top .danmu-item-type {
    background: rgba(16, 185, 129, 0.1);
    color: var(--success-color);
}

.danmu-item.type-bottom {
    border-left-color: var(--warning-color);
}

.danmu-item.type-bottom .danmu-item-type {
    background: rgba(245, 158, 11, 0.1);
    color: var(--warning-color);
}

/* ========================================
   深色模式适配
   ======================================== */
[data-theme="dark"] .api-mode-tab {
    background: rgba(17, 24, 39, 0.8);
    border: 2px solid rgba(99, 102, 241, 0.2);
}

[data-theme="dark"] .api-mode-tab:hover {
    border-color: rgba(129, 140, 248, 0.4);
    box-shadow: 0 0 20px rgba(129, 140, 248, 0.15);
}

[data-theme="dark"] .api-mode-tab.active {
    background: linear-gradient(135deg, #818cf8 0%, #a78bfa 100%);
    box-shadow: 0 4px 12px rgba(129, 140, 248, 0.3),
                0 0 40px rgba(167, 139, 250, 0.2);
}

[data-theme="dark"] .danmu-info-card {
    background: linear-gradient(135deg, rgba(129, 140, 248, 0.08), rgba(167, 139, 250, 0.08));
    border: 1px solid rgba(129, 140, 248, 0.25);
}

[data-theme="dark"] #danmu-heatmap-canvas {
    background: rgba(17, 24, 39, 0.6);
}

/* ========================================
   响应式适配
   ======================================== */
@media (max-width: 767px) {
    .api-mode-tabs {
        width: 100%;
    }

    .api-mode-tab {
        flex: 1;
        justify-content: center;
        padding: 0.625rem 1rem;
        font-size: 0.875rem;
    }

    .api-mode-tab .btn-icon {
        width: 16px;
        height: 16px;
    }

    .danmu-method-tabs {
        width: 100%;
    }

    .danmu-method-tab {
        flex: 1;
        justify-content: center;
        padding: 0.625rem 0.875rem;
        font-size: 0.875rem;
    }

    .danmu-info-header {
        flex-direction: column;
        align-items: flex-start;
    }

    .danmu-actions {
        width: 100%;
    }

    .danmu-actions .btn {
        flex: 1;
    }

    .danmu-stats-grid {
        grid-template-columns: repeat(2, 1fr);
    }

    .danmu-list-header {
        flex-direction: column;
        align-items: flex-start;
    }

    .danmu-list-filters {
        width: 100%;
    }

    .danmu-filter-btn {
        flex: 1;
        text-align: center;
        font-size: 0.75rem;
        padding: 0.375rem 0.5rem;
    }

    .danmu-item {
        flex-direction: column;
        gap: 0.5rem;
    }

    .danmu-item-time {
        width: fit-content;
    }
}
/* ========================================
   弹幕分页优化 - 加载更多样式
   ======================================== */
.load-more-btn {
    padding: 1rem;
    text-align: center;
}

.load-more-btn .btn {
    width: 100%;
    max-width: 300px;
}

.danmu-list-end {
    padding: 1.5rem;
    text-align: center;
    color: var(--text-tertiary);
    font-size: 0.875rem;
}

/* ========================================
   部署平台环境变量状态 - 顶栏指示器 & 模态框
   ======================================== */
.mobile-status-indicator[data-deploy-ok="0"] {
    border-color: rgba(239, 68, 68, 0.35);
    box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.06);
}

.mobile-status-indicator[data-deploy-ok="1"] {
    border-color: rgba(34, 197, 94, 0.35);
    box-shadow: 0 0 0 4px rgba(34, 197, 94, 0.06);
}

[data-theme="dark"] .mobile-status-indicator[data-deploy-ok="0"] {
    box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.08);
}

[data-theme="dark"] .mobile-status-indicator[data-deploy-ok="1"] {
    box-shadow: 0 0 0 4px rgba(34, 197, 94, 0.08);
}

.deploy-env-status-modal .modal-body {
    padding-top: 0.75rem;
}

.deploy-env-status-hero {
    position: relative;
    padding: 1.25rem 1.25rem 1.1rem;
    border-radius: var(--radius-lg);
    background: var(--bg-secondary);
    border: 1px solid var(--border-color);
    overflow: hidden;
    margin-bottom: 1rem;
}

.deploy-env-status-hero::before {
    content: '';
    position: absolute;
    inset: 0;
    background: var(--gradient-primary);
    opacity: 0.08;
    pointer-events: none;
}

[data-theme="dark"] .deploy-env-status-hero::before {
    opacity: 0.12;
}

.deploy-env-status-hero.success::before {
    background: radial-gradient(circle at 30% 20%, rgba(34, 197, 94, 0.35), transparent 55%),
                radial-gradient(circle at 80% 70%, rgba(129, 140, 248, 0.25), transparent 55%);
    opacity: 0.9;
}

.deploy-env-status-hero.error::before {
    background: radial-gradient(circle at 30% 20%, rgba(239, 68, 68, 0.35), transparent 55%),
                radial-gradient(circle at 80% 70%, rgba(192, 132, 252, 0.25), transparent 55%);
    opacity: 0.9;
}

.deploy-env-status-hero-content {
    position: relative;
    display: flex;
    align-items: flex-start;
    gap: 0.9rem;
}

.deploy-env-status-hero-icon {
    width: 44px;
    height: 44px;
    border-radius: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--bg-primary);
    border: 1px solid var(--border-color);
    box-shadow: var(--shadow-sm);
    flex: 0 0 auto;
}

.deploy-env-status-hero-icon svg {
    width: 22px;
    height: 22px;
}

.deploy-env-status-hero-title {
    font-size: 1.05rem;
    font-weight: 700;
    color: var(--text-primary);
    margin: 0;
}

.deploy-env-status-hero-subtitle {
    margin-top: 0.35rem;
    font-size: 0.9rem;
    color: var(--text-secondary);
    line-height: 1.5;
}

.deploy-env-status-chip {
    display: inline-flex;
    align-items: center;
    gap: 0.4rem;
    padding: 0.3rem 0.6rem;
    border-radius: 999px;
    border: 1px solid var(--border-color);
    background: var(--bg-primary);
    font-size: 0.8rem;
    color: var(--text-secondary);
    margin-top: 0.55rem;
    width: fit-content;
}

.deploy-env-status-grid {
    display: grid;
    grid-template-columns: 1fr;
    gap: 0.75rem;
    margin-top: 0.75rem;
}

.deploy-env-var-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0.9rem 1rem;
    border-radius: var(--radius-lg);
    border: 1px solid var(--border-color);
    background: var(--bg-secondary);
    transition: transform var(--transition-fast), box-shadow var(--transition-fast);
}

.deploy-env-var-item:hover {
    transform: translateY(-1px);
    box-shadow: var(--shadow-sm);
}

.deploy-env-var-name {
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
    font-size: 0.85rem;
    color: var(--text-primary);
}

.deploy-env-var-status {
    display: inline-flex;
    align-items: center;
    gap: 0.4rem;
    padding: 0.28rem 0.6rem;
    border-radius: 999px;
    font-size: 0.8rem;
    border: 1px solid transparent;
    font-weight: 600;
}

.deploy-env-var-status.ok {
    color: var(--success-color);
    border-color: rgba(34, 197, 94, 0.25);
    background: rgba(34, 197, 94, 0.08);
}

.deploy-env-var-status.missing {
    color: var(--danger-color);
    border-color: rgba(239, 68, 68, 0.25);
    background: rgba(239, 68, 68, 0.08);
}

[data-theme="dark"] .deploy-env-var-status.ok {
    border-color: rgba(34, 197, 94, 0.35);
    background: rgba(34, 197, 94, 0.12);
}

[data-theme="dark"] .deploy-env-var-status.missing {
    border-color: rgba(239, 68, 68, 0.35);
    background: rgba(239, 68, 68, 0.12);
}

.deploy-env-code {
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
    font-size: 0.82rem;
    padding: 0.15rem 0.45rem;
    border-radius: 10px;
    border: 1px solid var(--border-color);
    background: var(--bg-primary);
    color: var(--text-primary);
    white-space: nowrap;
}

.deploy-env-status-hint {
    margin-top: 0.9rem;
    padding: 0.9rem 1rem;
    border-radius: var(--radius-lg);
    border: 1px dashed var(--border-color);
    background: var(--bg-tertiary);
    color: var(--text-secondary);
    font-size: 0.875rem;
    line-height: 1.6;
}

`;