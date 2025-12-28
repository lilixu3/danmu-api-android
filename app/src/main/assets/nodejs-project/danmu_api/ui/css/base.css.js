// language=CSS
export const baseCssContent = /* css */ `/* ========================================
   基础样式（不含 Design Tokens）
   - 主题/颜色/阴影/圆角等变量已迁移到 tokens.css.js
   - 本文件负责 reset、基础排版、布局骨架与通用结构样式
   ======================================== */

/* ========================================
   基础重置
   ======================================== */
* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

html {
    font-size: 16px;
    scroll-behavior: smooth;
}

body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    color: var(--text-primary);
    background: var(--bg-secondary);
    line-height: 1.6;
    overflow-x: hidden;
    transition: background var(--transition-base), color var(--transition-base);
    max-width: 100vw;
}

/* 深色模式专属背景纹理 - 增强版 */
[data-theme="dark"] body {
    background: linear-gradient(
        180deg,
        rgba(10, 15, 30, 1) 0%,
        rgba(15, 23, 42, 1) 50%,
        rgba(10, 15, 30, 1) 100%
    );
    position: relative;
}

[data-theme="dark"] body::before {
    content: '';
    position: fixed;
    inset: 0;
    background-image: 
        radial-gradient(circle at 20% 20%, rgba(99, 102, 241, 0.15) 0%, transparent 40%),
        radial-gradient(circle at 80% 20%, rgba(139, 92, 246, 0.12) 0%, transparent 40%),
        radial-gradient(circle at 80% 80%, rgba(236, 72, 153, 0.15) 0%, transparent 40%),
        radial-gradient(circle at 20% 80%, rgba(59, 130, 246, 0.12) 0%, transparent 40%);
    animation: backgroundFlow 20s ease-in-out infinite alternate;
    pointer-events: none;
    z-index: 0;
}

[data-theme="dark"] body::after {
    content: '';
    position: fixed;
    inset: 0;
    background: 
        repeating-linear-gradient(
            0deg,
            rgba(99, 102, 241, 0.03) 0px,
            transparent 1px,
            transparent 2px,
            rgba(99, 102, 241, 0.03) 3px
        ),
        repeating-linear-gradient(
            90deg,
            rgba(139, 92, 246, 0.03) 0px,
            transparent 1px,
            transparent 2px,
            rgba(139, 92, 246, 0.03) 3px
        );
    pointer-events: none;
    z-index: 0;
}

@keyframes backgroundFlow {
    0% {
        transform: scale(1) rotate(0deg);
        opacity: 0.6;
    }
    50% {
        transform: scale(1.1) rotate(5deg);
        opacity: 0.8;
    }
    100% {
        transform: scale(1) rotate(0deg);
        opacity: 0.6;
    }
}

* {
    box-sizing: border-box;
}

/* 滚动条美化 */
::-webkit-scrollbar {
    width: 8px;
    height: 8px;
}

::-webkit-scrollbar-track {
    background: var(--bg-tertiary);
    border-radius: 4px;
}

::-webkit-scrollbar-thumb {
    background: var(--border-color);
    border-radius: 4px;
    transition: background var(--transition-fast);
}

::-webkit-scrollbar-thumb:hover {
    background: var(--text-tertiary);
}
/* 深色模式滚动条增强 - 高级版 */
[data-theme="dark"] ::-webkit-scrollbar-track {
    background: rgba(17, 24, 39, 0.3);
    border-radius: 4px;
    box-shadow: inset 0 0 6px rgba(0, 0, 0, 0.3);
}

[data-theme="dark"] ::-webkit-scrollbar-thumb {
    background: linear-gradient(
        135deg,
        rgba(129, 140, 248, 0.6) 0%,
        rgba(167, 139, 250, 0.6) 50%,
        rgba(192, 132, 252, 0.6) 100%
    );
    border-radius: 4px;
    border: 1px solid rgba(129, 140, 248, 0.2);
    box-shadow: 
        inset 0 1px 0 rgba(255, 255, 255, 0.1),
        0 0 10px rgba(129, 140, 248, 0.3);
    position: relative;
}

[data-theme="dark"] ::-webkit-scrollbar-thumb:hover {
    background: linear-gradient(
        135deg,
        rgba(129, 140, 248, 0.8) 0%,
        rgba(167, 139, 250, 0.8) 50%,
        rgba(192, 132, 252, 0.8) 100%
    );
    box-shadow: 
        inset 0 1px 0 rgba(255, 255, 255, 0.15),
        0 0 20px rgba(129, 140, 248, 0.6),
        0 0 40px rgba(167, 139, 250, 0.3);
}

[data-theme="dark"] ::-webkit-scrollbar-thumb:active {
    background: linear-gradient(
        135deg,
        rgba(129, 140, 248, 1) 0%,
        rgba(167, 139, 250, 1) 50%,
        rgba(192, 132, 252, 1) 100%
    );
}

/* ========================================
   布局容器
   ======================================== */
.app-container {
    display: flex;
    min-height: calc(var(--app-vh, 1vh) * 100);
    position: relative;
    max-width: 100vw;
    overflow-x: hidden;
}

.main-content {
    flex: 1;
    padding: 2rem;
    margin-left: 280px;
    transition: margin-left var(--transition-base);
}

/* ========================================
   顶部进度条
   ======================================== */
.progress-bar-top {
    position: fixed;
    top: 0;
    left: 0;
    width: 0;
    height: 3px;
    background: var(--gradient-primary);
    z-index: 9999;
    transition: width 0.3s ease, opacity 0.3s ease;
    opacity: 0;
}

.progress-bar-top.active {
    opacity: 1;
}

/* ========================================
   页脚
   ======================================== */
.footer {
    margin-top: 4rem;
    padding: 2rem;
    background: var(--bg-primary);
    backdrop-filter: var(--blur-md);
    border-top: 1px solid var(--border-color);
    border-radius: var(--radius-lg) var(--radius-lg) 0 0;
    box-shadow: var(--shadow-md);
}

.footer-description {
    margin-bottom: 1.5rem;
}

.footer-text {
    font-size: 0.875rem;
    color: var(--text-secondary);
    margin-bottom: 0.5rem;
    line-height: 1.7;
}

.footer-links {
    display: flex;
    gap: 1.5rem;
    flex-wrap: wrap;
    margin-bottom: 1rem;
}

.footer-link {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    color: var(--text-secondary);
    text-decoration: none;
    font-size: 0.875rem;
    padding: 0.5rem 1rem;
    background: var(--bg-secondary);
    border-radius: var(--radius-sm);
    transition: all var(--transition-fast);
}

.footer-link:hover {
    color: var(--primary-color);
    background: var(--bg-tertiary);
    transform: translateY(-2px);
}

.footer-link-icon {
    font-size: 1.25rem;
}

.footer-icon {
    width: 20px;
    height: 20px;
}

.footer-note {
    font-size: 0.75rem;
    color: var(--text-tertiary);
    text-align: center;
    margin-top: 1rem;
    padding-top: 1rem;
    border-top: 1px solid var(--border-color);
}

/* ========================================
   工具类
   ======================================== */
.text-center {
    text-align: center;
}

.mt-1 { margin-top: 0.5rem; }
.mt-2 { margin-top: 1rem; }
.mt-3 { margin-top: 1.5rem; }
.mt-4 { margin-top: 2rem; }

.mb-1 { margin-bottom: 0.5rem; }
.mb-2 { margin-bottom: 1rem; }
.mb-3 { margin-bottom: 1.5rem; }
.mb-4 { margin-bottom: 2rem; }

/* ========================================
   动画关键帧
   ======================================== */
@keyframes fadeIn {
    from {
        opacity: 0;
    }
    to {
        opacity: 1;
    }
}

@keyframes fadeInUp {
    from {
        opacity: 0;
        transform: translateY(20px);
    }
    to {
        opacity: 1;
        transform: translateY(0);
    }
}

@keyframes fadeInDown {
    from {
        opacity: 0;
        transform: translateY(-20px);
    }
    to {
        opacity: 1;
        transform: translateY(0);
    }
}

@keyframes fadeOutRight {
    from {
        opacity: 1;
        transform: translateX(0);
    }
    to {
        opacity: 0;
        transform: translateX(30px);
    }
}

@keyframes pulse {
    0%, 100% {
        transform: scale(1);
    }
    50% {
        transform: scale(1.05);
    }
}

@keyframes slideInRight {
    from {
        transform: translateX(100%);
    }
    to {
        transform: translateX(0);
    }
}

@keyframes modalSlideIn {
    from {
        opacity: 0;
        transform: scale(0.95) translateY(-20px);
    }
    to {
        opacity: 1;
        transform: scale(1) translateY(0);
    }
}

@keyframes modalSlideOut {
    from {
        opacity: 1;
        transform: scale(1) translateY(0);
    }
    to {
        opacity: 0;
        transform: scale(0.95) translateY(-20px);
    }
}

/* ========================================
   加载遮罩
   ======================================== */
.loading-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.6);
    backdrop-filter: var(--blur-lg);
    display: none;
    align-items: center;
    justify-content: center;
    z-index: 10000;
    animation: fadeIn 0.3s ease;
}

.loading-overlay.active {
    display: flex;
}

.loading-content {
    text-align: center;
    background: var(--bg-primary);
    padding: 3rem;
    border-radius: var(--radius-xl);
    box-shadow: var(--shadow-lg);
    backdrop-filter: var(--blur-md);
    max-width: 400px;
}

.loading-spinner {
    width: 60px;
    height: 60px;
    border: 4px solid var(--border-color);
    border-top-color: var(--primary-color);
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
    margin: 0 auto 1.5rem;
}

@keyframes spin {
    to {
        transform: rotate(360deg);
    }
}

.loading-spinner-small {
    display: inline-block;
    width: 16px;
    height: 16px;
    border: 2px solid currentColor;
    border-top-color: transparent;
    border-radius: 50%;
    animation: spin 0.6s linear infinite;
    vertical-align: middle;
}

.loading-title {
    font-size: 1.25rem;
    font-weight: 600;
    color: var(--text-primary);
    margin-bottom: 0.5rem;
}

.loading-desc {
    font-size: 0.875rem;
    color: var(--text-secondary);
}
`;