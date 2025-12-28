// language=CSS
export const modeBadgeCssContent = /* css */ `
/* ========================================
   模式徽章系统
   ======================================== */

/* 基础徽章样式 */
.badge {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    padding: 0.375rem 0.875rem;
    border-radius: var(--radius-md);
    font-size: 0.8125rem;
    font-weight: 600;
    letter-spacing: 0.3px;
    transition: all var(--transition-fast);
    border: 1px solid transparent;
}

.badge-sm {
    padding: 0.25rem 0.625rem;
    font-size: 0.75rem;
}

.badge-lg {
    padding: 0.5rem 1.125rem;
    font-size: 0.9375rem;
}

/* ========================================
   模式徽章变体
   ======================================== */

/* 预览模式 */
.mode-badge-preview {
    background: linear-gradient(135deg, rgba(148, 163, 184, 0.15), rgba(100, 116, 139, 0.15));
    color: var(--gray-600);
    border-color: var(--gray-400);
}

[data-theme="dark"] .mode-badge-preview {
    background: linear-gradient(135deg, rgba(148, 163, 184, 0.2), rgba(100, 116, 139, 0.2));
    color: var(--gray-300);
    border-color: var(--gray-500);
}

/* 用户模式 */
.mode-badge-user {
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.15), rgba(37, 99, 235, 0.15));
    color: var(--primary-600);
    border-color: var(--primary-400);
}

[data-theme="dark"] .mode-badge-user {
    background: linear-gradient(135deg, rgba(59, 130, 246, 0.2), rgba(37, 99, 235, 0.2));
    color: var(--primary-300);
    border-color: var(--primary-500);
}

/* 管理员模式 */
.mode-badge-admin {
    background: linear-gradient(135deg, rgba(239, 68, 68, 0.15), rgba(220, 38, 38, 0.15));
    color: var(--danger-600);
    border-color: var(--danger-400);
    animation: pulse 2s ease-in-out infinite;
}

[data-theme="dark"] .mode-badge-admin {
    background: linear-gradient(135deg, rgba(239, 68, 68, 0.2), rgba(220, 38, 38, 0.2));
    color: var(--danger-300);
    border-color: var(--danger-500);
}

/* ========================================
   状态指示器
   ======================================== */

/* 运行中状态 */
.status-badge-running {
    background: linear-gradient(135deg, rgba(16, 185, 129, 0.15), rgba(5, 150, 105, 0.15));
    color: var(--success-600);
    border-color: var(--success-400);
}

.status-badge-running::before {
    content: '';
    width: 8px;
    height: 8px;
    background: var(--success-500);
    border-radius: 50%;
    animation: pulse 2s ease-in-out infinite;
}

[data-theme="dark"] .status-badge-running {
    background: linear-gradient(135deg, rgba(16, 185, 129, 0.2), rgba(5, 150, 105, 0.2));
    color: var(--success-300);
    border-color: var(--success-500);
}

/* 警告状态 */
.status-badge-warning {
    background: linear-gradient(135deg, rgba(245, 158, 11, 0.15), rgba(217, 119, 6, 0.15));
    color: var(--warning-600);
    border-color: var(--warning-400);
}

.status-badge-warning::before {
    content: '';
    width: 8px;
    height: 8px;
    background: var(--warning-500);
    border-radius: 50%;
    animation: blink 1.5s ease-in-out infinite;
}

[data-theme="dark"] .status-badge-warning {
    background: linear-gradient(135deg, rgba(245, 158, 11, 0.2), rgba(217, 119, 6, 0.2));
    color: var(--warning-300);
    border-color: var(--warning-500);
}

/* 错误状态 */
.status-badge-error {
    background: linear-gradient(135deg, rgba(239, 68, 68, 0.15), rgba(220, 38, 38, 0.15));
    color: var(--danger-600);
    border-color: var(--danger-400);
}

.status-badge-error::before {
    content: '';
    width: 8px;
    height: 8px;
    background: var(--danger-500);
    border-radius: 50%;
    animation: shake 0.5s ease-in-out infinite;
}

[data-theme="dark"] .status-badge-error {
    background: linear-gradient(135deg, rgba(239, 68, 68, 0.2), rgba(220, 38, 38, 0.2));
    color: var(--danger-300);
    border-color: var(--danger-500);
}

/* ========================================
   部署平台徽章
   ======================================== */

/* Node.js */
.deploy-badge-node {
    background: linear-gradient(135deg, rgba(16, 185, 129, 0.15), rgba(5, 150, 105, 0.15));
    color: var(--success-600);
    border-color: var(--success-400);
}

[data-theme="dark"] .deploy-badge-node {
    background: linear-gradient(135deg, rgba(16, 185, 129, 0.2), rgba(5, 150, 105, 0.2));
    color: var(--success-300);
}

/* Vercel */
.deploy-badge-vercel {
    background: linear-gradient(135deg, rgba(0, 0, 0, 0.1), rgba(67, 67, 67, 0.1));
    color: var(--gray-900);
    border-color: var(--gray-400);
}

[data-theme="dark"] .deploy-badge-vercel {
    background: linear-gradient(135deg, rgba(255, 255, 255, 0.1), rgba(245, 245, 245, 0.1));
    color: var(--gray-100);
    border-color: var(--gray-600);
}

/* Netlify */
.deploy-badge-netlify {
    background: linear-gradient(135deg, rgba(0, 199, 183, 0.15), rgba(0, 168, 150, 0.15));
    color: #00a896;
    border-color: #00c7b7;
}

[data-theme="dark"] .deploy-badge-netlify {
    background: linear-gradient(135deg, rgba(0, 199, 183, 0.2), rgba(0, 168, 150, 0.2));
    color: #00c7b7;
}

/* Cloudflare */
.deploy-badge-cloudflare {
    background: linear-gradient(135deg, rgba(243, 128, 32, 0.15), rgba(246, 130, 31, 0.15));
    color: #f38020;
    border-color: #f6821f;
}

[data-theme="dark"] .deploy-badge-cloudflare {
    background: linear-gradient(135deg, rgba(243, 128, 32, 0.2), rgba(246, 130, 31, 0.2));
    color: #f6821f;
}

/* EdgeOne */
.deploy-badge-edgeone {
    background: linear-gradient(135deg, rgba(0, 110, 255, 0.15), rgba(0, 82, 204, 0.15));
    color: #006eff;
    border-color: #0052cc;
}

[data-theme="dark"] .deploy-badge-edgeone {
    background: linear-gradient(135deg, rgba(0, 110, 255, 0.2), rgba(0, 82, 204, 0.2));
    color: #4d9eff;
}

/* Docker */
.deploy-badge-docker {
    background: linear-gradient(135deg, rgba(36, 150, 237, 0.15), rgba(29, 127, 193, 0.15));
    color: #2496ed;
    border-color: #1d7fc1;
}

[data-theme="dark"] .deploy-badge-docker {
    background: linear-gradient(135deg, rgba(36, 150, 237, 0.2), rgba(29, 127, 193, 0.2));
    color: #4daef5;
}

/* ========================================
   徽章图标
   ======================================== */
.badge-icon {
    width: 14px;
    height: 14px;
    flex-shrink: 0;
}

.badge-icon-pulse {
    animation: pulse 2s ease-in-out infinite;
}

/* ========================================
   徽章动画
   ======================================== */

/* 闪烁动画 */
@keyframes blink {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.3; }
}

/* 呼吸动画 */
@keyframes breathe {
    0%, 100% { transform: scale(1); opacity: 1; }
    50% { transform: scale(1.05); opacity: 0.8; }
}

/* 抖动动画 */
@keyframes shake {
    0%, 100% { transform: translateX(0); }
    10%, 30%, 50%, 70%, 90% { transform: translateX(-2px); }
    20%, 40%, 60%, 80% { transform: translateX(2px); }
}

/* ========================================
   交互式徽章
   ======================================== */
.badge-interactive {
    cursor: pointer;
    user-select: none;
}

.badge-interactive:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-sm);
}

.badge-interactive:active {
    transform: translateY(0);
}

/* ========================================
   徽章组
   ======================================== */
.badge-group {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
    align-items: center;
}

.badge-group-compact {
    gap: 0.375rem;
}

.badge-group-loose {
    gap: 0.75rem;
}

/* ========================================
   徽章计数器
   ======================================== */
.badge-counter {
    position: relative;
}

.badge-counter::after {
    content: attr(data-count);
    position: absolute;
    top: -6px;
    right: -6px;
    min-width: 18px;
    height: 18px;
    padding: 0 4px;
    background: var(--danger-500);
    color: white;
    font-size: 0.6875rem;
    font-weight: 700;
    border-radius: 9px;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 2px solid var(--bg-primary);
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

/* ========================================
   徽章状态点
   ======================================== */
.badge-dot {
    position: relative;
    padding-left: 1.5rem;
}

.badge-dot::before {
    content: '';
    position: absolute;
    left: 0.75rem;
    top: 50%;
    transform: translateY(-50%);
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: currentColor;
}

.badge-dot-pulse::before {
    animation: pulse 2s ease-in-out infinite;
}

.badge-dot-blink::before {
    animation: blink 1.5s ease-in-out infinite;
}

/* ========================================
   徽章轮廓样式
   ======================================== */
.badge-outline {
    background: transparent;
    border-width: 2px;
}

.badge-outline.badge-primary {
    color: var(--primary-color);
    border-color: var(--primary-color);
}

.badge-outline.badge-success {
    color: var(--success-color);
    border-color: var(--success-color);
}

.badge-outline.badge-warning {
    color: var(--warning-color);
    border-color: var(--warning-color);
}

.badge-outline.badge-danger {
    color: var(--danger-color);
    border-color: var(--danger-color);
}

/* ========================================
   徽章渐变样式
   ======================================== */
.badge-gradient-primary {
    background: var(--gradient-primary);
    color: white;
    border: none;
    box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.badge-gradient-success {
    background: var(--gradient-success);
    color: white;
    border: none;
    box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
}

.badge-gradient-warning {
    background: var(--gradient-warning);
    color: white;
    border: none;
    box-shadow: 0 4px 12px rgba(245, 158, 11, 0.3);
}

.badge-gradient-danger {
    background: var(--gradient-danger);
    color: white;
    border: none;
    box-shadow: 0 4px 12px rgba(239, 68, 68, 0.3);
}

/* ========================================
   徽章特殊效果
   ======================================== */

/* 发光徽章 */
.badge-glow {
    position: relative;
    overflow: visible;
}

.badge-glow::after {
    content: '';
    position: absolute;
    inset: -2px;
    background: inherit;
    border-radius: inherit;
    filter: blur(8px);
    opacity: 0.5;
    z-index: -1;
    animation: breathe 2s ease-in-out infinite;
}

/* 闪光徽章 */
.badge-shine {
    position: relative;
    overflow: hidden;
}

.badge-shine::before {
    content: '';
    position: absolute;
    top: 0;
    left: -100%;
    width: 100%;
    height: 100%;
    background: linear-gradient(
        90deg,
        transparent,
        rgba(255, 255, 255, 0.3),
        transparent
    );
    animation: shine 3s ease-in-out infinite;
}

@keyframes shine {
    0% { left: -100%; }
    20%, 100% { left: 100%; }
}

/* ========================================
   响应式徽章
   ======================================== */
@media (max-width: 767px) {
    .badge {
        font-size: 0.75rem;
        padding: 0.25rem 0.75rem;
    }

    .badge-sm {
        font-size: 0.6875rem;
        padding: 0.1875rem 0.5rem;
    }

    .badge-lg {
        font-size: 0.875rem;
        padding: 0.375rem 1rem;
    }

    .badge-icon {
        width: 12px;
        height: 12px;
    }
}
/* ========================================
   徽章高级效果 - 新增
   ======================================== */

/* 彩虹边框效果 */
[data-theme="dark"] .badge {
    position: relative;
    background-clip: padding-box;
}

[data-theme="dark"] .badge::before {
    content: '';
    position: absolute;
    inset: -1px;
    border-radius: inherit;
    padding: 1px;
    background: linear-gradient(
        135deg,
        rgba(129, 140, 248, 0.4),
        rgba(167, 139, 250, 0.4),
        rgba(192, 132, 252, 0.4),
        rgba(236, 72, 153, 0.4)
    );
    -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
    -webkit-mask-composite: xor;
    mask-composite: exclude;
    opacity: 0;
    transition: opacity 0.3s ease;
    pointer-events: none;
}

[data-theme="dark"] .badge:hover::before {
    opacity: 1;
}

/* 管理员徽章特殊动画 */
[data-theme="dark"] .mode-badge-admin {
    animation: adminPulse 2s ease-in-out infinite;
    box-shadow: 0 0 20px rgba(239, 68, 68, 0.4);
}

[data-theme="dark"] .mode-badge-admin::after {
    content: '';
    position: absolute;
    inset: -2px;
    border-radius: inherit;
    background: inherit;
    filter: blur(10px);
    opacity: 0.5;
    z-index: -1;
    animation: adminGlow 2s ease-in-out infinite;
}

@keyframes adminPulse {
    0%, 100% {
        transform: scale(1);
        box-shadow: 0 0 20px rgba(239, 68, 68, 0.4);
    }
    50% {
        transform: scale(1.05);
        box-shadow: 0 0 30px rgba(239, 68, 68, 0.6);
    }
}

@keyframes adminGlow {
    0%, 100% {
        opacity: 0.3;
    }
    50% {
        opacity: 0.6;
    }
}

/* 运行状态呼吸灯 */
[data-theme="dark"] .status-badge-running::before {
    box-shadow: 
        0 0 10px var(--success-500),
        0 0 20px rgba(16, 185, 129, 0.5);
}

[data-theme="dark"] .status-badge-running::after {
    content: '';
    position: absolute;
    width: 16px;
    height: 16px;
    background: var(--success-500);
    border-radius: 50%;
    left: 0.875rem;
    filter: blur(6px);
    opacity: 0.6;
    animation: breathingGlow 2s ease-in-out infinite;
}

@keyframes breathingGlow {
    0%, 100% {
        transform: scale(0.8);
        opacity: 0.4;
    }
    50% {
        transform: scale(1.2);
        opacity: 0.8;
    }
}
`;