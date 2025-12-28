// language=CSS
export const responsiveCssContent = /* css */ `
/* ========================================
   响应式断点定义
   ======================================== */
/* 
   断点策略：
   - Mobile: < 768px
   - Tablet: 768px - 1024px
   - Desktop: > 1024px
   - Large Desktop: > 1440px
*/

/* ========================================
   移动端适配 (< 768px)
   ======================================== */
@media (max-width: 767px) {
    /* 全局防止溢出 */
    html, body {
        max-width: 100vw;
        overflow-x: hidden;
    }
    
    * {
        max-width: 100%;
    }
    
    .app-container,
    .main-content,
    .sidebar,
    .content-section {
        max-width: 100vw;
        overflow-x: hidden;
    }
    /* 主布局 */
    .main-content {
        margin-left: 0;
        padding: 0 0.75rem 0.75rem;
        max-width: 100vw;
        overflow-x: hidden;
    }

    /* 侧边栏 */
    .sidebar {
        transform: translateX(-100%);
    }

    .sidebar.active {
        transform: translateX(0);
        box-shadow: 4px 0 24px rgba(0, 0, 0, 0.2);
    }

    .sidebar-toggle {
        display: block;
    }

    /* 侧边栏遮罩 */
    .sidebar-overlay {
        animation: overlayFadeIn 0.3s ease-out;
    }

    /* 移动端头部 - 重新设计 */
    .mobile-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 1.25rem 0.75rem;
        background: rgba(255, 255, 255, 1);
        backdrop-filter: none;
        border-radius: 0;
        margin-left: -0.75rem;
        margin-right: -0.75rem;
        margin-bottom: 1.25rem;
        width: calc(100% + 1.5rem);
        max-width: none;
        border-bottom: 1px solid var(--border-color);
        box-shadow: 0 6px 24px rgba(0, 0, 0, 0.06);
        position: sticky;
        top: 0;
        z-index: 100;
        animation: slideInDown 0.4s ease-out;
    }

    /* 左侧区域 */
    .mobile-header-left {
        display: flex;
        align-items: center;
        gap: 1rem;
        flex: 1;
        min-width: 0;
    }

    /* 菜单按钮 */
    .mobile-menu-btn {
        display: flex;
        flex-direction: column;
        justify-content: center;
        gap: 5px;
        width: 44px;
        height: 44px;
        background: var(--bg-secondary);
        border: 1px solid var(--border-color);
        border-radius: var(--radius-md);
        padding: 0;
        cursor: pointer;
        transition: all var(--transition-fast);
        position: relative;
        overflow: hidden;
        flex-shrink: 0;
    }

    .mobile-menu-btn::before {
        content: '';
        position: absolute;
        inset: 0;
        background: var(--gradient-primary);
        opacity: 0;
        transition: opacity var(--transition-fast);
    }

    .mobile-menu-btn:hover::before {
        opacity: 0.1;
    }

    .mobile-menu-btn:active {
        transform: scale(0.95);
    }

    .menu-line {
        display: block;
        width: 20px;
        height: 2.5px;
        background: var(--text-primary);
        border-radius: 2px;
        margin: 0 auto;
        transition: all 0.3s cubic-bezier(0.68, -0.55, 0.265, 1.55);
        position: relative;
    }

    .sidebar.active ~ .main-content .mobile-menu-btn .menu-line:nth-child(1) {
        transform: translateY(7.5px) rotate(45deg);
    }

    .sidebar.active ~ .main-content .mobile-menu-btn .menu-line:nth-child(2) {
        opacity: 0;
        transform: translateX(-20px);
    }

    .sidebar.active ~ .main-content .mobile-menu-btn .menu-line:nth-child(3) {
        transform: translateY(-7.5px) rotate(-45deg);
    }

    /* Logo区域 */
    .mobile-logo-wrapper {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        flex: 1;
        min-width: 0;
    }

    .mobile-logo-image {
        width: 36px;
        height: 36px;
        border-radius: var(--radius-md);
        object-fit: cover;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
        flex-shrink: 0;
    }

    .mobile-title-group {
        display: flex;
        flex-direction: column;
        gap: 0.125rem;
        flex: 1;
        min-width: 0;
    }

    .mobile-title {
        font-size: 1rem;
        font-weight: 700;
        color: var(--text-primary);
        line-height: 1.2;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        background: var(--gradient-primary);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
        background-clip: text;
    }

    .mobile-subtitle {
        font-size: 0.6875rem;
        font-weight: 500;
        color: var(--text-tertiary);
        text-transform: uppercase;
        letter-spacing: 0.5px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    /* 右侧区域 */
    .mobile-header-right {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        flex-shrink: 0;
    }

    /* 操作按钮 */
    .mobile-action-btn {
        width: 40px;
        height: 40px;
        background: var(--bg-secondary);
        border: 1px solid var(--border-color);
        border-radius: var(--radius-md);
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        transition: all var(--transition-fast);
        position: relative;
        overflow: hidden;
    }

    .mobile-action-btn::before {
        content: '';
        position: absolute;
        inset: 0;
        background: var(--gradient-primary);
        opacity: 0;
        transition: opacity var(--transition-fast);
    }

    .mobile-action-btn:hover::before {
        opacity: 0.1;
    }

    .mobile-action-btn:active {
        transform: scale(0.9) rotate(15deg);
    }

    .mobile-action-icon {
        width: 20px;
        height: 20px;
        color: var(--text-primary);
        transition: all var(--transition-fast);
        position: relative;
        z-index: 1;
    }

    .mobile-action-btn .theme-icon-sun {
        display: block;
    }

    .mobile-action-btn .theme-icon-moon {
        display: none;
    }

    [data-theme="dark"] .mobile-action-btn .theme-icon-sun {
        display: none;
    }

    [data-theme="dark"] .mobile-action-btn .theme-icon-moon {
        display: block;
    }

    /* 状态指示器 */
    .mobile-status-indicator {
        width: 40px;
        height: 40px;
        background: var(--bg-secondary);
        border: 1px solid var(--border-color);
        border-radius: var(--radius-md);
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        transition: all var(--transition-fast);
    }

    .mobile-status-indicator:active {
        transform: scale(0.95);
    }

    .status-dot {
        width: 10px;
        height: 10px;
        border-radius: 50%;
        position: relative;
    }

    .status-dot::before {
        content: '';
        position: absolute;
        inset: -4px;
        border-radius: 50%;
        background: inherit;
        opacity: 0.3;
        animation: pulse 2s ease-in-out infinite;
    }

    .status-dot.status-running {
        background: var(--success-color);
        box-shadow: 0 0 8px var(--success-color);
    }

    .status-dot.status-warning {
        background: var(--warning-color);
        box-shadow: 0 0 8px var(--warning-color);
    }

    .status-dot.status-error {
        background: var(--danger-color);
        box-shadow: 0 0 8px var(--danger-color);
    }

    /* 区块标题 */
    .section-header {
        flex-direction: column;
        align-items: flex-start !important;
        gap: 1rem;
    }

    .section-title {
        font-size: 1.5rem;
    }

    .section-desc {
        font-size: 0.875rem;
    }

    .header-actions {
        width: 100%;
        flex-wrap: wrap;
    }

    .header-actions .btn {
        flex: 1;
        min-width: calc(50% - 0.375rem);
        justify-content: center;
    }

    /* 表单 */
    .form-card {
        padding: 1rem;
    }

    .input-group {
        flex-direction: column;
        width: 100%;
        box-sizing: border-box;
    }

    .input-group .btn {
        width: 100%;
        box-sizing: border-box;
    }
    
    .form-input,
    .form-select,
    .form-textarea {
        max-width: 100%;
        box-sizing: border-box;
    }

    /* 修复移动端键盘/viewport变化导致的元素偶发“消失”问题（Chrome/部分WebView） */
    .section-header,
    .header-actions,
    .danmu-method-panel,
    .danmu-method-switcher,
    .input-group {
        transform: translateZ(0);
        backface-visibility: hidden;
    }

    /* 让浏览器自动滚动聚焦输入框时，预留底部空间，避免把“搜索/匹配”按钮挤出视口 */
    .form-input,
    .form-select,
    .form-textarea {
        scroll-margin-bottom: 240px;
    }

    /* 深色模式下输入框 focus 的 transform 在移动端可能触发重绘异常 */
    [data-theme="dark"] .form-input:focus,
    [data-theme="dark"] .form-select:focus,
    [data-theme="dark"] .form-textarea:focus {
        transform: none;
    }

/* 搜索输入组在移动端保持横向布局 */
    .input-group.search-input-group {
        flex-direction: row;
    }

    .input-group.search-input-group .search-input {
        flex: 1;
        min-width: 0;
    }

    .input-group.search-input-group .search-btn {
        width: auto;
        flex-shrink: 0;
        padding: 0.75rem 1rem;
    }
    
    .search-btn-text {
        display: inline;
    }
    
    /* 模态框按钮在移动端保持横向 */
    .modal-footer-compact {
        flex-direction: row;
        gap: 0.625rem;
        padding: 1rem;
    }
    
    .modal-footer-compact .btn-modal {
        font-size: 0.875rem;
        padding: 0.625rem 1rem;
    }
    /* 按钮 */
    .btn {
        padding: 0.625rem 1rem;
        font-size: 0.875rem;
    }

    .btn-sm {
        padding: 0.5rem 0.875rem;
        font-size: 0.8125rem;
    }

    .btn-lg {
        padding: 0.75rem 1.25rem;
        font-size: 1rem;
    }

    /* 模态框 */
    .modal-container {
        margin: 1rem;
        max-height: calc(100vh - 2rem);
    }

    .modal-header {
        padding: 1rem;
    }

    .modal-body {
        padding: 1rem;
    }

    .modal-footer {
        padding: 1rem;
        flex-direction: column;
    }

    .modal-footer .btn {
        width: 100%;
    }

    /* 页脚 */
    .footer {
        padding: 1.5rem 1rem;
        margin-top: 3rem;
    }

    .footer-links {
        flex-direction: column;
        gap: 0.75rem;
    }

    .footer-link {
        justify-content: center;
    }

    /* 主题切换按钮 */
    .theme-toggle {
        bottom: 1rem;
        right: 1rem;
        width: 48px;
        height: 48px;
    }

    .theme-icon {
        width: 20px;
        height: 20px;
    }

    /* 版本卡片 */
    .version-card {
        margin: 0.75rem;
        padding: 0.875rem;
    }

    .version-item {
        font-size: 0.8125rem;
    }

    .version-update-notice {
        flex-direction: column;
        text-align: center;
    }

    .update-btn {
        width: 100%;
    }

    /* API端点卡片 */
    .endpoint-value {
        font-size: 0.75rem;
    }

    /* 多选标签 */
    .selected-tags,
    .available-tags {
        padding: 0.75rem;
    }

    .selected-tag,
    .available-tag,
    .tag-option {
        font-size: 0.8125rem;
        padding: 0.375rem 0.75rem;
    }

    /* 数字选择器 */
    .number-display {
        font-size: 1.5rem;
    }

    /* 颜色池 */
    .color-pool-controls {
        flex-direction: column;
    }

    .color-pool-controls .btn {
        width: 100%;
    }

    .color-input-wrapper {
        flex-direction: column;
        width: 100%;
    }

    .color-hex-input-wrapper {
        max-width: 100%;
    }

    .color-add-btn {
        width: 100%;
        justify-content: center;
    }

    .color-chip {
        width: 44px;
        height: 44px;
    }

    .pool-stats {
        flex-direction: row;
        align-items: center;
        flex-wrap: nowrap;
    }

    /* 加载状态 */
    .loading-content {
        padding: 2rem;
        margin: 1rem;
    }

    .loading-spinner {
        width: 48px;
        height: 48px;
    }

    .loading-title {
        font-size: 1.125rem;
    }

    /* 卡片 */
    .card {
        padding: 1rem;
        max-width: 100%;
        box-sizing: border-box;
    }
    
    .form-card,
    .preview-hero-card,
    .preview-category,
    .env-item,
    .log-terminal {
        max-width: 100%;
        box-sizing: border-box;
    }
    /* 成功动画 */
    .success-icon {
        font-size: 4rem;
    }

    .success-message {
        font-size: 1.25rem;
    }


    /* 弹幕测试/推送页面：搜索结果卡片标题在移动端允许自动换行，避免被截断 */
    .anime-title {
        overflow: visible;
        text-overflow: clip;
        display: block;
        -webkit-line-clamp: initial;
        -webkit-box-orient: initial;
        white-space: normal;
        word-break: break-word;
        overflow-wrap: anywhere;
    }

}

/* ========================================
   小屏手机优化 (< 480px)
   ======================================== */
@media (max-width: 479px) {
    html {
        font-size: 14px;
    }

    .main-content {
        padding: 0 0.75rem 0.75rem;
    }

    .mobile-header {
        padding: 1.15rem 0.75rem;
        margin-left: -0.75rem;
        margin-right: -0.75rem;
        margin-bottom: 1rem;
        width: calc(100% + 1.5rem);
        max-width: none;
        border-radius: 0;
    }

    .mobile-header-left {
        gap: 0.75rem;
    }

    .mobile-menu-btn {
        width: 40px;
        height: 40px;
    }

    .menu-line {
        width: 18px;
    }

    .mobile-logo-image {
        width: 32px;
        height: 32px;
    }

    .mobile-logo-wrapper {
        gap: 0.625rem;
    }

    .mobile-title {
        font-size: 0.9375rem;
    }

    .mobile-subtitle {
        font-size: 0.625rem;
    }

    .mobile-header-right {
        gap: 0.625rem;
    }

    .mobile-action-btn,
    .mobile-status-indicator {
        width: 36px;
        height: 36px;
    }

    .mobile-action-icon {
        width: 18px;
        height: 18px;
    }

    .status-dot {
        width: 8px;
        height: 8px;
    }

    .section-title {
        font-size: 1.25rem;
    }

    .btn {
        padding: 0.5rem 0.875rem;
        font-size: 0.8125rem;
    }

    .form-input,
    .form-select,
    .form-textarea {
        padding: 0.625rem 0.875rem;
        font-size: 0.875rem;
    }

    .color-chip {
        width: 36px;
        height: 36px;
    }

    .number-btn {
        width: 28px;
        height: 28px;
    }

    .theme-toggle {
        width: 44px;
        height: 44px;
    }
    .search-input-group .search-btn {
        padding: 0.75rem 0.875rem;
    }
    
    .search-btn-text {
        font-size: 0.8125rem;
    }
    
    .modal-footer-compact .btn-modal {
        font-size: 0.8125rem;
        padding: 0.625rem 0.875rem;
        gap: 0.375rem;
    }
    
    .modal-footer-compact .btn-modal .btn-icon {
        width: 16px;
        height: 16px;
    }
}

/* ========================================
   平板适配 (768px - 1024px)
   ======================================== */
@media (min-width: 768px) and (max-width: 1024px) {
    /* 隐藏移动端头部 */
    .mobile-header {
        display: none;
    }

    /* 主布局微调 */
    .main-content {
        padding: 1.5rem;
    }

    /* 侧边栏 */
    .sidebar {
        width: 260px;
    }

    .main-content {
        margin-left: 260px;
    }

    /* 按钮组 */
    .header-actions .btn {
        padding: 0.625rem 1rem;
        font-size: 0.875rem;
    }

    /* 网格布局 */
    .two-col-grid {
        grid-template-columns: 1fr;
    }

    .three-col-grid {
        grid-template-columns: repeat(2, 1fr);
    }

    /* 表单 */
    .input-group .form-input {
        min-width: 200px;
    }

    /* 颜色池 */
    .color-chip {
        width: 48px;
        height: 48px;
    }

    /* 页脚 */
    .footer {
        padding: 1.75rem;
    }
}

/* ========================================
   桌面端适配 (> 1024px)
   ======================================== */
@media (min-width: 1025px) {
    /* 隐藏移动端头部 */
    .mobile-header {
        display: none;
    }

    /* 内容宽度限制 */
    .main-content {
        max-width: 1400px;
    }

    /* 悬浮效果增强 */
    .card:hover {
        transform: translateY(-4px);
    }

    /* 按钮悬浮效果 */
    .btn:hover:not(:disabled) {
        transform: translateY(-2px);
    }

    /* 标签悬浮效果 */
    .tag-option:hover {
        transform: translateY(-3px);
    }

    /* 颜色块悬浮效果 */
    .color-chip:hover {
        transform: scale(1.15);
    }
}

/* ========================================
   大屏幕优化 (> 1440px)
   ======================================== */
@media (min-width: 1441px) {
    html {
        font-size: 17px;
    }

    .sidebar {
        width: 300px;
    }

    .main-content {
        margin-left: 300px;
        padding: 2.5rem;
    }

    .section-title {
        font-size: 2rem;
    }

    /* 网格优化 */
    .four-col-grid {
        grid-template-columns: repeat(4, 1fr);
    }

    /* 更大的卡片间距 */
    .card {
        padding: 2rem;
    }

    /* 页脚 */
    .footer {
        padding: 2.5rem;
    }
}

/* ========================================
   触摸设备优化
   ======================================== */
@media (hover: none) and (pointer: coarse) {
    /* 增大可点击区域 */
    .btn {
        min-height: 44px;
    }

    .nav-item {
        min-height: 48px;
    }

    .tag-option,
    .available-tag,
    .selected-tag {
        min-height: 40px;
    }

    .number-btn {
        min-width: 44px;
        min-height: 44px;
    }

    /* 移除悬浮效果 */
    .card:hover {
        transform: none;
    }

    .btn:hover {
        transform: none;
    }

    /* 增强点击反馈 */
    .btn:active {
        transform: scale(0.97);
    }

    .nav-item:active {
        transform: scale(0.98);
    }

    .tag-option:active,
    .available-tag:active {
        transform: scale(0.95);
    }
}

/* ========================================
   打印样式
   ======================================== */
@media print {
    /* 隐藏不需要打印的元素 */
    .sidebar,
    .theme-toggle,
    .mobile-header,
    .header-actions,
    .btn,
    .nav-menu,
    .footer-links {
        display: none !important;
    }

    /* 重置布局 */
    .main-content {
        margin-left: 0;
        padding: 0;
    }

    /* 移除背景和边框 */
    body,
    .card,
    .form-card {
        background: white !important;
        box-shadow: none !important;
        border: none !important;
    }

    /* 确保文字清晰 */
    body {
        color: black !important;
    }

    /* 分页优化 */
    .card,
    .form-card {
        page-break-inside: avoid;
    }
}

/* ========================================
   横屏模式优化
   ======================================== */
@media (max-width: 767px) and (orientation: landscape) {
    /* 减小垂直间距 */
    .main-content {
        padding: 0 0.75rem 0.75rem;
    }

    .mobile-header {
        padding: 1.05rem 0.75rem;
        margin-left: -0.75rem;
        margin-right: -0.75rem;
        margin-bottom: 0.75rem;
        width: calc(100% + 1.5rem);
        max-width: none;
    }

    .section-header {
        margin-bottom: 1rem;
    }

    /* 紧凑按钮 */
    .btn {
        padding: 0.5rem 0.875rem;
    }

    /* 模态框高度优化 */
    .modal-container {
        max-height: 90vh;
    }

    /* 侧边栏宽度 */
    .sidebar {
        width: 260px;
    }
}

/* ========================================
   减少动画 (用户偏好)
   ======================================== */
@media (prefers-reduced-motion: reduce) {
    *,
    *::before,
    *::after {
        animation-duration: 0.01ms !important;
        animation-iteration-count: 1 !important;
        transition-duration: 0.01ms !important;
        scroll-behavior: auto !important;
    }

    .loading-spinner {
        animation: none;
        border-top-color: var(--primary-color);
    }
}

/* ========================================
   高对比度模式
   ======================================== */
@media (prefers-contrast: high) {
    :root {
        --border-color: rgba(0, 0, 0, 0.3);
        --shadow-sm: 0 2px 8px rgba(0, 0, 0, 0.2);
        --shadow-md: 0 4px 16px rgba(0, 0, 0, 0.3);
        --shadow-lg: 0 8px 32px rgba(0, 0, 0, 0.4);
    }

    [data-theme="dark"] {
        --border-color: rgba(255, 255, 255, 0.3);
    }

    /* 增强边框 */
    .card,
    .form-input,
    .form-select,
    .btn,
    .sidebar {
        border-width: 2px;
    }

    /* 减少透明度 */
    .backdrop-blur {
        backdrop-filter: none;
    }
}

/* ========================================
   深色模式用户偏好
   ======================================== */
@media (prefers-color-scheme: dark) {
    /* 如果用户偏好深色，但未手动设置主题，则自动应用深色 */
    :root:not([data-theme]) {
        --bg-primary: rgba(15, 23, 42, 0.95);
        --bg-secondary: rgba(30, 41, 59, 0.9);
        --bg-tertiary: rgba(51, 65, 85, 0.85);
        --text-primary: #f1f5f9;
        --text-secondary: #cbd5e1;
        --text-tertiary: #94a3b8;
        --border-color: rgba(71, 85, 105, 0.6);
        --shadow-sm: 0 2px 8px rgba(0, 0, 0, 0.3);
        --shadow-md: 0 4px 16px rgba(0, 0, 0, 0.4);
        --shadow-lg: 0 8px 32px rgba(0, 0, 0, 0.5);
    }
}
/* ========================================
   深色模式 - 移动端顶栏增强
   ======================================== */
@media (max-width: 767px) {
    [data-theme="dark"] .mobile-header {
        background: rgba(10, 15, 30, 1);
        backdrop-filter: none;
        border: none;
        border-bottom: 1px solid var(--border-color);
        box-shadow: 0 4px 24px rgba(0, 0, 0, 0.55);
    }

    [data-theme="dark"] .mobile-header::before,
    [data-theme="dark"] .mobile-header::after {
        content: none;
    }

    @keyframes headerShine {
        0% { left: -100%; }
        50%, 100% { left: 100%; }
    }

    @keyframes headerGlow {
        0%, 100% {
            opacity: 0.5;
            transform: scaleX(1);
        }
        50% {
            opacity: 1;
            transform: scaleX(1.05);
        }
    }

    [data-theme="dark"] .mobile-menu-btn,
    [data-theme="dark"] .mobile-action-btn,
    [data-theme="dark"] .mobile-status-indicator {
        background: rgba(17, 24, 39, 0.75);
        backdrop-filter: blur(12px);
        border: 1px solid rgba(99, 102, 241, 0.25);
        box-shadow: 
            inset 0 1px 0 rgba(255, 255, 255, 0.08),
            0 4px 12px rgba(0, 0, 0, 0.3);
        transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
    }

    [data-theme="dark"] .mobile-menu-btn:hover,
    [data-theme="dark"] .mobile-action-btn:hover,
    [data-theme="dark"] .mobile-status-indicator:hover {
        background: rgba(17, 24, 39, 0.9);
        border-color: rgba(129, 140, 248, 0.5);
        box-shadow: 
            0 0 30px rgba(129, 140, 248, 0.25),
            0 0 60px rgba(167, 139, 250, 0.15),
            inset 0 1px 0 rgba(255, 255, 255, 0.12),
            0 4px 16px rgba(0, 0, 0, 0.4);
        transform: scale(1.05);
    }

    [data-theme="dark"] .mobile-menu-btn:active,
    [data-theme="dark"] .mobile-action-btn:active,
    [data-theme="dark"] .mobile-status-indicator:active {
        transform: scale(0.95);
        box-shadow: 
            0 0 15px rgba(129, 140, 248, 0.3),
            inset 0 2px 4px rgba(0, 0, 0, 0.3);
    }

    [data-theme="dark"] .mobile-logo-image {
        box-shadow: 
            0 4px 16px rgba(0, 0, 0, 0.5),
            0 0 30px rgba(99, 102, 241, 0.3),
            0 0 60px rgba(129, 140, 248, 0.15);
        border: 1px solid rgba(129, 140, 248, 0.2);
    }

    [data-theme="dark"] .status-dot.status-running {
        box-shadow: 
            0 0 16px var(--success-color),
            0 0 32px rgba(52, 211, 153, 0.4),
            0 0 48px rgba(16, 185, 129, 0.2);
        animation: statusPulse 2s ease-in-out infinite;
    }

    [data-theme="dark"] .status-dot.status-warning {
        box-shadow: 
            0 0 16px var(--warning-color),
            0 0 32px rgba(251, 191, 36, 0.4),
            0 0 48px rgba(245, 158, 11, 0.2);
        animation: statusPulse 2s ease-in-out infinite;
    }

    [data-theme="dark"] .status-dot.status-error {
        box-shadow: 
            0 0 16px var(--danger-color),
            0 0 32px rgba(248, 113, 113, 0.4),
            0 0 48px rgba(239, 68, 68, 0.2);
        animation: statusPulse 1.5s ease-in-out infinite;
    }

    @keyframes statusPulse {
        0%, 100% {
            transform: scale(1);
            opacity: 1;
        }
        50% {
            transform: scale(1.1);
            opacity: 0.8;
        }
    }
}
`;