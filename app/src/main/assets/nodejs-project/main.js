/**
 * Node.js Mobile entry
 *
 * 这个 APK 的默认入口是 assets/nodejs-project/main.js。
 * 原项目的 Android 启动脚本是：android-server.mjs（ESM）。
 *
 * 这里用 CommonJS 包一层，通过 dynamic import 启动 ESM 入口，
 * 避免 danmu_api/server.js 依赖 esbuild（Android 上通常缺少对应二进制）。
 */

(async () => {
  try {
    await import('./android-server.mjs');
  } catch (e) {
    console.error('[main] failed to start:', e && (e.stack || e));
    // 让 Node 退出码非 0，但不要硬杀宿主进程
    process.exitCode = 1;
  }
})();
