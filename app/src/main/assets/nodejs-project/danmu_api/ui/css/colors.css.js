// language=CSS
export const colorsCssContent = /* css */ `/* ========================================
   颜色工具类 & 色板展示
   - 颜色变量（Design Tokens）已迁移到 tokens.css.js
   - 这里仅保留使用变量的工具类和展示组件样式
   ======================================== */
/* ========================================
   语义化颜色类
   ======================================== */

/* 文本颜色 */
.text-primary { color: var(--text-primary) !important; }
.text-secondary { color: var(--text-secondary) !important; }
.text-tertiary { color: var(--text-tertiary) !important; }
.text-success { color: var(--success-color) !important; }
.text-warning { color: var(--warning-color) !important; }
.text-danger { color: var(--danger-color) !important; }
.text-info { color: var(--primary-color) !important; }

/* 背景颜色 */
.bg-primary { background: var(--bg-primary) !important; }
.bg-secondary { background: var(--bg-secondary) !important; }
.bg-tertiary { background: var(--bg-tertiary) !important; }
.bg-success { background: var(--success-color) !important; color: white !important; }
.bg-warning { background: var(--warning-color) !important; color: white !important; }
.bg-danger { background: var(--danger-color) !important; color: white !important; }
.bg-info { background: var(--primary-color) !important; color: white !important; }

/* 渐变背景 */
.bg-gradient-primary { background: var(--gradient-primary) !important; }
.bg-gradient-success { background: var(--gradient-success) !important; }
.bg-gradient-warning { background: var(--gradient-warning) !important; }
.bg-gradient-danger { background: var(--gradient-danger) !important; }

/* ========================================
   颜色池编辑器 - 提示区
   ======================================== */
.color-pool-hint {
    font-size: 0.8125rem;
    color: var(--text-secondary);
    margin-bottom: 1rem;
    padding: 0.75rem 1rem;
    background: linear-gradient(135deg, rgba(99, 102, 241, 0.08), rgba(139, 92, 246, 0.05));
    border-left: 3px solid var(--primary-color);
    border-radius: var(--radius-sm);
    display: flex;
    align-items: center;
    gap: 0.5rem;
    box-shadow: 0 4px 12px rgba(99, 102, 241, 0.08);
}

.color-pool-hint::before {
    content: '💡';
    font-size: 1rem;
    flex-shrink: 0;
}

/* ========================================
   颜色池编辑器 - 控制区
   ======================================== */
.color-pool-controls {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 0.75rem;
    margin-bottom: 1rem;
    padding: 1rem;
    background: linear-gradient(135deg, var(--bg-secondary), var(--bg-tertiary));
    border-radius: var(--radius-lg);
    border: 1px solid var(--border-color);
    box-shadow: var(--shadow-sm);
}

/* 输入区占满整行 */
.color-pool-controls .color-input-group {
    grid-column: 1 / -1;
}

.color-input-group {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
}

.color-input-group.picker-active {
    z-index: 10003;
}

.color-input-label {
    font-size: 0.75rem;
    font-weight: 600;
    color: var(--text-secondary);
    text-transform: uppercase;
    letter-spacing: 0.5px;
}

.color-input-wrapper {
    display: grid;
    grid-template-columns: auto 1fr auto;
    gap: 0.75rem;
    align-items: stretch;
}

/* ========================================
   高级调色板面板
   ======================================== */
.color-picker-panel-wrapper {
    position: relative;
    z-index: 1;
}

.color-picker-panel-wrapper.picker-active {
    z-index: 10002;
}

.color-picker-trigger {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 0.75rem;
    padding: 0.625rem 1rem;
    background: var(--bg-primary);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-md);
    cursor: pointer;
    transition: all var(--transition-fast);
    min-height: 44px;
    min-width: 140px;
}

.color-picker-trigger:hover {
    border-color: var(--primary-color);
    box-shadow: 0 4px 12px rgba(99, 102, 241, 0.12);
}

.color-picker-trigger.active {
    border-color: var(--primary-color);
    box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.color-preview {
    width: 28px;
    height: 28px;
    border-radius: var(--radius-sm);
    border: 2px solid rgba(255, 255, 255, 0.9);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15), inset 0 1px 2px rgba(0, 0, 0, 0.1);
    flex-shrink: 0;
    transition: transform var(--transition-fast);
}

.color-picker-trigger:hover .color-preview {
    transform: scale(1.05);
}

.color-picker-label {
    font-size: 0.875rem;
    font-weight: 500;
    color: var(--text-secondary);
    user-select: none;
    white-space: nowrap;
    flex: 1;
}

.picker-arrow {
    width: 16px;
    height: 16px;
    color: var(--text-tertiary);
    transition: transform var(--transition-fast);
    flex-shrink: 0;
}

.color-picker-trigger.active .picker-arrow {
    transform: rotate(180deg);
}

/* 调色板下拉面板 */
.color-picker-dropdown {
    position: absolute;
    top: calc(100% + 8px);
    left: 0;
    min-width: 300px;
    background: var(--bg-primary);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-lg);
    padding: 1rem;
    box-shadow: 0 20px 40px rgba(0, 0, 0, 0.2);
    z-index: 10001;
    display: none;
    opacity: 0;
    transform: translateY(-10px) scale(0.95);
    transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.color-picker-dropdown.active {
    display: block;
    opacity: 1;
    transform: translateY(0) scale(1);
}

/* 主调色板画布 */
.color-picker-canvas-wrapper {
    position: relative;
    width: 100%;
    aspect-ratio: 280 / 180;
    border-radius: var(--radius-md);
    overflow: hidden;
    box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.1);
    margin-bottom: 0.75rem;
    cursor: crosshair;
}

#color-picker-canvas {
    display: block;
    width: 100%;
    height: 100%;
}

.color-picker-cursor {
    position: absolute;
    width: 16px;
    height: 16px;
    border: 3px solid white;
    border-radius: 50%;
    pointer-events: none;
    transform: translate(-50%, -50%);
    box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.3), 0 2px 8px rgba(0, 0, 0, 0.3);
}

/* 色相条 */
.color-picker-hue-wrapper {
    position: relative;
    width: 100%;
    height: 20px;
    border-radius: var(--radius-sm);
    overflow: hidden;
    box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.1);
    margin-bottom: 1rem;
    cursor: pointer;
}

#color-picker-hue {
    display: block;
    width: 100%;
    height: 100%;
}

.color-hue-cursor {
    position: absolute;
    top: 50%;
    width: 4px;
    height: 140%;
    background: white;
    border-radius: 2px;
    pointer-events: none;
    transform: translate(-50%, -50%);
    box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.3), 0 2px 6px rgba(0, 0, 0, 0.4);
}

/* 颜色信息区 */
.color-picker-info {
    display: flex;
    gap: 1rem;
    align-items: stretch;
}

.color-preview-large {
    width: 60px;
    height: 60px;
    border-radius: var(--radius-md);
    border: 2px solid rgba(255, 255, 255, 0.9);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    flex-shrink: 0;
}

.color-values {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
}

.color-value-group {
    display: flex;
    align-items: center;
    gap: 0.5rem;
}

.color-value-label {
    font-size: 0.6875rem;
    font-weight: 700;
    color: var(--text-tertiary);
    text-transform: uppercase;
    letter-spacing: 0.5px;
    min-width: 32px;
}

.color-value-input {
    flex: 1;
    padding: 0.375rem 0.625rem;
    font-size: 0.8125rem;
    font-family: 'Monaco', 'Consolas', 'Courier New', monospace;
    font-weight: 600;
    color: var(--text-primary);
    background: var(--bg-secondary);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-sm);
    text-transform: uppercase;
}

/* ========================================
   HEX 输入框
   ======================================== */
.color-hex-input-wrapper {
    position: relative;
    display: flex;
    align-items: center;
    flex: 1;
}

.color-hex-prefix {
    position: absolute;
    left: 0.75rem;
    top: 50%;
    transform: translateY(-50%);
    font-size: 0.875rem;
    font-weight: 700;
    color: var(--text-tertiary);
    pointer-events: none;
    font-family: 'Monaco', 'Consolas', 'Courier New', monospace;
}

.color-hex-input {
    width: 100%;
    padding: 0.625rem 0.75rem 0.625rem 1.75rem;
    font-size: 0.875rem;
    font-family: 'Monaco', 'Consolas', 'Courier New', monospace;
    font-weight: 600;
    color: var(--text-primary);
    background: var(--bg-primary);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-md);
    transition: all var(--transition-fast);
    text-transform: uppercase;
    letter-spacing: 0.5px;
    min-height: 44px;
}

.color-hex-input::placeholder {
    color: var(--text-tertiary);
    font-weight: 500;
    text-transform: none;
}

.color-hex-input:hover {
    border-color: var(--primary-color);
}

.color-hex-input:focus {
    outline: none;
    border-color: var(--primary-color);
    box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
    background: var(--bg-primary);
}

/* ========================================
   添加按钮
   ======================================== */
.color-add-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 0.5rem;
    padding: 0 1.25rem;
    min-height: 44px;
    min-width: 90px;
    background: var(--gradient-primary);
    color: white;
    border: none;
    border-radius: var(--radius-md);
    font-weight: 600;
    font-size: 0.9375rem;
    cursor: pointer;
    transition: all var(--transition-fast);
    box-shadow: 0 4px 12px rgba(99, 102, 241, 0.25);
    white-space: nowrap;
}

.color-add-btn svg {
    width: 18px;
    height: 18px;
    stroke-width: 2.5;
}

.color-add-btn:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 20px rgba(99, 102, 241, 0.35);
}

.color-add-btn:active {
    transform: translateY(0);
}

/* ========================================
   功能按钮样式
   ======================================== */
.color-pool-controls > .btn {
    min-height: 42px;
    border-radius: var(--radius-md);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 0.5rem;
    font-weight: 600;
    transition: all var(--transition-fast);
}

.color-pool-controls > .btn:hover {
    transform: translateY(-1px);
}

/* 重置按钮占满两列 */
.color-pool-controls > .btn.btn-danger {
    grid-column: 1 / -1;
}

.btn-icon-text {
    display: inline-flex;
    align-items: center;
    gap: 0.5rem;
    font-weight: 600;
}

/* ========================================
   颜色池容器
   ======================================== */
.color-pool-container {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(40px, 1fr));
    gap: 0.375rem;
    padding: 0.75rem;
    background: var(--bg-secondary);
    border: 2px dashed var(--border-color);
    border-radius: var(--radius-lg);
    min-height: 80px;
    align-content: start;
    transition: all var(--transition-fast);
}

.color-pool-container:hover {
    border-color: var(--primary-color);
    background: var(--bg-tertiary);
}

.color-pool-container.empty {
    display: flex;
    justify-content: center;
    align-items: center;
}

.color-pool-container.empty::before {
    content: '🎨 暂无颜色，请点击上方按钮添加';
    color: var(--text-tertiary);
    font-size: 0.875rem;
    text-align: center;
}

/* ========================================
   颜色块
   ======================================== */
@keyframes colorChipFadeIn {
    from {
        opacity: 0;
        transform: scale(0.6) translateY(20px) rotate(-5deg);
        filter: blur(4px);
    }
    to {
        opacity: 1;
        transform: scale(1) translateY(0) rotate(0deg);
        filter: blur(0);
    }
}

.color-chip {
    width: 100%;
    aspect-ratio: 1;
    min-height: 40px;
    border-radius: var(--radius-sm);
    position: relative;
    cursor: move;
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
    transition: all 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);
    border: 1.5px solid rgba(255, 255, 255, 0.5);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    overflow: visible;
    animation: colorChipFadeIn 0.5s cubic-bezier(0.34, 1.56, 0.64, 1) backwards;
    position: relative;
}

.color-chip::after {
    content: '';
    position: absolute;
    inset: -2px;
    border-radius: inherit;
    opacity: 0;
    background: inherit;
    filter: blur(8px);
    z-index: -1;
    transition: opacity 0.3s ease;
}

.color-chip:hover::after {
    opacity: 0.6;
}

[data-theme="dark"] .color-chip:hover::after {
    opacity: 0.8;
}

.color-chip:hover {
    transform: translateY(-4px) scale(1.05);
    box-shadow: 0 12px 24px rgba(0, 0, 0, 0.15);
    z-index: 10;
    border-color: rgba(255, 255, 255, 0.8);
}

.color-chip:active {
    cursor: grabbing;
}

.color-chip.dragging {
    opacity: 0.6;
    transform: scale(1.1) rotate(3deg);
    cursor: grabbing;
    z-index: 100;
    box-shadow: 0 16px 32px rgba(0, 0, 0, 0.2);
}

/* 颜色块标签 */
.color-hex-label {
    font-size: 0.5625rem;
    font-weight: 700;
    font-family: 'Monaco', 'Consolas', 'Courier New', monospace;
    color: rgba(0, 0, 0, 0.8);
    background: rgba(255, 255, 255, 0.95);
    padding: 0.125rem 0.25rem;
    border-radius: 4px;
    text-shadow: none;
    letter-spacing: 0.3px;
    pointer-events: none;
    user-select: none;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    backdrop-filter: blur(4px);
}

/* 删除按钮 */
.color-chip .remove-chip-btn {
    position: absolute;
    top: -8px;
    right: -8px;
    width: 24px;
    height: 24px;
    background: var(--danger-color);
    color: white;
    border-radius: 50%;
    border: 2px solid rgba(255, 255, 255, 0.9);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
    font-weight: 700;
    line-height: 1;
    cursor: pointer;
    opacity: 0;
    transform: scale(0.8);
    transition: all 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
    z-index: 2;
}

.color-chip:hover .remove-chip-btn {
    opacity: 1;
    transform: scale(1);
}

.color-chip .remove-chip-btn:hover {
    background: var(--danger-hover);
    transform: scale(1.15) rotate(90deg);
}

/* ========================================
   统计徽章
   ======================================== */
.pool-stats {
    display: inline-flex;
    align-items: center;
    gap: 0.75rem;
    flex-wrap: nowrap;
    white-space: nowrap;
}

.pool-count-badge {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    padding: 0.375rem 0.75rem;
    background: var(--gradient-primary);
    color: white;
    border-radius: 999px;
    font-weight: 600;
    font-size: 0.8125rem;
    box-shadow: 0 4px 12px rgba(99, 102, 241, 0.2);
}

.pool-count-icon {
    font-size: 0.9375rem;
}
.form-help {
    display: flex;
    align-items: center;
    justify-content: flex-start;
    margin-top: 0.5rem;
}

/* ========================================
   颜色池标题行
   ======================================== */
.color-pool-title-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 0.75rem;
    margin-bottom: 0.75rem;
}

.color-pool-title {
    font-size: 1rem;
    font-weight: 700;
    color: var(--text-primary);
    display: flex;
    align-items: center;
    gap: 0.375rem;
    flex-shrink: 1;
    min-width: 0;
}

/* ========================================
   批量导入模态框
   ======================================== */
.batch-import-modal {
    display: none;
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0, 0, 0, 0.6);
    backdrop-filter: blur(8px);
    z-index: 10000;
    align-items: center;
    justify-content: center;
    padding: 1rem;
    animation: fadeIn 0.3s ease-out;
}

.batch-import-modal.active {
    display: flex;
}

.batch-import-container {
    background: var(--bg-primary);
    border-radius: var(--radius-xl);
    padding: 1.5rem;
    max-width: 540px;
    width: 100%;
    max-height: 85vh;
    overflow-y: auto;
    box-shadow: 0 20px 50px rgba(0, 0, 0, 0.3);
    animation: modalSlideIn 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
    border: 1px solid var(--border-color);
}

.batch-import-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 1rem;
    padding-bottom: 1rem;
    border-bottom: 1px solid var(--border-color);
}

.batch-import-title {
    font-size: 1.25rem;
    font-weight: 700;
    color: var(--text-primary);
    display: flex;
    align-items: center;
    gap: 0.5rem;
}

.batch-import-title::before {
    content: '📦';
    font-size: 1.5rem;
}

.batch-import-close {
    width: 36px;
    height: 36px;
    background: var(--bg-secondary);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-md);
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: all var(--transition-fast);
    color: var(--text-secondary);
}

.batch-import-close:hover {
    background: var(--danger-color);
    color: white;
    border-color: var(--danger-color);
    transform: rotate(90deg);
}

.batch-import-body {
    display: flex;
    flex-direction: column;
    gap: 1rem;
}

.batch-import-hint {
    font-size: 0.875rem;
    color: var(--text-secondary);
    padding: 0.75rem 1rem;
    background: linear-gradient(135deg, rgba(99, 102, 241, 0.08), rgba(139, 92, 246, 0.05));
    border-left: 3px solid var(--primary-color);
    border-radius: var(--radius-sm);
    line-height: 1.6;
}

.batch-import-hint code {
    background: rgba(99, 102, 241, 0.15);
    padding: 0.125rem 0.375rem;
    border-radius: 4px;
    font-size: 0.8125rem;
    font-weight: 600;
    color: var(--primary-color);
}

.batch-import-textarea {
    width: 100%;
    min-height: 180px;
    padding: 1rem;
    font-family: 'Monaco', 'Consolas', 'Courier New', monospace;
    font-size: 0.875rem;
    line-height: 1.6;
    color: var(--text-primary);
    background: var(--bg-secondary);
    border: 2px solid var(--border-color);
    border-radius: var(--radius-md);
    resize: vertical;
    transition: all var(--transition-fast);
}

.batch-import-textarea::placeholder {
    color: var(--text-tertiary);
}

.batch-import-textarea:focus {
    outline: none;
    border-color: var(--primary-color);
    box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.batch-import-preview {
    display: block;
    padding: 1rem;
    background: var(--bg-tertiary);
    border-radius: var(--radius-md);
    min-height: 60px;
    border: 1px dashed var(--border-color);
}

.batch-import-preview-colors {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
    margin-top: 0.5rem;
}

.batch-import-preview.empty::before {
    content: '预览区 - 输入颜色后显示预览';
    color: var(--text-tertiary);
    font-size: 0.8125rem;
    width: 100%;
    text-align: center;
}

.batch-import-preview-chip {
    width: 36px;
    height: 36px;
    border-radius: var(--radius-sm);
    border: 2px solid rgba(255, 255, 255, 0.85);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
    transition: transform var(--transition-fast);
}

.batch-import-preview-chip:hover {
    transform: scale(1.15);
}

.batch-import-footer {
    display: flex;
    gap: 0.75rem;
    justify-content: flex-end;
    padding-top: 1rem;
    border-top: 1px solid var(--border-color);
}

.batch-import-footer .btn {
    min-width: 100px;
}

/* ========================================
   动画
   ======================================== */
@keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
}

@keyframes modalSlideIn {
    from {
        opacity: 0;
        transform: translateY(-20px) scale(0.95);
    }
    to {
        opacity: 1;
        transform: translateY(0) scale(1);
    }
}

/* ========================================
   暗色主题适配
   ======================================== */
[data-theme="dark"] .color-chip {
    border-color: rgba(255, 255, 255, 0.2);
}

[data-theme="dark"] .color-chip:hover {
    border-color: rgba(255, 255, 255, 0.4);
}

[data-theme="dark"] .color-hex-label {
    background: rgba(30, 30, 30, 0.95);
    color: rgba(255, 255, 255, 0.9);
}

[data-theme="dark"] .color-preview {
    border-color: rgba(255, 255, 255, 0.3);
}

[data-theme="dark"] .color-picker-dropdown {
    box-shadow: 0 20px 40px rgba(0, 0, 0, 0.5);
}

[data-theme="dark"] .batch-import-modal {
    background: rgba(0, 0, 0, 0.8);
}

/* ========================================
   响应式适配
   ======================================== */
@media (max-width: 640px) {
    .color-pool-controls {
        grid-template-columns: 1fr;
        gap: 0.625rem;
    }

    .color-pool-controls > .btn {
        grid-column: auto;
    }

    .color-input-wrapper {
        grid-template-columns: 1fr;
        gap: 0.5rem;
    }

    .color-picker-trigger {
        min-width: 100%;
    }

    .color-add-btn {
        width: 100%;
    }

    .color-pool-container {
        grid-template-columns: repeat(auto-fill, minmax(36px, 1fr));
        gap: 0.375rem;
        padding: 0.5rem;
    }

    .color-chip {
        min-height: 36px;
    }

    .color-hex-label {
        font-size: 0.5rem;
        padding: 0.125rem 0.25rem;
    }

    .batch-import-container {
        padding: 1rem;
        max-height: 90vh;
    }

    .color-picker-dropdown {
        min-width: 280px;
        left: 50%;
        transform: translateX(-50%) translateY(-10px) scale(0.95);
    }

    .color-picker-dropdown.active {
        transform: translateX(-50%) translateY(0) scale(1);
    }
}

@media (max-width: 480px) {
    .color-pool-hint {
        font-size: 0.75rem;
        padding: 0.625rem 0.75rem;
    }

    .pool-count-badge {
        font-size: 0.6875rem;
        padding: 0.1875rem 0.5rem;
    }

    .color-picker-info {
        flex-direction: column;
        gap: 0.75rem;
    }

    .color-preview-large {
        width: 100%;
        height: 48px;
    }

    .color-pool-container {
        grid-template-columns: repeat(auto-fill, minmax(32px, 1fr));
        gap: 0.25rem;
    }

    .color-chip {
        min-height: 32px;
    }

    .color-hex-label {
        font-size: 0.4375rem;
        padding: 0.0625rem 0.1875rem;
    }
}
`;

