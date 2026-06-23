/**
 * Node.js Mobile entry
 *
 * 这个 APK 的默认入口是 assets/nodejs-project/main.js。
 * Android 启动脚本是：android-server.js。
 *
 * 这里用 CommonJS 包一层，保持启动入口稳定，
 * 避免 danmu_api/server.js 依赖 esbuild（Android 上通常缺少对应二进制）。
 */

(async () => {
  const startupFailure = require('./startup-failure.js');
  try {
    startupFailure.clearStartupFailure?.();
  } catch {}

  try {
    require('./android-server.js');
  } catch (e) {
    try {
      startupFailure?.recordStartupFailure?.(e, { stage: 'main.import', exitCode: 1 });
    } catch {}
    console.error('[main] failed to start:', e && (e.stack || e));
    // 让 Node 退出码非 0，但不要硬杀宿主进程
    process.exitCode = 1;
  }
})();
