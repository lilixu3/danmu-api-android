const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');
const { URL, pathToFileURL } = require('url');
const { clearStartupFailure, recordStartupFailure } = require('./startup-failure.js');
const { startFavoriteSchedulerHost } = require('./favorite-scheduler-host.js');
const { createAppOutboundRuntime, networkRuntimeFingerprint } = require('./app-outbound-runtime.js');
const { createAccessControl, isLoopbackIp, normalizeClientIp, readClientIp } = require('./app-access-control.js');
const { createServerLogging } = require('./app-server-logging.js');
const { createServerWorkerRuntime } = require('./server-worker-runtime.js');
const { applyDanmuXmlOverrides, createDanmakuCache } = require('./app-danmaku-cache.js');
const { createProxyHelper } = require('./app-proxy-helper.js');
const { createHttpRoutes } = require('./app-http-routes.js');
const {
  createConfigRuntime,
  listenOptions,
  listenUrl,
  readListenConfig,
} = require('./app-config-runtime.js');
const appManagement = require('./app-management.js');
const { coreProxyConfig } = require('./app-proxy-routing.js');
const _appOutboundPaths = {configPath:process.env.DANMU_APP_OUTBOUND_CONFIG};
// Resolve current module dir (ESM-safe)
// (__filename/__dirname already defined above)

// NOTE: https-proxy-agent is optional.
// - It's used only when PROXY_URL is a *forward proxy* (not a reverse-proxy base URL).
// - We lazy-load it so the server won't crash if node_modules is missing.

// The app bundles danmu_api variants, and selects one via DANMU_API_VARIANT:
//    - stable => ./danmu_api_stable
//    - dev    => ./danmu_api_dev
//    - custom => ./danmu_api_custom
let handleRequest = null;
let _directCoreGlobals = null;
let _loadedCoreDirectory = '';
let _workerEnabled = true;
let _hotReloadEnabled = true;
let _mainServer = null;
let _shutdownPromise = null;
let _directFavoriteSchedulerStop = () => {};
let _directFavoriteSchedulerSync = async () => {};
let _directFavoriteSchedulerGeneration = 0;

let _coreWatchers = [];
let _coreWatchDirs = new Set();
let _coreWatchVariant = null;
let _coreReloadTimer = null;
let _coreReloadInFlight = false;
let _coreReloadPending = false;
let _coreWatchAvailable = null;
let _coreStatCheckMs = 0;
let _coreSnapshot = null;
let _coreScanInFlight = false;
let _coreReloadSeq = 1;

const _VARIANT_ENV_KEY = 'DANMU_API_VARIANT';
const _RUNTIME_IDENTITY_ENV_KEY = 'DANMU_API_RUNTIME_IDENTITY';

// === 修改 1: 添加 custom 的映射 ===
const _VARIANT_MAP = {
  stable: { dir: 'danmu_api_stable', label: '稳定版 (huangxd-/danmu_api)' },
  dev: { dir: 'danmu_api_dev', label: '开发版 (lilixu3/danmu_api)' },
  custom: { dir: 'danmu_api_custom', label: '自定义版' },
};

// === 修改 2: 允许返回 custom 状态 ===
function _getVariant() {
  const raw = String(process.env[_VARIANT_ENV_KEY] || 'stable').trim().toLowerCase();
  if (raw === 'dev' || raw === 'development') return 'dev';
  if (raw === 'custom') return 'custom';
  return 'stable';
}

let _appOutbound = null;
function _getEnvSnapshot() {
  const out = {};
  for (const [k, v] of Object.entries(process.env)) {
    out[k] = String(v);
  }
  if (_appOutbound?.snapshot().config.enabled || _appOutbound?.snapshot().config.proxy?.enabled) out.OUTBOUND_MODE = 'off';
  return out;
}

function _getRuntimeIdentity() {
  return String(process.env[_RUNTIME_IDENTITY_ENV_KEY] || '').trim();
}

function _refreshRuntimeFlags() {
  _workerEnabled = _readBoolEnv('DANMU_API_WORKER', true);
  _hotReloadEnabled = _readBoolEnv('DANMU_API_HOT_RELOAD', true);

  if (!_hotReloadEnabled) {
    _clearCoreWatchers();
  }
  const activeWorker = workerRuntime.activeState;
  if (!_workerEnabled && activeWorker) {
    workerRuntime.shutdownWorker(activeWorker);
  }
}

const _SERVER_CLOSE_TIMEOUT_MS = 350;

function _closeServer(server) {
  if (!server) return Promise.resolve();
  return new Promise((resolve) => {
    let settled = false;
    const finish = () => {
      if (settled) return;
      settled = true;
      resolve();
    };
    const fallback = setTimeout(finish, _SERVER_CLOSE_TIMEOUT_MS);
    try {
      server.close(() => {
        clearTimeout(fallback);
        finish();
      });
      try { server.closeIdleConnections?.(); } catch {}
      try { server.closeAllConnections?.(); } catch {}
    } catch {
      clearTimeout(fallback);
      finish();
    }
  });
}

function _exitProcess(exitCode) {
  try {
    // 这里不能再用 process.exit(exitCode)：
    // 当前 android-server.js 运行在嵌入式 Node（node::Start）里，
    // 硬退出会直接杀掉宿主 :node / app_process 进程，
    // Java/Kotlin 侧拿不到 startNodeWithArguments() 的返回，
    // 也就无法把“真正启动失败”上报成 STATUS_ERROR，只会继续卡在“等待端口就绪”。
    //
    // 正确做法是：
    // 1. 先把 server / worker / watcher / log stream 全部关闭；
    // 2. 只设置 exitCode；
    // 3. 让事件循环自然排空，最终由 node::Start() 正常返回到宿主。
    //
    // 这样 NodeService / RootNodeEntry 才能走到自己的 finally / 错误上报路径，
    // App 看到的是“服务已停止 -> 启动失败”，而不是一直卡在启动中。
    process.exitCode = exitCode;
  } catch {
    // ignore
  }
}

async function _gracefulShutdown(exitCode = 0, reason = 'shutdown') {
  if (_shutdownPromise) return _shutdownPromise;

  _shutdownPromise = (async () => {
    log('Shutting down ...', reason);
    shutdownFn = null;

    configRuntime.dispose();
    if (_coreReloadTimer) {
      clearTimeout(_coreReloadTimer);
      _coreReloadTimer = null;
    }
    _coreReloadPending = false;
    _clearCoreWatchers();
    _stopDirectFavoriteScheduler();

    const workerState = workerRuntime.takeActiveState();
    const mainServer = _mainServer;
    const proxyServer = proxyHelper.takeServer();
    const logStream = serverLogging.takeLogStream();
    _mainServer = null;

    await Promise.allSettled([
      workerRuntime.terminateWorker(workerState),
      _appOutbound?.stop(),
      _closeServer(proxyServer),
      _closeServer(mainServer),
      serverLogging.closeStreamAsync(logStream),
    ]);

    _exitProcess(exitCode);
  })();

  try {
    await _shutdownPromise;
  } finally {
    _shutdownPromise = null;
  }
}

function _stopDirectFavoriteScheduler() {
  _directFavoriteSchedulerGeneration += 1;
  try { _directFavoriteSchedulerStop(); } catch {}
  _directFavoriteSchedulerStop = () => {};
  _directFavoriteSchedulerSync = async () => {};
}

async function _scheduleDirectFavoriteScheduler(variantKey, info) {
  _stopDirectFavoriteScheduler();
  const generation = _directFavoriteSchedulerGeneration;
  try {
    const result = await startFavoriteSchedulerHost({
      projectDir: __dirname,
      variantDir: info.dir,
      env: _getEnvSnapshot(),
      port: PORT,
      log,
    });
    if (generation !== _directFavoriteSchedulerGeneration) {
      try { result?.stop?.(); } catch {}
      return;
    }
    _directFavoriteSchedulerStop = result?.stop || (() => {});
    _directFavoriteSchedulerSync = result?.sync || (async () => {});
    if (result?.supported) {
      log('[danmu_api]', `Favorite scheduler attached to direct core (${variantKey})`);
    }
  } catch (error) {
    if (generation === _directFavoriteSchedulerGeneration) {
      log('[danmu_api]', 'Favorite scheduler initialization skipped:', error?.message || error);
    }
  }
}

function _wrapDirectFavoritePersistence(coreHandler) {
  return async (...args) => {
    const req = args[0];
    try {
      return await coreHandler(...args);
    } finally {
      if (_isFavoriteMutationRequest(req?.url, req?.method)) {
        await _directFavoriteSchedulerSync();
      }
    }
  };
}

async function _loadHandleRequestForVariant(options = {}) {
  const forceReload = !!options.forceReload;
  const reloadTag = forceReload ? (_coreReloadSeq++) : 0;
  const v = _getVariant();
  // 确保 info 存在，如果不存在 fallback 到 stable
  const info = _VARIANT_MAP[v] || _VARIANT_MAP.stable;

  // Don't nuke the previous handler unless we have a new one successfully loaded.
  // This avoids breaking existing requests if reload fails.
  const tryLoad = async (variantKey) => {
    const i = _VARIANT_MAP[variantKey] || _VARIANT_MAP.stable;
    const workerLoaded = await workerRuntime.tryLoadViaWorker(variantKey, i, forceReload);
    if (workerLoaded) return workerLoaded;
    // 动态 import worker.js
    const workerUrl = pathToFileURL(path.join(__dirname, i.dir, 'worker.js'));
    if (forceReload) {
      workerUrl.searchParams.set('v', String(reloadTag));
    }
    const mod = await import(workerUrl.href);
    if (!mod?.handleRequest) {
      throw new Error(`worker.js missing export: handleRequest (variant=${variantKey})`);
    }
    await require('./app-proxy-routing.js').bindCoreSourceContext(path.join(__dirname, i.dir));
    let coreGlobals = null;
    for (const globalsRel of ['configs/globals.js', 'config/globals.js']) {
      try {
        const globalsUrl = pathToFileURL(path.join(__dirname, i.dir, globalsRel));
        if (forceReload) {
          globalsUrl.searchParams.set('v', String(reloadTag));
        }
        const globalsMod = await import(globalsUrl.href);
        coreGlobals = globalsMod?.globals || globalsMod?.Globals || null;
        if (coreGlobals) break;
      } catch {
        // try next layout
      }
    }
    return {
      handler: _wrapDirectFavoritePersistence(require('./app-series-refresh.js').wrapSeriesRefresh(
        require('./app-url-metadata.js').wrapUrlMetadata(mod.handleRequest, path.join(__dirname, i.dir)), path.join(__dirname, i.dir))),
      info: i,
      variantKey,
      coreGlobals,
    };
  };

  try {
    const loaded = await tryLoad(v);
    if (loaded.viaWorker) {
      _stopDirectFavoriteScheduler();
    } else {
      await _scheduleDirectFavoriteScheduler(loaded.variantKey, loaded.info);
    }
    handleRequest = loaded.handler;
    _directCoreGlobals = loaded.coreGlobals || null;
    _loadedCoreDirectory=path.join(__dirname,loaded.info.dir);
    _loadedRuntimeVariant = loaded.variantKey;
    runtimeDataContext.markLoaded();
    console.log('[danmu_api]', `Using variant: ${loaded.variantKey} => ${loaded.info.label}`);
    _setupCoreWatchersForVariant(loaded.variantKey);
    return;
  } catch (e) {
    console.error('[danmu_api]', `Failed to load variant=${v}:`, e?.stack || e);
    
    // 如果加载失败（例如 Custom 文件夹为空），且当前不是 stable，尝试回退到 stable
    if (v !== 'stable') {
      try {
        console.log('[danmu_api]', 'Attempting fallback to stable...');
        const loaded = await tryLoad('stable');
        if (loaded.viaWorker) {
          _stopDirectFavoriteScheduler();
        } else {
          await _scheduleDirectFavoriteScheduler(loaded.variantKey, loaded.info);
        }
        handleRequest = loaded.handler;
        _directCoreGlobals = loaded.coreGlobals || null;
        _loadedCoreDirectory=path.join(__dirname,loaded.info.dir);
        _loadedRuntimeVariant = loaded.variantKey;
        runtimeDataContext.markLoaded();
        console.log('[danmu_api]', `Fallback to stable => ${loaded.info.label}`);
        _setupCoreWatchersForVariant(loaded.variantKey);
        return;
      } catch (fallbackErr) {
        console.error('[danmu_api]', 'Fallback failed:', fallbackErr.message);
      }
    }
    // 如果连 fallback 都失败了，或者本来就是 stable 失败了，抛出异常
    throw e;
  }
}


// Persistent home (config/logs are stored outside the module folder)
const HOME = process.env.DANMU_API_HOME || __dirname;
const serverLogging = createServerLogging({
  home: HOME,
  env: process.env,
  fs,
  console,
  process,
});
const LOG_DIR = serverLogging.logDir;
const LOG_FILE = serverLogging.logFile;

function log(...args) {
  console.log('[danmu_api]', ...args);
}

const workerRuntime = createServerWorkerRuntime({
  projectDir: __dirname,
  getEnvSnapshot: _getEnvSnapshot,
  getOutboundSnapshot: () => _appOutbound?.snapshot(),
  emitLog: (level, line) => serverLogging.emitWorkerLog(level, line),
  log,
  isEnabled: () => _workerEnabled,
});
let _loadedRuntimeVariant = '';
const { createRuntimeContext, guardSession } = require('./app-runtime-context.js');
const runtimeDataContext = createRuntimeContext(HOME, () => _loadedRuntimeVariant || _getVariant(), _getEnvSnapshot);
const danmakuCache = createDanmakuCache({
  sendJson: _sendJson,
  isAdminAuthorized: _isAdminAuthorized,
  stripTokenPrefix: _stripTokenPrefix,
  getRequestHandler: () => handleRequest,
  getEnvSnapshot: _getEnvSnapshot,
  port: () => PORT,
});
if (!process.env.DANMU_API_HOME || !String(process.env.DANMU_API_HOME).trim()) {
  process.env.DANMU_API_HOME = HOME;
}
try {
  const current = (typeof process.cwd === 'function') ? String(process.cwd() || '').trim() : '';
  if (current !== HOME) {
    process.chdir(HOME);
  }
} catch {}
const CONFIG_DIR = path.join(HOME, 'config');

const ENV_FILE = path.join(CONFIG_DIR, '.env');
const ACCESS_CONTROL_FILE = path.join(CONFIG_DIR, 'access-control.json');

function ensureDirs() {
  try { fs.mkdirSync(CONFIG_DIR, { recursive: true }); } catch {}
  try { fs.mkdirSync(LOG_DIR, { recursive: true }); } catch {}
}

const configRuntime = createConfigRuntime({
  env: process.env,
  fs,
  home: HOME,
  ensureDirs,
  getBaseEnvSnapshot: _getEnvSnapshot,
  loadAccessControl: () => accessControl.loadFromDisk(),
  onReload: async () => {
    serverLogging.refreshLogLevel();
    serverLogging.refreshLogConfig();
    _refreshRuntimeFlags();
    await proxyHelper.sync();
    try {
      await _loadHandleRequestForVariant();
    } catch (error) {
      log('Reload handler failed:', error?.stack || error);
    }
    workerRuntime.syncEnvToWorker();
  },
  log,
});

function _resolveCacheProbeDir() {
  const envHome = String(process.env.DANMU_API_HOME || '').trim();
  const cwd = (typeof process.cwd === 'function') ? String(process.cwd() || '').trim() : '';
  const base = envHome || cwd || __dirname;
  return path.join(base, '.cache');
}

function _checkCacheProbeWritable(cacheDir) {
  const probeName = `.health_probe_${process.pid}_${Date.now()}_${Math.random().toString(16).slice(2)}`;
  const probePath = path.join(cacheDir, probeName);
  try {
    fs.mkdirSync(cacheDir, { recursive: true });
    fs.writeFileSync(probePath, 'ok', 'utf8');
    fs.unlinkSync(probePath);
    return true;
  } catch {
    try {
      if (fs.existsSync(probePath)) fs.unlinkSync(probePath);
    } catch {}
    return false;
  }
}

// Basic in-memory runtime metrics (exposed via /__health)
const METRICS = {
  startedAt: Date.now(),
  requestCount: 0,
  lastRequestAt: 0,
  lastRequestPath: '',
  lastClientIp: '',
};

function _readBoolEnv(key, def = false) {
  const raw = process.env[key];
  if (raw === undefined || raw === null) return def;
  const v = String(raw).trim().toLowerCase();
  if (!v) return def;
  return v === '1' || v === 'true' || v === 'yes' || v === 'on';
}

let HOST = '0.0.0.0';
let PORT = 9321;
let PROXY_PORT = 5321;

function _refreshListenConfigFromEnv() {
  const config = readListenConfig(process.env);
  HOST = config.host;
  PORT = config.port;
  PROXY_PORT = config.proxyPort;
}

function _listenOptions(port) {
  return listenOptions(HOST, port);
}

function _listenUrl(port, pathSuffix = '') {
  return listenUrl(HOST, port, pathSuffix);
}

// Will be set after servers are created (used by the Android app to stop Node gracefully)
let shutdownFn = null;

function _clearCoreWatchers() {
  for (const w of _coreWatchers) {
    try { w.close(); } catch {}
  }
  _coreWatchers = [];
  _coreWatchDirs = new Set();
  _coreWatchVariant = null;
  _coreWatchAvailable = null;
}

function _computeCoreSnapshot(rootDir) {
  if (!rootDir) return null;
  if (!fs.existsSync(rootDir)) return null;

  let maxMtime = 0;
  let totalSize = 0;
  let fileCount = 0;

  const stack = [rootDir];
  while (stack.length) {
    const dir = stack.pop();
    if (!dir) continue;
    let entries = [];
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      continue;
    }
    for (const ent of entries) {
      if (!ent) continue;
      const full = path.join(dir, ent.name);
      if (ent.isDirectory()) {
        stack.push(full);
        continue;
      }
      if (ent.isFile() || ent.isSymbolicLink()) {
        try {
          const st = fs.statSync(full);
          fileCount += 1;
          totalSize += st.size || 0;
          if (st.mtimeMs > maxMtime) maxMtime = st.mtimeMs;
        } catch {
          // ignore
        }
      }
    }
  }

  return `${fileCount}:${totalSize}:${Math.trunc(maxMtime)}`;
}

function _scheduleCoreReload(reason) {
  if (!_hotReloadEnabled) return;
  if (_coreReloadTimer) clearTimeout(_coreReloadTimer);

  _coreReloadTimer = setTimeout(async () => {
    if (_coreReloadInFlight) {
      _coreReloadPending = true;
      return;
    }
    _coreReloadInFlight = true;
    try {
      log('Core changed, reloading ...', reason || '');
      await _loadHandleRequestForVariant({ forceReload: true });
      workerRuntime.syncEnvToWorker();
    } catch (e) {
      log('Core reload failed:', e?.stack || e);
    } finally {
      _coreReloadInFlight = false;
      if (_coreReloadPending) {
        _coreReloadPending = false;
        _scheduleCoreReload('pending');
      }
    }
  }, 350);
}

function _maybeReloadCoreOnTraffic() {
  if (!_hotReloadEnabled) return;
  if (_coreWatchAvailable !== false) return;

  const now = Date.now();
  if (now - _coreStatCheckMs < 3000) return;
  if (_coreScanInFlight) return;
  _coreStatCheckMs = now;
  _coreScanInFlight = true;

  try {
    const variantKey = _getVariant();
    const info = _VARIANT_MAP[variantKey] || _VARIANT_MAP.stable;
    const coreDir = path.join(__dirname, info.dir);
    const snap = _computeCoreSnapshot(coreDir);
    if (!snap) return;
    if (_coreSnapshot && snap !== _coreSnapshot) {
      _scheduleCoreReload('traffic-scan');
    }
    _coreSnapshot = snap;
  } finally {
    _coreScanInFlight = false;
  }
}

function _onCoreFsEvent(baseDir, eventType, filename) {
  if (filename) {
    const full = path.join(baseDir, String(filename));
    try {
      const st = fs.statSync(full);
      if (st && st.isDirectory() && !_coreWatchDirs.has(full)) {
        _watchDirRecursive(full);
      }
    } catch {
      // ignore
    }
  }
  _scheduleCoreReload(`${eventType || 'change'}:${filename || ''}`);
}

function _watchDirRecursive(root) {
  const stack = [root];
  while (stack.length) {
    const dir = stack.pop();
    if (!dir || _coreWatchDirs.has(dir)) continue;
    _coreWatchDirs.add(dir);

    try {
      const watcher = fs.watch(dir, { persistent: false }, (eventType, filename) => {
        _onCoreFsEvent(dir, eventType, filename);
      });
      _coreWatchers.push(watcher);
    } catch (e) {
      log('fs.watch unavailable for', dir, e?.message || e);
      _coreWatchAvailable = false;
    }

    let entries = [];
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      continue;
    }
    for (const ent of entries) {
      if (ent && ent.isDirectory()) {
        stack.push(path.join(dir, ent.name));
      }
    }
  }
}

function _setupCoreWatchersForVariant(variantKey) {
  if (!_hotReloadEnabled) return;
  if (_coreWatchVariant === variantKey && _coreWatchers.length) return;

  _clearCoreWatchers();
  _coreWatchVariant = variantKey;

  const info = _VARIANT_MAP[variantKey] || _VARIANT_MAP.stable;
  const coreDir = path.join(__dirname, info.dir);
  if (!fs.existsSync(coreDir)) {
    log('Core dir missing, skip watch:', coreDir);
    _coreWatchAvailable = false;
    return;
  }

  _watchDirRecursive(coreDir);
  if (_coreWatchers.length > 0 && _coreWatchAvailable !== false) {
    _coreWatchAvailable = true;
    log('Watching core dir:', coreDir);
  } else {
    _coreWatchAvailable = false;
    _coreSnapshot = _computeCoreSnapshot(coreDir);
    log('Core watch unavailable, fallback to traffic scan:', coreDir);
  }
}

function _sendJson(res, statusCode, payload) {
  res.statusCode = statusCode;
  res.setHeader('content-type', 'application/json; charset=utf-8');
  res.end(JSON.stringify(payload));
}

async function readRequestBody(req) {
  return await new Promise((resolve, reject) => {
    const chunks = [];
    req.on('data', c => chunks.push(c));
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

async function _readJsonBody(req) {
  const buf = await readRequestBody(req);
  if (!buf || !buf.length) return {};
  try {
    return JSON.parse(buf.toString('utf-8'));
  } catch {
    throw new Error('请求体必须是 JSON');
  }
}

const QUIET_CORE_LOG_PATH_RE = /(^|\/)api\/(?:logs|reqrecords|cache\/animes)(?=(["'\s?#]|$))/i;
const FAVORITE_MUTATION_PATH_RE = /^\/api\/(?:v2\/)?favorite\/(?:add|remove|refresh|schedule)\/?$/i;

function _stripLikelyTokenPrefix(pathname) {
  const parts = String(pathname || '/').split('/').filter(Boolean);
  if (parts.length > 1 && parts[0] !== 'api') {
    return '/' + parts.slice(1).join('/');
  }
  return String(pathname || '/');
}

function _isFavoriteMutationRequest(url, method) {
  if (String(method || '').toUpperCase() !== 'POST') return false;
  try {
    const pathname = _stripLikelyTokenPrefix(new URL(String(url || '')).pathname || '/');
    return FAVORITE_MUTATION_PATH_RE.test(pathname);
  } catch {
    return false;
  }
}

function _isQuietCoreLogRequest(urlObj, clientIp) {
  if (!isLoopbackIp(clientIp)) return false;
  try {
    const pathname = _stripLikelyTokenPrefix(urlObj?.pathname || '/');
    return QUIET_CORE_LOG_PATH_RE.test(pathname);
  } catch {
    return false;
  }
}

function _coreLogMessage(entry) {
  return String(entry && typeof entry === 'object' ? (entry.message ?? '') : entry ?? '');
}

function _referencesQuietCorePath(message) {
  return QUIET_CORE_LOG_PATH_RE.test(String(message || ''));
}

function _isQuietRequestUrl(entry) {
  const message = _coreLogMessage(entry);
  return /\[Server\]\s+request url:/i.test(message) && _referencesQuietCorePath(message);
}

function _isQuietRequestPath(entry) {
  const message = _coreLogMessage(entry);
  return /\[Server\]\s+request path:/i.test(message) && _referencesQuietCorePath(message);
}

function _isServerClientIpLog(entry) {
  return /\[Server\]\s+client ip:/i.test(_coreLogMessage(entry));
}

function _isQuietSummary(entry) {
  const message = _coreLogMessage(entry);
  return /\[Server\]/i.test(message) &&
    _referencesQuietCorePath(message) &&
    !/request url:/i.test(message) &&
    !/request path:/i.test(message);
}

function _isLocalCacheInitInfo(entry) {
  const message = _coreLogMessage(entry);
  return /\[Cache\]\s+getLocalCaches start\./i.test(message) ||
    /\[Cache\]\s+Restored lastSelectMap from local cache/i.test(message) ||
    /\[Cache\]\s+getLocalCaches completed successfully\./i.test(message);
}

function _quietGroupSize(buffer, index) {
  if (index + 3 > buffer.length) return 0;
  if (!_isQuietRequestUrl(buffer[index]) ||
      !_isQuietRequestPath(buffer[index + 1]) ||
      !_isServerClientIpLog(buffer[index + 2])) {
    return 0;
  }
  return index + 3 < buffer.length && _isQuietSummary(buffer[index + 3]) ? 4 : 3;
}

function _quietLogScanStartHint() {
  const buffer = _directCoreGlobals?.logBuffer;
  if (!Array.isArray(buffer)) return 0;
  return Math.max(0, buffer.length - 8);
}

function _removeQuietCoreLogNoise(startHint = 0) {
  const buffer = _directCoreGlobals?.logBuffer;
  if (!Array.isArray(buffer) || buffer.length === 0) return;
  const start = Math.max(0, Math.min(buffer.length, Number(startHint) || 0));
  const head = buffer.slice(0, start);
  const tail = buffer.slice(start);
  const out = [];
  let changed = false;
  for (let i = 0; i < tail.length;) {
    const groupSize = _quietGroupSize(tail, i);
    if (groupSize > 0) {
      changed = true;
      i += groupSize;
      while (i < tail.length && _isLocalCacheInitInfo(tail[i])) i++;
      continue;
    }
    if (_isQuietRequestUrl(tail[i]) || _isQuietRequestPath(tail[i]) || _isQuietSummary(tail[i])) {
      changed = true;
      i++;
      continue;
    }
    out.push(tail[i]);
    i++;
  }
  if (changed) {
    _directCoreGlobals.logBuffer = head.concat(out);
  }
}

function _isAdminAuthorized(req, urlObj, clientIp) {
  if (isLoopbackIp(clientIp)) return true;

  const token = String(process.env.TOKEN || '').trim();
  if (!token) return false;

  const xAdminToken = String(req.headers['x-admin-token'] || '').trim();
  if (xAdminToken && xAdminToken === token) return true;

  const authHeader = String(req.headers.authorization || '').trim();
  if (authHeader) {
    const authMatch = authHeader.match(/^(?:Bearer|Token)\s+(.+)$/i);
    if (authMatch && String(authMatch[1] || '').trim() === token) return true;
    if (authHeader === token) return true;
  }

  const queryToken = String(urlObj?.searchParams?.get('token') || '').trim();
  return queryToken && queryToken === token;
}

function _stripTokenPrefix(pathname) {
  try {
    const token = String(process.env.TOKEN || '').trim();
    if (!token) return pathname;
    const prefix = `/${token}`;
    if (pathname === prefix) return '/';
    if (pathname.startsWith(prefix + '/')) return pathname.substring(prefix.length);
    return pathname;
  } catch {
    return pathname;
  }
}

const accessControl = createAccessControl({
  fs,
  file: ACCESS_CONTROL_FILE,
  ensureDirs,
  stripTokenPrefix: _stripTokenPrefix,
  isAdminAuthorized: _isAdminAuthorized,
  sendJson: _sendJson,
  readJsonBody: _readJsonBody,
  log,
});

const proxyHelper = createProxyHelper({
  readClientIp,
  stripTokenPrefix: _stripTokenPrefix,
  isAdminAuthorized: _isAdminAuthorized,
  accessControl,
  readRequestBody,
  coreProxyConfig,
  requestThroughProxy: require('./app-proxy-routing.js').requestThroughProxy,
  maybeReloadConfigOnTraffic: () => configRuntime.maybeReloadOnTraffic(),
  maybeReloadCoreOnTraffic: _maybeReloadCoreOnTraffic,
  getListenOptions: _listenOptions,
  getListenUrl: _listenUrl,
  getHost: () => HOST,
  getPort: () => PROXY_PORT,
  isMainServerListening: () => Boolean(_mainServer?.listening),
  isShuttingDown: () => Boolean(_shutdownPromise),
  closeServer: _closeServer,
  log,
});

const httpRoutes = createHttpRoutes({
  http,
  appManagement,
  readClientIp,
  readRequestBody,
  normalizeClientIp,
  guardSession,
  runtimeDataContext,
  configRuntime,
  accessControl,
  danmakuCache,
  workerRuntime,
  sourceProbe: require('./app-source-probe.js'),
  appOutboundDiagnostics: require('./app-outbound-diagnostics.js'),
  sendJson: _sendJson,
  stripTokenPrefix: _stripTokenPrefix,
  isAdminAuthorized: _isAdminAuthorized,
  getEnvSnapshot: _getEnvSnapshot,
  getOutboundSnapshot: () => _appOutbound?.snapshot(),
  getRequestHandler: () => handleRequest,
  getVariant: _getVariant,
  getVariantInfo: variantKey => _VARIANT_MAP[variantKey] || _VARIANT_MAP.stable,
  getLoadedCoreDirectory: () => _loadedCoreDirectory,
  getLoadedRuntimeVariant: () => _loadedRuntimeVariant,
  getRuntimeIdentity: _getRuntimeIdentity,
  getHost: () => HOST,
  getPort: () => PORT,
  getProxyPort: () => PROXY_PORT,
  getCacheProbeDir: _resolveCacheProbeDir,
  checkCacheProbeWritable: _checkCacheProbeWritable,
  getMetrics: () => METRICS,
  getLogFile: () => LOG_FILE,
  getHome: () => HOME,
  getProcess: () => process,
  getNetworkFingerprint: () => networkRuntimeFingerprint,
  maybeReloadCoreOnTraffic: _maybeReloadCoreOnTraffic,
  removeQuietCoreLogNoise: _removeQuietCoreLogNoise,
  quietLogScanStartHint: _quietLogScanStartHint,
  isQuietCoreLogRequest: _isQuietCoreLogRequest,
  applyDanmuXmlOverrides,
  refreshLogLevel: () => serverLogging.refreshLogLevel(),
  refreshLogConfig: () => serverLogging.refreshLogConfig(),
  syncEnvToWorker: () => workerRuntime.syncEnvToWorker(),
  requestShutdown: () => {
    try { shutdownFn && shutdownFn(); } catch {}
  },
  envFile: ENV_FILE,
  projectDir: __dirname,
});

function createMainServer() {
  return httpRoutes.createServer();
}

async function main() {
  clearStartupFailure();
  ensureDirs();
  configRuntime.loadConfigOnce();
  // .env 读取后再解析监听地址，确保 App 设置端口生效。
  _refreshListenConfigFromEnv();
  _refreshRuntimeFlags();
  const startupCacheDir = _resolveCacheProbeDir();
  log(`[runtime] cwd=${process.cwd()} envHome=${String(process.env.DANMU_API_HOME || '')} home=${HOME} cacheDir=${startupCacheDir}`);
  // Enable file logging after .env has been loaded (LOG_LEVEL takes effect)
  serverLogging.setupFileLogging();
  _appOutbound = createAppOutboundRuntime({..._appOutboundPaths, log, onSnapshot: () => workerRuntime.syncEnvToWorker()});
  await _appOutbound.start();
  await _loadHandleRequestForVariant();
  workerRuntime.syncEnvToWorker();
  configRuntime.watch();

  _mainServer = createMainServer();

  await new Promise((resolve, reject) => {
    _mainServer.listen(_listenOptions(PORT), () => {
      log(`Main server listening on ${_listenUrl(PORT)}`);
      resolve();
    });
    _mainServer.on('error', reject);
  });

  await proxyHelper.sync();

  shutdownFn = () => {
    void _gracefulShutdown(0, 'api');
  };

  process.on('SIGTERM', () => {
    void _gracefulShutdown(0, 'sigterm');
  });
  process.on('SIGINT', () => {
    void _gracefulShutdown(0, 'sigint');
  });
}

main().catch(async (e) => {
  const startupFailure = recordStartupFailure(e, { stage: 'main.startup', exitCode: 1 });
  log('Fatal error:', startupFailure.detail || e?.stack || e);
  await _gracefulShutdown(1, 'fatal');
});
