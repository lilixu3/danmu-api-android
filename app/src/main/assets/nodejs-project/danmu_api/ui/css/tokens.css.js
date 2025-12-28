// language=CSS
export const tokensCssContent = /* css */ `
/* ========================================
   Design Tokens - 全局变量（统一维护入口）
   说明：
   1) 颜色色阶（primary/gray/success/warning/danger 等）
   2) 语义变量（bg/text/border/primary-color...）
   3) 阴影/模糊/过渡/圆角等基础 Token
   主题切换：
   - 默认：浅色（:root）
   - 深色：通过 [data-theme="dark"] 覆盖
   ======================================== */

:root {
    /* Primary 蓝色系 - 优化深色模式显示 */
    --primary-50: #eff6ff;
    --primary-100: #dbeafe;
    --primary-200: #bfdbfe;
    --primary-300: #93c5fd;
    --primary-400: #60a5fa;
    --primary-500: #3b82f6;
    --primary-600: #2563eb;
    --primary-700: #1d4ed8;
    --primary-800: #1e40af;
    --primary-900: #1e3a8a;
    --primary-light: #a78bfa; /* 更柔和的紫蓝色 */
    --primary-glow: rgba(99, 102, 241, 0.4); /* 发光效果 */

    /* Success 绿色系 */
    --success-50: #ecfdf5;
    --success-100: #d1fae5;
    --success-200: #a7f3d0;
    --success-300: #6ee7b7;
    --success-400: #34d399;
    --success-500: #10b981;
    --success-600: #059669;
    --success-700: #047857;
    --success-800: #065f46;
    --success-900: #064e3b;

    /* Warning 橙色系 */
    --warning-50: #fffbeb;
    --warning-100: #fef3c7;
    --warning-200: #fde68a;
    --warning-300: #fcd34d;
    --warning-400: #fbbf24;
    --warning-500: #f59e0b;
    --warning-600: #d97706;
    --warning-700: #b45309;
    --warning-800: #92400e;
    --warning-900: #78350f;

    /* Danger 红色系 */
    --danger-50: #fef2f2;
    --danger-100: #fee2e2;
    --danger-200: #fecaca;
    --danger-300: #fca5a5;
    --danger-400: #f87171;
    --danger-500: #ef4444;
    --danger-600: #dc2626;
    --danger-700: #b91c1c;
    --danger-800: #991b1b;
    --danger-900: #7f1d1d;

    /* Gray 灰色系 */
    --gray-50: #f9fafb;
    --gray-100: #f3f4f6;
    --gray-200: #e5e7eb;
    --gray-300: #d1d5db;
    --gray-400: #9ca3af;
    --gray-500: #6b7280;
    --gray-600: #4b5563;
    --gray-700: #374151;
    --gray-800: #1f2937;
    --gray-900: #111827;

    /* Purple 紫色系 */
    --purple-50: #faf5ff;
    --purple-100: #f3e8ff;
    --purple-200: #e9d5ff;
    --purple-300: #d8b4fe;
    --purple-400: #c084fc;
    --purple-500: #a855f7;
    --purple-600: #9333ea;
    --purple-700: #7e22ce;
    --purple-800: #6b21a8;
    --purple-900: #581c87;

    /* Pink 粉色系 */
    --pink-50: #fdf2f8;
    --pink-100: #fce7f3;
    --pink-200: #fbcfe8;
    --pink-300: #f9a8d4;
    --pink-400: #f472b6;
    --pink-500: #ec4899;
    --pink-600: #db2777;
    --pink-700: #be185d;
    --pink-800: #9f1239;
    --pink-900: #831843;

    /* Teal 青色系 */
    --teal-50: #f0fdfa;
    --teal-100: #ccfbf1;
    --teal-200: #99f6e4;
    --teal-300: #5eead4;
    --teal-400: #2dd4bf;
    --teal-500: #14b8a6;
    --teal-600: #0d9488;
    --teal-700: #0f766e;
    --teal-800: #115e59;
    --teal-900: #134e4a;

    /* Indigo 靛蓝系 */
    --indigo-50: #eef2ff;
    --indigo-100: #e0e7ff;
    --indigo-200: #c7d2fe;
    --indigo-300: #a5b4fc;
    --indigo-400: #818cf8;
    --indigo-500: #6366f1;
    --indigo-600: #4f46e5;
    --indigo-700: #4338ca;
    --indigo-800: #3730a3;
    --indigo-900: #312e81;

    /* ----------------------------------------
       语义 Token（主题 + 交互）
       ---------------------------------------- */
    /* 浅色模式颜色 - 引用色阶系统 */
    --bg-primary: rgba(255, 255, 255, 0.95);
    --bg-secondary: rgba(248, 250, 252, 0.9);
    --bg-tertiary: rgba(241, 245, 249, 0.85);
    --text-primary: #1e293b;
    --text-secondary: #64748b;
    --text-tertiary: #94a3b8;
    --border-color: rgba(226, 232, 240, 0.8);
    --shadow-sm: 0 2px 8px rgba(0, 0, 0, 0.06);
    --shadow-md: 0 4px 16px rgba(0, 0, 0, 0.08);
    --shadow-lg: 0 8px 32px rgba(0, 0, 0, 0.12);
    --blur-sm: blur(8px);
    --blur-md: blur(12px);
    --blur-lg: blur(16px);
    
    /* 功能色 - 引用色阶 */
    --primary-color: var(--primary-500);
    --primary-hover: var(--primary-600);
    --success-color: var(--success-500);
    --success-hover: var(--success-600);
    --warning-color: var(--warning-500);
    --warning-hover: var(--warning-600);
    --danger-color: var(--danger-500);
    --danger-hover: var(--danger-600);
    
    /* 渐变色 */
    --gradient-primary: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    --gradient-success: linear-gradient(135deg, #10b981 0%, #059669 100%);
    --gradient-warning: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
    --gradient-danger: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
    
    /* 过渡效果 */
    --transition-fast: 0.15s ease;
    --transition-base: 0.3s ease;
    --transition-slow: 0.5s ease;
    
    /* 圆角 */
    --radius-sm: 8px;
    --radius-md: 12px;
    --radius-lg: 16px;
    --radius-xl: 20px;
}

[data-theme="dark"] {
    /* 背景色 - 更深邃的黑色调，带有微妙的蓝色底色 */
    --bg-primary: rgba(10, 15, 30, 0.97);
    --bg-secondary: rgba(17, 24, 39, 0.92);
    --bg-tertiary: rgba(31, 41, 55, 0.88);
    
    /* 文本色 - 更柔和的白色，减少眼睛疲劳 */
    --text-primary: #e2e8f0;
    --text-secondary: #a0aec0;
    --text-tertiary: #718096;
    
    /* 边框色 - 带有微妙的发光效果 */
    --border-color: rgba(99, 102, 241, 0.15);
    
    /* 阴影 - 更深邃，带有微妙的彩色光晕 */
    --shadow-sm: 0 2px 12px rgba(0, 0, 0, 0.5), 0 1px 4px rgba(99, 102, 241, 0.1);
    --shadow-md: 0 4px 20px rgba(0, 0, 0, 0.6), 0 2px 8px rgba(99, 102, 241, 0.15);
    --shadow-lg: 0 8px 40px rgba(0, 0, 0, 0.7), 0 4px 16px rgba(99, 102, 241, 0.2);
    
    /* 模糊效果增强 */
    --blur-sm: blur(12px);
    --blur-md: blur(16px);
    --blur-lg: blur(24px);
    
    /* 功能色 - 更亮，更有活力 */
    --primary-color: #818cf8;
    --primary-hover: #a78bfa;
    --success-color: #34d399;
    --success-hover: #6ee7b7;
    --warning-color: #fbbf24;
    --warning-hover: #fcd34d;
    --danger-color: #f87171;
    --danger-hover: #fca5a5;
    
    /* 渐变色 - 更鲜明的对比 */
    --gradient-primary: linear-gradient(135deg, #818cf8 0%, #a78bfa 50%, #c084fc 100%);
    --gradient-success: linear-gradient(135deg, #34d399 0%, #10b981 100%);
    --gradient-warning: linear-gradient(135deg, #fbbf24 0%, #f59e0b 100%);
    --gradient-danger: linear-gradient(135deg, #f87171 0%, #ef4444 100%);
    
    /* 特殊效果 */
    --glow-primary: 0 0 20px rgba(129, 140, 248, 0.3);
    --glow-success: 0 0 20px rgba(52, 211, 153, 0.3);
    --glow-warning: 0 0 20px rgba(251, 191, 36, 0.3);
    --glow-danger: 0 0 20px rgba(248, 113, 113, 0.3);
}

`;
