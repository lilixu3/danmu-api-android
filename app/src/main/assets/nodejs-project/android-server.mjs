import http from 'http';
import https from 'https';
import fs from 'fs';
import net from 'net';
import path from 'path';
import { URL, fileURLToPath } from 'url';
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
let _activeWorkerState = null;
let _workerSeq = 1;
let _workerEnabled = true;
let _hotReloadEnabled = true;
let _workerSupport = null;
let _WorkerCtor = null;

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

function _getEnvSnapshot() {
  const out = {};
  for (const [k, v] of Object.entries(process.env)) {
    out[k] = String(v);
  }
  return out;
}

function _refreshRuntimeFlags() {
  _workerEnabled = _readBoolEnv('DANMU_API_WORKER', true);
  _hotReloadEnabled = _readBoolEnv('DANMU_API_HOT_RELOAD', true);

  if (!_hotReloadEnabled) {
    _clearCoreWatchers();
  }
  if (!_workerEnabled && _activeWorkerState) {
    _scheduleWorkerShutdown(_activeWorkerState);
    _activeWorkerState = null;
  }
}

async function _ensureWorkerSupport() {
  if (_workerSupport !== null) return _workerSupport;
  try {
    const mod = await import('node:worker_threads');
    _WorkerCtor = mod?.Worker || null;
    _workerSupport = !!_WorkerCtor;
    return _workerSupport;
  } catch (_e) {
    _WorkerCtor = null;
    _workerSupport = false;
    return false;
  }
}

function _newWorkerState(worker, variantKey) {
  return {
    worker,
    variantKey,
    pending: new Map(),
    ready: false,
    readyPromise: null,
    readyResolve: null,
    readyReject: null,
    createdAt: Date.now(),
  };
}

function _onWorkerMessage(state, msg) {
  if (!msg || typeof msg !== 'object') return;

  if (msg.type === 'ready') {
    state.ready = true;
    if (state.readyResolve) {
      state.readyResolve();
      state.readyResolve = null;
      state.readyReject = null;
    }
    return;
  }

  if (msg.type === 'log') {
    _emitWorkerLog(msg.level, msg.message);
    return;
  }

  if (msg.type === 'response' || msg.type === 'error') {
    const id = msg.id;
    const pending = state.pending.get(id);
    if (!pending) return;
    clearTimeout(pending.timer);
    state.pending.delete(id);
    if (msg.type === 'error') {
      pending.reject(new Error(msg.error || 'worker error'));
    } else {
      pending.resolve(msg);
    }
    return;
  }
}

function _onWorkerExit(state, err) {
  if (state.readyResolve) {
    state.readyReject && state.readyReject(err || new Error('worker exit'));
    state.readyResolve = null;
    state.readyReject = null;
  }

  for (const [id, pending] of state.pending.entries()) {
    clearTimeout(pending.timer);
    pending.reject(err || new Error('worker exit'));
    state.pending.delete(id);
  }

  if (_activeWorkerState === state) {
    _activeWorkerState = null;
  }
}

function _withTimeout(promise, timeoutMs, message) {
  return new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error(message || 'timeout')), timeoutMs);
    promise.then(
      (v) => {
        clearTimeout(t);
        resolve(v);
      },
      (e) => {
        clearTimeout(t);
        reject(e);
      }
    );
  });
}

function _emitWorkerLog(level, line) {
  const text = String(line || '').replace(/\r$/, '');
  if (!text) return;
  const useOrig = _origConsole || null;
  if (useOrig) {
    const fn = level === 'error'
      ? (useOrig.error || useOrig.log)
      : level === 'warn'
        ? (useOrig.warn || useOrig.log)
        : (useOrig.log || console.log);
    try { fn(text); } catch {}
    _writeLogLine(level === 'error' ? 'error' : level === 'warn' ? 'warn' : 'info', [text]);
    return;
  }
  if (level === 'error') console.error(text);
  else if (level === 'warn') console.warn(text);
  else console.log(text);
}

async function _spawnWorkerForVariant(variantKey, info) {
  const ok = await _ensureWorkerSupport();
  if (!ok || !_WorkerCtor) {
    throw new Error('worker_threads unavailable');
  }

  const workerUrl = new URL('./worker-proxy.mjs', import.meta.url);
  const envSnapshot = _getEnvSnapshot();
  const worker = new _WorkerCtor(workerUrl, {
    type: 'module',
    workerData: {
      variantDir: info.dir,
      projectDir: __dirname,
      env: envSnapshot,
    },
  });

  const state = _newWorkerState(worker, variantKey);
  state.readyPromise = new Promise((resolve, reject) => {
    state.readyResolve = resolve;
    state.readyReject = reject;
  });

  worker.on('message', (msg) => _onWorkerMessage(state, msg));
  worker.on('error', (err) => {
    log('Worker error:', err?.stack || err);
    _onWorkerExit(state, err);
  });
  worker.on('exit', (code) => {
    if (code !== 0) {
      log('Worker exit:', code);
    }
    _onWorkerExit(state, new Error(`worker exit ${code}`));
  });

  await _withTimeout(state.readyPromise, 10000, 'worker ready timeout');
  state.ready = true;
  return state;
}

function _scheduleWorkerShutdown(state) {
  const deadline = Date.now() + 10000;

  const tick = () => {
    if (!state || !state.worker) return;
    if (state.pending.size === 0 || Date.now() > deadline) {
      try { state.worker.terminate(); } catch {}
      return;
    }
    setTimeout(tick, 200);
  };

  setTimeout(tick, 200);
}

async function _ensureActiveWorkerForVariant(variantKey, info, forceReload = false) {
  if (!forceReload && _activeWorkerState && _activeWorkerState.variantKey === variantKey && _activeWorkerState.ready) {
    return _activeWorkerState;
  }

  const next = await _spawnWorkerForVariant(variantKey, info);
  const prev = _activeWorkerState;
  _activeWorkerState = next;
  if (prev) _scheduleWorkerShutdown(prev);
  return next;
}

function _postToWorker(state, msg) {
  if (!state || !state.worker) return false;
  try {
    state.worker.postMessage(msg);
    return true;
  } catch (_e) {
    return false;
  }
}

function _syncEnvToWorker() {
  if (!_activeWorkerState) return;
  const snapshot = _getEnvSnapshot();
  _postToWorker(_activeWorkerState, { type: 'setEnv', env: snapshot });
}

function _sendWorkerRequest(state, payload, timeoutMs = 30000) {
  return new Promise((resolve, reject) => {
    if (!state || !state.worker || !state.ready) {
      reject(new Error('worker not ready'));
      return;
    }

    const id = _workerSeq++;
    const timer = setTimeout(() => {
      state.pending.delete(id);
      reject(new Error('worker request timeout'));
    }, timeoutMs);

    state.pending.set(id, { resolve, reject, timer });
    try {
      state.worker.postMessage({ type: 'request', id, ...payload });
    } catch (e) {
      clearTimeout(timer);
      state.pending.delete(id);
      reject(e);
    }
  });
}

async function _handleRequestViaWorker(req, clientIp, variantKey = '', info = null) {
  let state = _activeWorkerState;
  if ((!state || !state.ready || (variantKey && state.variantKey !== variantKey)) && variantKey && info) {
    state = await _ensureActiveWorkerForVariant(variantKey, info, false);
  }
  if (!state || !state.ready) {
    throw new Error('worker not ready');
  }

  const headers = [];
  try {
    req.headers.forEach((value, key) => {
      headers.push([key, value]);
    });
  } catch {}

  let bodyBuf = null;
  try {
    const ab = await req.arrayBuffer();
    if (ab && ab.byteLength) bodyBuf = Buffer.from(ab);
  } catch {}

  const payload = {
    url: req.url,
    method: req.method,
    headers,
    body: bodyBuf,
    clientIp: clientIp || '',
  };

  let resp = null;
  try {
    resp = await _sendWorkerRequest(state, payload);
  } catch (e) {
    const message = String(e?.message || '');
    const retryable = !!variantKey && !!info && (
      message.includes('worker not ready') ||
      message.includes('worker exit') ||
      message.includes('worker request timeout')
    );
    if (!retryable) throw e;

    log('Worker request failed, retry with respawn:', message);
    const next = await _ensureActiveWorkerForVariant(variantKey, info, true);
    if (!next || !next.ready) throw e;
    resp = await _sendWorkerRequest(next, payload);
  }
  const outHeaders = new Headers(resp.headers || []);
  if (Array.isArray(resp.setCookie)) {
    for (const c of resp.setCookie) {
      if (!c) continue;
      try { outHeaders.append('set-cookie', c); } catch {}
    }
    if (typeof outHeaders.getSetCookie !== 'function') {
      outHeaders.getSetCookie = () => resp.setCookie;
    }
  }

  const body = resp.body ? Buffer.from(resp.body) : undefined;
  return new Response(body, { status: resp.status || 500, headers: outHeaders });
}

async function _tryLoadViaWorker(variantKey, info, forceReload = false) {
  if (!_workerEnabled) return null;
  const ok = await _ensureWorkerSupport();
  if (!ok || !_WorkerCtor) return null;

  try {
    await _ensureActiveWorkerForVariant(variantKey, info, forceReload);
  } catch (e) {
    log('Worker init failed, fallback to direct import:', e?.stack || e);
    if (_activeWorkerState) {
      _scheduleWorkerShutdown(_activeWorkerState);
      _activeWorkerState = null;
    }
    return null;
  }
  const handler = async (req, env, deployPlatform, clientIp) => {
    return _handleRequestViaWorker(req, clientIp, variantKey, info);
  };
  return { handler, info, variantKey, viaWorker: true };
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
    const workerLoaded = await _tryLoadViaWorker(variantKey, i, forceReload);
    if (workerLoaded) return workerLoaded;
    // 动态 import worker.js
    const workerUrl = new URL(`./${i.dir}/worker.js`, import.meta.url);
    if (forceReload) {
      workerUrl.searchParams.set('v', String(reloadTag));
    }
    const mod = await import(workerUrl.href);
    if (!mod?.handleRequest) {
      throw new Error(`worker.js missing export: handleRequest (variant=${variantKey})`);
    }
    return { handler: mod.handleRequest, info: i, variantKey };
  };

  try {
    const loaded = await tryLoad(v);
    handleRequest = loaded.handler;
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
        handleRequest = loaded.handler;
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


const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Persistent home (config/logs are stored outside the module folder)
const HOME = process.env.DANMU_API_HOME || __dirname;
const CONFIG_DIR = path.join(HOME, 'config');

const ENV_FILE = path.join(CONFIG_DIR, '.env');
const ACCESS_CONTROL_FILE = path.join(CONFIG_DIR, 'access-control.json');

// Config reload debounce + passive reload (no polling):
// - Prefer fs.watch (event-driven) when available.
// - If fs.watch is unavailable, DO NOT fall back to fs.watchFile polling (battery drain on mobile).
//   Instead, we check file mtimes only when requests arrive (throttled).
let _configReloadTimer = null;
let _lastConfigStatCheckMs = 0;
let _lastEnvMtimeMs = 0;
let _lastAccessControlMtimeMs = 0;

function _readMtimeMs(file) {
  try {
    return fs.statSync(file).mtimeMs || 0;
  } catch {
    return 0;
  }
}

function _recordConfigMtimes() {
  _lastEnvMtimeMs = _readMtimeMs(ENV_FILE);
  _lastAccessControlMtimeMs = _readMtimeMs(ACCESS_CONTROL_FILE);
}

function scheduleConfigReload() {
  if (_configReloadTimer) clearTimeout(_configReloadTimer);
  // NOTE: the callback must be async if we want to await inside.
  _configReloadTimer = setTimeout(async () => {
    log('Config changed, reloading ...');
    loadConfigOnce();
    _loadAccessControlFromDisk();
    // Refresh log level if user changed LOG_LEVEL in .env
    _refreshLogLevel();
    // Refresh file logging config if user changed App log settings in .env
    _refreshLogConfig();
    _refreshRuntimeFlags();
    // If variant/env changed, refresh handleRequest too.
    try {
      await _loadHandleRequestForVariant();
    } catch (e) {
      // Keep the old handler if reload fails; avoid crashing the host process.
      log('Reload handler failed:', e?.stack || e);
    }
    _syncEnvToWorker();
  }, 500);
}

function maybeReloadConfigOnTraffic() {
  const now = Date.now();
  if (now - _lastConfigStatCheckMs < 2000) return;
  _lastConfigStatCheckMs = now;

  const envM = _readMtimeMs(ENV_FILE);
  const aclM = _readMtimeMs(ACCESS_CONTROL_FILE);
  if (envM > _lastEnvMtimeMs || aclM > _lastAccessControlMtimeMs) {
    scheduleConfigReload();
  }
}

const LOG_DIR = path.join(HOME, 'logs');
const LOG_FILE = path.join(LOG_DIR, 'danmuapi.log');

// App-managed log settings (written by Android into config/.env):
//   DANMU_API_LOG_TO_FILE=1|0            (default: 0)
//   DANMU_API_LOG_MAX_BYTES=1048576      (default: 1MB)
//
// NOTE: This is NOT "polling". We only check size when writing logs.
const DEFAULT_LOG_MAX_BYTES = 1 * 1024 * 1024; // 1MB per file
const LOG_MAX_BACKUPS = 2; // danmuapi.log.1, danmuapi.log.2

// Basic in-memory runtime metrics (exposed via /__health)
const METRICS = {
  startedAt: Date.now(),
  requestCount: 0,
  lastRequestAt: 0,
  lastRequestPath: '',
  lastClientIp: '',
};

const _ACCESS_CONTROL_MODES = new Set(['off', 'blacklist', 'whitelist']);
const _ACCESS_INTERNAL_PATHS = new Set(['/__health', '/__shutdown', '/__access-control']);
const _ACCESS_TRACK_MAX_DEVICES = 240;

let _accessControl = {
  mode: 'off',
  whitelist: new Set(),
  blacklist: new Set(),
  updatedAtMs: 0,
};
let _accessAllowCount = 0;
let _accessBlockCount = 0;
const _accessDevices = new Map();

// File logging (best-effort):
// - Optionally writes console logs to LOG_FILE with rotation.
// - Respects LOG_LEVEL from env (.env supports it; default: info).
let _logStream = null;
let _origConsole = null;
let _logLevelValue = 2; // info

let _fileLogEnabled = false;
let _logMaxBytes = DEFAULT_LOG_MAX_BYTES;
let _logCurrentBytes = 0;

function _readBoolEnv(key, def = false) {
  const raw = process.env[key];
  if (raw === undefined || raw === null) return def;
  const v = String(raw).trim().toLowerCase();
  if (!v) return def;
  return v === '1' || v === 'true' || v === 'yes' || v === 'on';
}

function _readIntEnv(key, def, min, max) {
  const raw = process.env[key];
  if (raw === undefined || raw === null) return def;
  const n = Number(raw);
  if (!Number.isFinite(n)) return def;
  const v = Math.trunc(n);
  if (min !== undefined && v < min) return min;
  if (max !== undefined && v > max) return max;
  return v;
}

function _refreshLogLevel() {
  const raw = String(process.env.LOG_LEVEL || 'info').trim().toLowerCase();
  if (raw === 'error') _logLevelValue = 0;
  else if (raw === 'warn' || raw === 'warning') _logLevelValue = 1;
  else _logLevelValue = 2;
}

function _refreshLogConfig() {
  // default: disabled
  const enabled = _readBoolEnv('DANMU_API_LOG_TO_FILE', false);
  const maxBytes = _readIntEnv('DANMU_API_LOG_MAX_BYTES', DEFAULT_LOG_MAX_BYTES, 64 * 1024, 200 * 1024 * 1024);

  const enabledChanged = enabled !== _fileLogEnabled;
  const maxChanged = maxBytes !== _logMaxBytes;

  _fileLogEnabled = enabled;
  _logMaxBytes = maxBytes;

  if (!_fileLogEnabled) {
    // Close ASAP to stop file growth.
    try { _logStream && _logStream.end(); } catch {}
    try { _logStream && _logStream.destroy(); } catch {}
    _logStream = null;
    _logCurrentBytes = 0;
    return;
  }

  // Ensure directory exists when enabled.
  try { fs.mkdirSync(LOG_DIR, { recursive: true }); } catch {}

  // If max shrunk while stream is open and file already too big -> rotate once.
  if (_logStream && (enabledChanged || maxChanged)) {
    try {
      const st = fs.statSync(LOG_FILE);
      _logCurrentBytes = st?.size || _logCurrentBytes || 0;
    } catch {
      // ignore
    }
    if (_logCurrentBytes >= _logMaxBytes) {
      _rotateLogsOnce();
    }
  }
}

function _shouldWrite(level) {
  const v = level === 'error' ? 0 : level === 'warn' ? 1 : 2;
  return v <= _logLevelValue;
}

function _formatArg(a) {
  if (a === null) return 'null';
  if (a === undefined) return 'undefined';
  if (a instanceof Error) return a.stack || a.message || String(a);
  const t = typeof a;
  if (t === 'string') return a;
  if (t === 'bigint') return a.toString();
  if (t === 'object') {
    try {
      return JSON.stringify(a);
    } catch {
      try { return String(a); } catch { return '[object]'; }
    }
  }
  try {
    return String(a);
  } catch {
    return '[unprintable]';
  }
}

function _timestamp() {
  // ISO without milliseconds (smaller)
  const d = new Date();
  return d.toISOString().replace(/\.\d{3}Z$/, 'Z');
}

function _closeLogStream() {
  try { _logStream && _logStream.end(); } catch {}
  try { _logStream && _logStream.destroy(); } catch {}
  _logStream = null;
  _logCurrentBytes = 0;
}

function _rotateLogsOnce() {
  try {
    // Close current stream before renaming
    _closeLogStream();

    // Rotate: .2 (oldest) is dropped
    for (let i = LOG_MAX_BACKUPS; i >= 1; i--) {
      const src = `${LOG_FILE}.${i}`;
      const dst = `${LOG_FILE}.${i + 1}`;
      if (fs.existsSync(src)) {
        if (i === LOG_MAX_BACKUPS) {
          try { fs.rmSync(src, { force: true }); } catch {}
        } else {
          try { fs.renameSync(src, dst); } catch {}
        }
      }
    }
    if (fs.existsSync(LOG_FILE)) {
      try { fs.renameSync(LOG_FILE, `${LOG_FILE}.1`); } catch {}
    }
  } catch {
    // ignore
  }
}

function _ensureWritableLogFile() {
  try {
    if (!fs.existsSync(LOG_FILE)) return;
  } catch {
    return;
  }

  try {
    const st = fs.statSync(LOG_FILE);
    if (!st.isFile()) {
      const bad = `${LOG_FILE}.bad.${Date.now()}`;
      try { fs.renameSync(LOG_FILE, bad); } catch {}
      return;
    }
  } catch {
    // ignore
  }

  try {
    fs.accessSync(LOG_FILE, fs.constants.W_OK);
    return;
  } catch {
    // not writable
  }

  const suffix = new Date().toISOString().replace(/[:.]/g, '').replace('Z', '');
  const renamed = `${LOG_FILE}.readonly.${suffix}`;
  try {
    fs.renameSync(LOG_FILE, renamed);
    return;
  } catch {
    // ignore
  }

  try { fs.unlinkSync(LOG_FILE); } catch {}
}

function _ensureLogStream() {
  if (!_fileLogEnabled) return;
  if (_logStream) return;
  try {
    try { fs.mkdirSync(LOG_DIR, { recursive: true }); } catch {}
    _ensureWritableLogFile();

    // Initialize size counter from current file size once.
    try {
      const st = fs.statSync(LOG_FILE);
      _logCurrentBytes = st?.size || 0;
    } catch {
      _logCurrentBytes = 0;
    }

    // If already too big, rotate before opening.
    if (_logCurrentBytes >= _logMaxBytes) {
      _rotateLogsOnce();
      _logCurrentBytes = 0;
    }

    const stream = fs.createWriteStream(LOG_FILE, { flags: 'a' });
    stream.on('error', () => {
      try { stream.destroy(); } catch {}
      if (_logStream === stream) {
        _logStream = null;
        _logCurrentBytes = 0;
      }
    });
    _logStream = stream;
  } catch {
    _logStream = null;
  }
}

function _writeLogLine(level, args) {
  if (!_shouldWrite(level)) return;
  if (!_fileLogEnabled) return;

  try {
    _ensureLogStream();
    if (!_logStream) return;

    const line = `${_timestamp()} [${level.toUpperCase()}] ${args.map(_formatArg).join(' ')}\n`;
    const lineBytes = Buffer.byteLength(line, 'utf8');

    // Rotate before writing if this line would exceed max bytes (best-effort).
    if (_logCurrentBytes > 0 && (_logCurrentBytes + lineBytes) > _logMaxBytes) {
      _rotateLogsOnce();
      _ensureLogStream();
      if (!_logStream) return;
    }

    _logStream.write(line);
    _logCurrentBytes += lineBytes;

    // If a single line is huge and pushes us over, rotate on next write.
    if (_logCurrentBytes >= _logMaxBytes) {
      // Do nothing here to avoid rotating in the same tick; next line will rotate before writing.
    }
  } catch {
    // ignore
  }
}

function setupFileLogging() {
  // Must be called after loadConfigOnce() so .env is applied.
  _refreshLogLevel();
  _refreshLogConfig();

  if (!_origConsole) {
    _origConsole = {
      log: console.log.bind(console),
      info: (console.info || console.log).bind(console),
      warn: (console.warn || console.log).bind(console),
      error: (console.error || console.log).bind(console),
    };

    console.log = (...args) => { _origConsole.log(...args); _writeLogLine('info', args); };
    console.info = (...args) => { _origConsole.info(...args); _writeLogLine('info', args); };
    console.warn = (...args) => { _origConsole.warn(...args); _writeLogLine('warn', args); };
    console.error = (...args) => { _origConsole.error(...args); _writeLogLine('error', args); };
  }

  // Capture crashes
  process.on('unhandledRejection', (reason) => {
    console.error('[unhandledRejection]', reason && (reason.stack || reason));
  });
  process.on('uncaughtException', (err) => {
    console.error('[uncaughtException]', err && (err.stack || err));
  });

  process.on('exit', () => {
    _closeLogStream();
  });

  // Mark the beginning of a boot session in the log file (only if enabled)
  _writeLogLine('info', ['[danmu_api]', `log file: ${LOG_FILE}`, `LOG_LEVEL=${process.env.LOG_LEVEL || 'info'}`]);
}

let HOST = '0.0.0.0';
let PORT = 9321;
let PROXY_PORT = 9322;

function _safePort(raw, def) {
  const n = Number(raw);
  if (!Number.isFinite(n)) return def;
  const p = Math.trunc(n);
  if (p < 1 || p > 65535) return def;
  return p;
}

function _refreshListenConfigFromEnv() {
  const host = String(process.env.DANMU_API_HOST || '0.0.0.0').trim();
  HOST = host || '0.0.0.0';
  PORT = _safePort(process.env.DANMU_API_PORT, 9321);
  PROXY_PORT = _safePort(process.env.DANMU_API_PROXY_PORT, 9322);
}

// Will be set after servers are created (used by the Android app to stop Node gracefully)
let shutdownFn = null;

function log(...args) {
  console.log('[danmu_api]', ...args);
}

// Lazy-load optional dependency (keeps the server running even if node_modules is missing).
// - undefined: not attempted yet
// - null: attempted but unavailable
let _HttpsProxyAgentCtor = undefined;
async function getHttpsProxyAgentCtor() {
  if (_HttpsProxyAgentCtor !== undefined) return _HttpsProxyAgentCtor;
  try {
    const mod = await import('https-proxy-agent');
    _HttpsProxyAgentCtor = (mod && (mod.HttpsProxyAgent || mod.default)) || null;
    if (!_HttpsProxyAgentCtor) {
      log('https-proxy-agent loaded but missing export; forward proxy disabled');
    }
  } catch (e) {
    _HttpsProxyAgentCtor = null;
    log('https-proxy-agent not found; forward proxy disabled');
  }
  return _HttpsProxyAgentCtor;
}

function ensureDirs() {
  try { fs.mkdirSync(CONFIG_DIR, { recursive: true }); } catch {}
  try { fs.mkdirSync(LOG_DIR, { recursive: true }); } catch {}
}

function parseDotEnv(envText) {
  const out = {};
  const lines = envText.split(/\r?\n/);
  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;
    const eq = line.indexOf('=');
    if (eq <= 0) continue;
    const key = line.slice(0, eq).trim();
    let val = line.slice(eq + 1).trim();
    // strip surrounding quotes
    if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
      val = val.slice(1, -1);
    }
    out[key] = val;
  }
  return out;
}

function flattenObject(obj, prefix = '') {
  const result = {};
  if (!obj || typeof obj !== 'object') return result;

  for (const [k, v] of Object.entries(obj)) {
    if (!k) continue;
    const newKey = prefix ? `${prefix}_${k}` : k;

    if (v === null || v === undefined) continue;

    if (Array.isArray(v)) {
      // arrays -> JSON string
      result[newKey] = JSON.stringify(v);
    } else if (typeof v === 'object') {
      Object.assign(result, flattenObject(v, newKey));
    } else {
      result[newKey] = String(v);
    }
  }
  return result;
}

function applyEnv(kv, { override = true } = {}) {
  for (const [k, v] of Object.entries(kv)) {
    if (!override && process.env[k] !== undefined) continue;
    process.env[k] = v;
  }
}

function loadConfigOnce() {
  ensureDirs();

  const envFile = ENV_FILE;

  // 1) .env (runtime overrides system env if same key is set here)
  if (fs.existsSync(envFile)) {
    try {
      const t = fs.readFileSync(envFile, 'utf-8');
      const kv = parseDotEnv(t);
      applyEnv(kv, { override: true });
      log('Loaded .env:', envFile);
    } catch (e) {
      log('Failed to load .env:', e?.message || e);
    }
  } else {
    log('.env not found, skipping:', envFile);
  }
  _loadAccessControlFromDisk();
  _recordConfigMtimes();
}

function watchConfigs() {
  // Watch the config directory if possible (covers file create/rename).
  try {
    if (fs.existsSync(CONFIG_DIR)) {
      fs.watch(CONFIG_DIR, { persistent: false }, () => scheduleConfigReload());
    }
  } catch (e) {
    log('fs.watch(CONFIG_DIR) unavailable:', e?.message || e);
  }

  // Also watch individual files (some platforms are more reliable this way).
  for (const f of [ENV_FILE, ACCESS_CONTROL_FILE]) {
    try {
      if (!fs.existsSync(f)) continue;
      fs.watch(f, { persistent: false }, () => scheduleConfigReload());
    } catch (e) {
      // No polling fallback here to avoid battery drain on mobile.
      log('fs.watch unavailable for', f, '- will reload on traffic');
    }
  }
}

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
      _syncEnvToWorker();
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

function _normalizeClientIp(rawIp) {
  let value = String(rawIp || '').trim().toLowerCase();
  if (!value) return '';
  if (value.includes(',')) value = value.split(',')[0].trim();

  if (value.startsWith('[')) {
    const closeIdx = value.indexOf(']');
    if (closeIdx > 0) {
      value = value.substring(1, closeIdx);
    }
  }

  const zoneIdx = value.indexOf('%');
  if (zoneIdx > 0) {
    value = value.substring(0, zoneIdx);
  }

  if (/^\d{1,3}(?:\.\d{1,3}){3}:\d+$/.test(value)) {
    value = value.split(':')[0];
  }

  if (value.startsWith('::ffff:')) {
    const mapped = value.substring(7);
    if (net.isIP(mapped) === 4) return mapped;
  }

  if (net.isIP(value) > 0) return value;
  return '';
}

function _normalizeIpList(rawList) {
  const source = Array.isArray(rawList)
    ? rawList
    : typeof rawList === 'string'
      ? rawList.split(/[\s,;]+/)
      : [];
  const seen = new Set();
  const out = [];
  for (const item of source) {
    const ip = _normalizeClientIp(item);
    if (!ip || seen.has(ip)) continue;
    seen.add(ip);
    out.push(ip);
  }
  return out;
}

function _normalizeAccessMode(raw, fallback = 'off') {
  const mode = String(raw || '').trim().toLowerCase();
  if (!mode) return fallback;
  if (mode === 'blacklist' || mode === 'black' || mode === 'block' || mode === 'deny') return 'blacklist';
  if (mode === 'whitelist' || mode === 'white' || mode === 'allow') return 'whitelist';
  if (mode === 'off' || mode === 'none' || mode === 'disable' || mode === 'disabled') return 'off';
  return fallback;
}

function _snapshotAccessControl() {
  const updatedAtMs = Number(_accessControl.updatedAtMs || Date.now()) || Date.now();
  return {
    mode: _accessControl.mode || 'off',
    whitelist: Array.from(_accessControl.whitelist || []).sort(),
    blacklist: Array.from(_accessControl.blacklist || []).sort(),
    updatedAtMs,
    updatedAt: new Date(updatedAtMs).toISOString(),
  };
}

function _sanitizeAccessControl(raw, fallback = null, strictMode = false) {
  const base = fallback || _snapshotAccessControl();
  const data = raw && typeof raw === 'object' ? raw : {};

  let mode = base.mode;
  if (Object.prototype.hasOwnProperty.call(data, 'mode')) {
    const parsed = _normalizeAccessMode(data.mode, '');
    if (!parsed) {
      if (strictMode) {
        throw new Error('mode 仅支持 off / blacklist / whitelist');
      }
    } else {
      mode = parsed;
    }
  }
  if (!_ACCESS_CONTROL_MODES.has(mode)) {
    mode = 'off';
  }

  const whitelist = Object.prototype.hasOwnProperty.call(data, 'whitelist')
    ? _normalizeIpList(data.whitelist)
    : _normalizeIpList(base.whitelist);
  const blacklist = Object.prototype.hasOwnProperty.call(data, 'blacklist')
    ? _normalizeIpList(data.blacklist)
    : _normalizeIpList(base.blacklist);

  const updatedAtMs = Number(data.updatedAtMs || base.updatedAtMs || Date.now()) || Date.now();
  return { mode, whitelist, blacklist, updatedAtMs };
}

function _applyAccessControl(next) {
  _accessControl = {
    mode: _normalizeAccessMode(next?.mode, 'off'),
    whitelist: new Set(_normalizeIpList(next?.whitelist)),
    blacklist: new Set(_normalizeIpList(next?.blacklist)),
    updatedAtMs: Number(next?.updatedAtMs || Date.now()) || Date.now(),
  };
}

function _persistAccessControl() {
  try {
    ensureDirs();
    const current = _snapshotAccessControl();
    const next = {
      mode: current.mode,
      whitelist: current.whitelist,
      blacklist: current.blacklist,
      updatedAtMs: Date.now(),
    };
    _applyAccessControl(next);

    const tmpFile = `${ACCESS_CONTROL_FILE}.tmp`;
    fs.writeFileSync(tmpFile, `${JSON.stringify(next, null, 2)}\n`, 'utf-8');
    fs.renameSync(tmpFile, ACCESS_CONTROL_FILE);
    _lastAccessControlMtimeMs = _readMtimeMs(ACCESS_CONTROL_FILE);
    return true;
  } catch (e) {
    log('Failed to persist access-control config:', e?.message || e);
    return false;
  }
}

function _loadAccessControlFromDisk() {
  try {
    if (!fs.existsSync(ACCESS_CONTROL_FILE)) {
      _lastAccessControlMtimeMs = 0;
      if (!_ACCESS_CONTROL_MODES.has(_accessControl.mode)) {
        _applyAccessControl({ mode: 'off', whitelist: [], blacklist: [], updatedAtMs: Date.now() });
      }
      return;
    }
    const raw = fs.readFileSync(ACCESS_CONTROL_FILE, 'utf-8');
    const parsed = raw.trim() ? JSON.parse(raw) : {};
    const next = _sanitizeAccessControl(parsed, {
      mode: 'off',
      whitelist: [],
      blacklist: [],
      updatedAtMs: Date.now(),
    });
    _applyAccessControl(next);
    _lastAccessControlMtimeMs = _readMtimeMs(ACCESS_CONTROL_FILE);
  } catch (e) {
    log('Failed to load access-control config:', e?.message || e);
  }
}

function _isInternalAccessPath(pathname) {
  const stripped = _stripTokenPrefix(String(pathname || '/'));
  return _ACCESS_INTERNAL_PATHS.has(stripped);
}

function _evaluateClientAccess(clientIp, pathname) {
  const normalizedIp = _normalizeClientIp(clientIp);
  const mode = _accessControl.mode || 'off';

  if (_isInternalAccessPath(pathname)) {
    return { allowed: true, reason: 'internal', clientIp: normalizedIp };
  }
  if (_isLoopbackIp(normalizedIp)) {
    return { allowed: true, reason: 'loopback', clientIp: normalizedIp };
  }
  if (mode === 'off') {
    return { allowed: true, reason: 'mode_off', clientIp: normalizedIp };
  }

  if (mode === 'blacklist') {
    if (normalizedIp && _accessControl.blacklist.has(normalizedIp)) {
      return { allowed: false, reason: 'blacklist_hit', clientIp: normalizedIp };
    }
    return { allowed: true, reason: 'blacklist_pass', clientIp: normalizedIp };
  }

  if (mode === 'whitelist') {
    if (normalizedIp && _accessControl.whitelist.has(normalizedIp)) {
      return { allowed: true, reason: 'whitelist_hit', clientIp: normalizedIp };
    }
    return { allowed: false, reason: 'whitelist_miss', clientIp: normalizedIp };
  }

  return { allowed: true, reason: 'unknown_mode', clientIp: normalizedIp };
}

function _evictOldestAccessDevice() {
  let oldestKey = '';
  let oldestSeen = Number.MAX_SAFE_INTEGER;
  for (const [ip, item] of _accessDevices.entries()) {
    const ts = Number(item.lastSeenAtMs || 0);
    if (ts < oldestSeen) {
      oldestSeen = ts;
      oldestKey = ip;
    }
  }
  if (oldestKey) {
    _accessDevices.delete(oldestKey);
  }
}

function _recordDeviceAccess({ clientIp, method, pathname, allowed, reason, userAgent }) {
  const ip = _normalizeClientIp(clientIp);
  if (!ip || _isLoopbackIp(ip)) return;

  const now = Date.now();
  let item = _accessDevices.get(ip);
  if (!item) {
    if (_accessDevices.size >= _ACCESS_TRACK_MAX_DEVICES) {
      _evictOldestAccessDevice();
    }
    item = {
      ip,
      firstSeenAtMs: now,
      lastSeenAtMs: now,
      totalRequests: 0,
      allowedRequests: 0,
      blockedRequests: 0,
      lastMethod: 'GET',
      lastPath: '/',
      lastReason: '',
      lastUserAgent: '',
    };
    _accessDevices.set(ip, item);
  }

  item.lastSeenAtMs = now;
  item.totalRequests += 1;
  if (allowed) item.allowedRequests += 1;
  else item.blockedRequests += 1;
  item.lastMethod = String(method || 'GET').toUpperCase();
  item.lastPath = String(pathname || '/');
  item.lastReason = String(reason || '');
  if (userAgent) {
    item.lastUserAgent = String(userAgent).slice(0, 220);
  }

  if (allowed) _accessAllowCount += 1;
  else _accessBlockCount += 1;
}

function _buildAccessDevicesPayload() {
  const mode = _accessControl.mode || 'off';
  const whitelist = _accessControl.whitelist || new Set();
  const blacklist = _accessControl.blacklist || new Set();

  const list = [];
  for (const item of _accessDevices.values()) {
    const ip = item.ip;
    const inWhitelist = whitelist.has(ip);
    const inBlacklist = blacklist.has(ip);
    const effectiveBlocked = !_isLoopbackIp(ip) && (
      (mode === 'blacklist' && inBlacklist) ||
      (mode === 'whitelist' && !inWhitelist)
    );
    list.push({
      ip,
      firstSeenAtMs: item.firstSeenAtMs,
      firstSeenAt: new Date(item.firstSeenAtMs).toISOString(),
      lastSeenAtMs: item.lastSeenAtMs,
      lastSeenAt: new Date(item.lastSeenAtMs).toISOString(),
      totalRequests: item.totalRequests,
      allowedRequests: item.allowedRequests,
      blockedRequests: item.blockedRequests,
      lastMethod: item.lastMethod,
      lastPath: item.lastPath,
      lastReason: item.lastReason,
      lastUserAgent: item.lastUserAgent,
      inWhitelist,
      inBlacklist,
      effectiveBlocked,
    });
  }
  list.sort((a, b) => b.lastSeenAtMs - a.lastSeenAtMs);
  return list;
}

function _buildAccessControlPayload() {
  const snapshot = _snapshotAccessControl();
  return {
    success: true,
    config: {
      mode: snapshot.mode,
      whitelist: snapshot.whitelist,
      blacklist: snapshot.blacklist,
      updatedAtMs: snapshot.updatedAtMs,
      updatedAt: snapshot.updatedAt,
      file: ACCESS_CONTROL_FILE,
    },
    stats: {
      trackedDevices: _accessDevices.size,
      whitelistCount: snapshot.whitelist.length,
      blacklistCount: snapshot.blacklist.length,
      totalAllowedRequests: _accessAllowCount,
      totalBlockedRequests: _accessBlockCount,
    },
    devices: _buildAccessDevicesPayload(),
  };
}

function _sendJson(res, statusCode, payload) {
  res.statusCode = statusCode;
  res.setHeader('content-type', 'application/json; charset=utf-8');
  res.end(JSON.stringify(payload));
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

async function _handleAccessControlEndpoint(req, res, urlObj, clientIp) {
  if (!_isAdminAuthorized(req, urlObj, clientIp)) {
    _sendJson(res, 403, {
      success: false,
      errorCode: 403,
      errorMessage: 'Forbidden',
    });
    return true;
  }

  const method = String(req.method || 'GET').toUpperCase();
  if (method === 'GET') {
    _sendJson(res, 200, _buildAccessControlPayload());
    return true;
  }
  if (!['POST', 'PUT', 'PATCH'].includes(method)) {
    _sendJson(res, 405, {
      success: false,
      errorCode: 405,
      errorMessage: 'Method Not Allowed',
    });
    return true;
  }

  try {
    const body = await _readJsonBody(req);
    if (body && body.clearDevices === true) {
      _accessDevices.clear();
    }

    const current = _snapshotAccessControl();
    const next = _sanitizeAccessControl(body, current, true);
    if (body && body.clearRules === true) {
      next.whitelist = [];
      next.blacklist = [];
    }
    next.updatedAtMs = Date.now();
    _applyAccessControl(next);

    if (!_persistAccessControl()) {
      throw new Error('保存访问控制配置失败');
    }
    _sendJson(res, 200, _buildAccessControlPayload());
  } catch (e) {
    _sendJson(res, 400, {
      success: false,
      errorCode: 400,
      errorMessage: e?.message || '参数错误',
    });
  }
  return true;
}

function getClientIp(req) {
  const xff = req.headers['x-forwarded-for'];
  if (typeof xff === 'string' && xff) return _normalizeClientIp(xff);
  const xrip = req.headers['x-real-ip'];
  if (typeof xrip === 'string' && xrip) return _normalizeClientIp(xrip);
  return _normalizeClientIp(req.socket?.remoteAddress || '');
}

function _isLoopbackIp(ip) {
  const raw = _normalizeClientIp(ip);
  if (!raw) return false;
  if (raw === '::1' || raw === '0:0:0:0:0:0:0:1') return true;
  if (raw.startsWith('127.')) return true;
  if (raw.startsWith('::ffff:127.')) return true;
  return false;
}

function _isAdminAuthorized(req, urlObj, clientIp) {
  if (_isLoopbackIp(clientIp)) return true;

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

function bufferFromArrayBuffer(ab) {
  return Buffer.from(ab);
}

async function toNodeResponse(webResponse, res) {
  res.statusCode = webResponse.status;

  // Copy headers
  // Handle set-cookie specially
  if (typeof webResponse.headers.getSetCookie === 'function') {
    const cookies = webResponse.headers.getSetCookie();
    if (cookies && cookies.length) res.setHeader('set-cookie', cookies);
  } else {
    const sc = webResponse.headers.get('set-cookie');
    if (sc) res.setHeader('set-cookie', sc);
  }

  webResponse.headers.forEach((value, key) => {
    if (key.toLowerCase() === 'set-cookie') return;
    try { res.setHeader(key, value); } catch {}
  });

  const ab = await webResponse.arrayBuffer();
  res.end(bufferFromArrayBuffer(ab));
}

async function readRequestBody(req) {
  return await new Promise((resolve, reject) => {
    const chunks = [];
    req.on('data', c => chunks.push(c));
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

// ------------------------------------------------------------
// Danmu XML one-shot overrides (used by Android push UI)
// ------------------------------------------------------------

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

function _normalizeNumber(n, digits = 3) {
  if (!Number.isFinite(n)) return null;
  const fixed = Number(n).toFixed(digits);
  return fixed.replace(/\.?0+$/, '');
}

function _applyDanmuXmlOverrides(xml, { offsetSec = 0, fontSize = null } = {}) {
  const off = Number(offsetSec);
  const hasOffset = Number.isFinite(off) && Math.abs(off) > 1e-6;

  let fsInt = null;
  if (fontSize !== null && typeof fontSize !== 'undefined') {
    const tmp = Number.parseInt(String(fontSize), 10);
    if (Number.isFinite(tmp) && tmp > 0) fsInt = tmp;
  }
  const hasFont = fsInt !== null;

  if (!hasOffset && !hasFont) return xml;
  if (typeof xml !== 'string' || !xml.includes('<d')) return xml;

  // Only rewrite the p="..." attribute inside <d ...> nodes.
  // B站弹幕 XML: p="time,mode,size,color,ts,pool,hash,rowid"
  return xml.replace(/(<d[^>]*?\sp=")([^"]+)(")/g, (m, prefix, pVal, suffix) => {
    const parts = String(pVal).split(',');
    if (parts.length < 3) return m;

    if (hasOffset) {
      const t = Number(parts[0]);
      if (Number.isFinite(t)) {
        let nt = t + off;
        if (nt < 0) nt = 0;
        const norm = _normalizeNumber(nt);
        if (norm !== null) parts[0] = norm;
      }
    }

    if (hasFont) {
      parts[2] = String(fsInt);
    }

    return prefix + parts.join(',') + suffix;
  });
}

function createMainServer() {
  const server = http.createServer(async (req, res) => {
    try {
      const host = req.headers.host || `127.0.0.1:${PORT}`;
      const fullUrl = new URL(req.url || '/', `http://${host}`);
      const rawPathname = fullUrl.pathname || '/';
      const strippedPathname = _stripTokenPrefix(rawPathname);
      const clientIp = getClientIp(req);
      const method = (req.method || 'GET').toUpperCase();

      // Update simple metrics (for diagnostics UI)
      try {
        METRICS.requestCount++;
        METRICS.lastRequestAt = Date.now();
        METRICS.lastRequestPath = fullUrl.pathname || '';
        METRICS.lastClientIp = clientIp || '';
      } catch {}

      maybeReloadConfigOnTraffic();
      _maybeReloadCoreOnTraffic();

      // Health endpoint (used by the Android app for status/diagnostics)
      if (strippedPathname === '/__health') {
        const variantKey = _getVariant();
        const info = _VARIANT_MAP[variantKey] || _VARIANT_MAP.stable;
        const payload = {
          ok: true,
          time: new Date().toISOString(),
          pid: process.pid,
          node: process.version,
          uptimeSec: Math.floor(process.uptime()),
          host: HOST,
          ports: { main: PORT, proxy: PROXY_PORT },
          variant: variantKey,
          variantLabel: info.label,
          requestCount: METRICS.requestCount,
          lastRequestAt: METRICS.lastRequestAt,
          lastRequestPath: METRICS.lastRequestPath,
          lastClientIp: METRICS.lastClientIp,
          envFileMtimeMs: _lastEnvMtimeMs,
          logFile: LOG_FILE,
          logLevel: String(process.env.LOG_LEVEL || 'info'),
          accessControl: {
            mode: _accessControl.mode || 'off',
            whitelistCount: _accessControl.whitelist.size,
            blacklistCount: _accessControl.blacklist.size,
            blockedRequests: _accessBlockCount,
          },
        };
        res.statusCode = 200;
        res.setHeader('content-type', 'application/json; charset=utf-8');
        res.end(JSON.stringify(payload));
        return;
      }

      // Android app uses this endpoint to request a graceful shutdown.
      if (strippedPathname === '/__shutdown') {
        if (!_isAdminAuthorized(req, fullUrl, clientIp)) {
          res.statusCode = 403;
          res.setHeader('content-type', 'text/plain; charset=utf-8');
          res.end('Forbidden');
          return;
        }
        res.statusCode = 200;
        res.setHeader('content-type', 'text/plain; charset=utf-8');
        res.end('OK');
        // Delay a tick so the response has a chance to flush.
        setTimeout(() => {
          try { shutdownFn && shutdownFn(); } catch {}
        }, 10);
        return;
      }

      if (strippedPathname === '/__access-control') {
        await _handleAccessControlEndpoint(req, res, fullUrl, clientIp);
        return;
      }

      const accessResult = _evaluateClientAccess(clientIp, rawPathname);
      const userAgent = String(req.headers['user-agent'] || '').trim();
      if (!accessResult.allowed) {
        _recordDeviceAccess({
          clientIp: accessResult.clientIp,
          method,
          pathname: strippedPathname,
          allowed: false,
          reason: accessResult.reason,
          userAgent,
        });
        _sendJson(res, 403, {
          success: false,
          errorCode: 403,
          errorMessage: '设备访问受限',
          mode: _accessControl.mode || 'off',
          clientIp: accessResult.clientIp,
          reason: accessResult.reason,
        });
        return;
      }
      if (!_isInternalAccessPath(fullUrl.pathname || '/')) {
        _recordDeviceAccess({
          clientIp: accessResult.clientIp || clientIp,
          method,
          pathname: strippedPathname,
          allowed: true,
          reason: accessResult.reason,
          userAgent,
        });
      }

      // Build headers (Node gives lower-cased keys)
      const headers = {};
      for (const [k, v] of Object.entries(req.headers)) {
        if (typeof v === 'undefined') continue;
        headers[k] = v;
      }

      let body;
      if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
        const buf = await readRequestBody(req);
        body = buf.length ? buf : undefined;
      }

      // ------------------------------------------------------------
      // Optional one-shot overrides for danmu XML output.
      // Used by Android "Push danmu" UI to fix drift and temporarily override font size
      // without mutating core env vars.
      //
      // Query params:
      //  - offset (seconds, can be negative)
      //  - offsetMs (milliseconds)
      //  - fontSize (int)
      // ------------------------------------------------------------
      const offsetMsRaw = fullUrl.searchParams.get('offsetMs') || fullUrl.searchParams.get('offset_ms');
      const offsetRaw = fullUrl.searchParams.get('offset') || fullUrl.searchParams.get('danmu_offset');
      const fontRaw = fullUrl.searchParams.get('fontSize') || fullUrl.searchParams.get('font_size') || fullUrl.searchParams.get('fontsize');

      let offsetSec = 0;
      if (offsetMsRaw && String(offsetMsRaw).trim() !== '') {
        const ms = Number.parseFloat(String(offsetMsRaw));
        if (Number.isFinite(ms)) offsetSec = ms / 1000.0;
      } else if (offsetRaw && String(offsetRaw).trim() !== '') {
        const s = Number.parseFloat(String(offsetRaw));
        if (Number.isFinite(s)) offsetSec = s;
      }

      let fontSize = null;
      if (fontRaw && String(fontRaw).trim() !== '') {
        const fs = Number.parseInt(String(fontRaw), 10);
        if (Number.isFinite(fs) && fs > 0) fontSize = fs;
      }

      const wantsOverride = strippedPathname.startsWith('/api/v2/comment/') &&
        (Math.abs(offsetSec) > 1e-6 || fontSize !== null);

      if (wantsOverride) {
        // Hand a clean URL to core (avoid unknown param side-effects), then post-process output.
        const cleanUrl = new URL(fullUrl.toString());
        for (const k of ['offset', 'danmu_offset', 'offsetMs', 'offset_ms', 'fontSize', 'font_size', 'fontsize']) {
          cleanUrl.searchParams.delete(k);
        }

        const coreReq = new Request(cleanUrl.toString(), {
          method,
          headers,
          body,
        });

        const coreRes = await handleRequest(coreReq, process.env, 'node', clientIp);
        const ct = String(coreRes.headers.get('content-type') || '').toLowerCase();
        const rawText = await coreRes.text();

        let outText = rawText;
        if (ct.includes('xml') || rawText.trimStart().startsWith('<')) {
          outText = _applyDanmuXmlOverrides(rawText, { offsetSec, fontSize });
        }

        const outHeaders = new Headers(coreRes.headers);
        // Body has been rewritten; remove content-length/content-encoding to avoid mismatch.
        outHeaders.delete('content-length');
        outHeaders.delete('content-encoding');

        const outRes = new Response(outText, {
          status: coreRes.status,
          headers: outHeaders,
        });

        await toNodeResponse(outRes, res);
        return;
      }

      const webReq = new Request(fullUrl.toString(), {
        method,
        headers,
        body,
      });

      const webRes = await handleRequest(webReq, process.env, 'node', clientIp);
      await toNodeResponse(webRes, res);
    } catch (e) {
      res.statusCode = 500;
      res.setHeader('content-type', 'text/plain; charset=utf-8');
      res.end(`danmu_api server error: ${e?.stack || e}`);
    }
  });

  return server;
}

// Proxy server mostly copied from upstream server.js, but rewritten for ESM
function createProxyServer() {
  const server = http.createServer(async (req, res) => {
    if (!req.url) {
      res.writeHead(400);
      res.end('Invalid request');
      return;
    }

    try {
      const urlObj = new URL(req.url, `http://${req.headers.host || '127.0.0.1'}`);
      const proxyPath = _stripTokenPrefix(urlObj.pathname || '/');
      const clientIp = getClientIp(req);
      const method = (req.method || 'GET').toUpperCase();
      const userAgent = String(req.headers['user-agent'] || '').trim();

      maybeReloadConfigOnTraffic();
      _maybeReloadCoreOnTraffic();

      if (proxyPath !== '/proxy') {
        res.writeHead(404, { 'Content-Type': 'text/plain' });
        res.end('Not found');
        return;
      }

      const accessResult = _evaluateClientAccess(clientIp, proxyPath);
      if (!accessResult.allowed) {
        _recordDeviceAccess({
          clientIp: accessResult.clientIp,
          method,
          pathname: proxyPath,
          allowed: false,
          reason: accessResult.reason,
          userAgent,
        });
        res.writeHead(403, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({
          success: false,
          errorCode: 403,
          errorMessage: '设备访问受限',
          mode: _accessControl.mode || 'off',
          clientIp: accessResult.clientIp,
          reason: accessResult.reason,
        }));
        return;
      }
      _recordDeviceAccess({
        clientIp: accessResult.clientIp || clientIp,
        method,
        pathname: proxyPath,
        allowed: true,
        reason: accessResult.reason,
        userAgent,
      });

      if (!_isAdminAuthorized(req, urlObj, clientIp)) {
        res.writeHead(403, { 'Content-Type': 'text/plain; charset=utf-8' });
        res.end('Forbidden');
        return;
      }

      const targetUrl = urlObj.searchParams.get('url');
      if (!targetUrl) {
        res.writeHead(400, { 'Content-Type': 'text/plain' });
        res.end('Missing url parameter');
        return;
      }

      // Parse PROXY_URL:
      // - If it ends with '/', treat as reverse proxy base and append encoded targetUrl
      // - Otherwise treat as forward proxy (http/https proxy)
      const proxyConfig = process.env.PROXY_URL || '';
      let reverseProxyUrl = null;
      let proxyAgent = null;

      if (proxyConfig) {
        if (proxyConfig.endsWith('/')) {
          reverseProxyUrl = proxyConfig;
        } else {
          try {
            const HttpsProxyAgent = await getHttpsProxyAgentCtor();
            if (HttpsProxyAgent) {
              proxyAgent = new HttpsProxyAgent(proxyConfig);
            } else {
              log('PROXY_URL looks like a forward proxy, but https-proxy-agent is unavailable; forward proxy disabled');
            }
          } catch (e) {
            log('Invalid PROXY_URL for proxy agent:', e?.message || e);
          }
        }
      }

      // Build final URL (reverse proxy preferred)
      const finalUrl = reverseProxyUrl
        ? `${reverseProxyUrl}${encodeURIComponent(targetUrl)}`
        : targetUrl;

      const targetUrlObj = new URL(finalUrl);
      const protocol = targetUrlObj.protocol === 'https:' ? https : http;

      const requestOptions = {
        hostname: targetUrlObj.hostname,
        port: targetUrlObj.port || (targetUrlObj.protocol === 'https:' ? 443 : 80),
        path: targetUrlObj.pathname + targetUrlObj.search,
        method: req.method || 'GET',
        headers: {
          ...req.headers,
          host: targetUrlObj.host,
        },
      };

      if (proxyAgent && !reverseProxyUrl) requestOptions.agent = proxyAgent;

      // Read body if needed
      let bodyBuf = null;
      if (method !== 'GET') {
        bodyBuf = await readRequestBody(req);
      }

      const proxyReq = protocol.request(requestOptions, (proxyRes) => {
        // Copy status + headers
        res.writeHead(proxyRes.statusCode || 502, proxyRes.headers);

        proxyRes.on('data', (chunk) => res.write(chunk));
        proxyRes.on('end', () => res.end());
      });

      proxyReq.on('error', (err) => {
        res.writeHead(500, { 'Content-Type': 'text/plain' });
        res.end(`Proxy error: ${err.message}`);
      });

      if (bodyBuf && bodyBuf.length) proxyReq.write(bodyBuf);
      proxyReq.end();
    } catch (error) {
      res.writeHead(500, { 'Content-Type': 'text/plain' });
      res.end(`Error: ${error.message}`);
    }
  });

  return server;
}

async function main() {
  ensureDirs();
  loadConfigOnce();
  // .env 读取后再解析监听地址，确保 App 设置端口生效。
  _refreshListenConfigFromEnv();
  _refreshRuntimeFlags();
  // Enable file logging after .env has been loaded (LOG_LEVEL takes effect)
  setupFileLogging();
  await _loadHandleRequestForVariant();
  _syncEnvToWorker();
  watchConfigs();

  const mainServer = createMainServer();
  const proxyServer = createProxyServer();

  await new Promise((resolve, reject) => {
    mainServer.listen(PORT, HOST, () => {
      log(`Main server listening on http://${HOST}:${PORT}`);
      resolve();
    });
    mainServer.on('error', reject);
  });

  await new Promise((resolve, reject) => {
    proxyServer.listen(PROXY_PORT, HOST, () => {
      log(`Proxy server listening on http://${HOST}:${PROXY_PORT}/proxy?url=...`);
      resolve();
    });
    proxyServer.on('error', reject);
  });

  let shuttingDown = false;
  const shutdown = () => {
    if (shuttingDown) return;
    shuttingDown = true;
    log('Shutting down ...');
    try { mainServer.close(); } catch {}
    try { proxyServer.close(); } catch {}
    // For this App we WANT Node to exit, so the Android Service can stop cleanly.
    setTimeout(() => {
      try {
        process.exit(0);
      } catch {
        process.exitCode = 0;
      }
    }, 350);
  };

  // Expose to /__shutdown endpoint
  shutdownFn = shutdown;

  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);
}

main().catch((e) => {
  log('Fatal error:', e?.stack || e);
  // 保持进程存活一小会儿以便 Android 端能捕获到日志 (如果直接 exit(1)，App 端可能只收到 "Service stopped" 而没有日志)
  setTimeout(() => {
      process.exit(1);
  }, 2000);
});
