import { parentPort, workerData } from 'node:worker_threads';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import util from 'node:util';

if (!parentPort) {
  throw new Error('worker missing parentPort');
}

const _origConsole = {
  log: console.log.bind(console),
  info: (console.info || console.log).bind(console),
  warn: (console.warn || console.log).bind(console),
  error: (console.error || console.log).bind(console),
  debug: (console.debug || console.log).bind(console),
};

function _sendLog(level, args) {
  let message = '';
  try {
    message = util.format(...args);
  } catch {
    try {
      message = args.map((v) => String(v)).join(' ');
    } catch {
      message = '';
    }
  }
  try {
    parentPort.postMessage({ type: 'log', level, message });
  } catch {
    // 如果通信失败，退回本地输出，避免吞日志
    const fn = level === 'error'
      ? _origConsole.error
      : level === 'warn'
        ? _origConsole.warn
        : level === 'debug'
          ? _origConsole.debug
          : _origConsole.log;
    try { fn(message); } catch {}
  }
}

console.log = (...args) => _sendLog('info', args);
console.info = (...args) => _sendLog('info', args);
console.warn = (...args) => _sendLog('warn', args);
console.error = (...args) => _sendLog('error', args);
console.debug = (...args) => _sendLog('debug', args);

const data = workerData || {};
const variantDir = String(data.variantDir || '').trim();
const projectDir = String(data.projectDir || '').trim();
const initialEnv = data.env && typeof data.env === 'object' ? data.env : {};

function applyEnvSnapshot(snapshot) {
  const next = snapshot && typeof snapshot === 'object' ? snapshot : {};
  for (const k of Object.keys(process.env)) {
    if (!(k in next)) delete process.env[k];
  }
  for (const [k, v] of Object.entries(next)) {
    process.env[k] = String(v);
  }
}

applyEnvSnapshot(initialEnv);

let handleRequest = null;
let coreGlobals = null;

const QUIET_CORE_LOG_PATH_RE = /(^|\/)api\/(?:logs|reqrecords|cache\/animes)(?=(["'\s?#]|$))/i;

function _normalizeClientIp(rawIp) {
  let value = String(rawIp || '').trim().toLowerCase();
  if (!value) return '';
  if (value.includes(',')) value = value.split(',')[0].trim();
  if (value.startsWith('[')) {
    const closeIdx = value.indexOf(']');
    if (closeIdx > 0) value = value.substring(1, closeIdx);
  }
  const zoneIdx = value.indexOf('%');
  if (zoneIdx > 0) value = value.substring(0, zoneIdx);
  if (value.startsWith('::ffff:')) value = value.substring(7);
  return value;
}

function _isLoopbackIp(ip) {
  const value = _normalizeClientIp(ip);
  return value === '::1' || value === '0:0:0:0:0:0:0:1' || value.startsWith('127.');
}

function _stripLikelyTokenPrefix(pathname) {
  const parts = String(pathname || '/').split('/').filter(Boolean);
  if (parts.length > 1 && parts[0] !== 'api') {
    return '/' + parts.slice(1).join('/');
  }
  return String(pathname || '/');
}

function _isQuietCoreLogRequest(url, clientIp) {
  if (!_isLoopbackIp(clientIp)) return false;
  try {
    const pathname = _stripLikelyTokenPrefix(new URL(String(url || '')).pathname || '/');
    return QUIET_CORE_LOG_PATH_RE.test(pathname);
  } catch {
    return false;
  }
}

function _logMessage(entry) {
  return String(entry && typeof entry === 'object' ? (entry.message ?? '') : entry ?? '');
}

function _referencesQuietCorePath(message) {
  return QUIET_CORE_LOG_PATH_RE.test(String(message || ''));
}

function _isQuietRequestUrl(entry) {
  const message = _logMessage(entry);
  return /\[Server\]\s+request url:/i.test(message) && _referencesQuietCorePath(message);
}

function _isQuietRequestPath(entry) {
  const message = _logMessage(entry);
  return /\[Server\]\s+request path:/i.test(message) && _referencesQuietCorePath(message);
}

function _isServerClientIp(entry) {
  return /\[Server\]\s+client ip:/i.test(_logMessage(entry));
}

function _isQuietSummary(entry) {
  const message = _logMessage(entry);
  return /\[Server\]/i.test(message) &&
    _referencesQuietCorePath(message) &&
    !/request url:/i.test(message) &&
    !/request path:/i.test(message);
}

function _isLocalCacheInitInfo(entry) {
  const message = _logMessage(entry);
  return /\[Cache\]\s+getLocalCaches start\./i.test(message) ||
    /\[Cache\]\s+Restored lastSelectMap from local cache/i.test(message) ||
    /\[Cache\]\s+getLocalCaches completed successfully\./i.test(message);
}

function _quietGroupSize(buffer, index) {
  if (index + 3 > buffer.length) return 0;
  if (!_isQuietRequestUrl(buffer[index]) ||
      !_isQuietRequestPath(buffer[index + 1]) ||
      !_isServerClientIp(buffer[index + 2])) {
    return 0;
  }
  return index + 3 < buffer.length && _isQuietSummary(buffer[index + 3]) ? 4 : 3;
}

function _quietLogScanStartHint() {
  const buffer = coreGlobals?.logBuffer;
  if (!Array.isArray(buffer)) return 0;
  return Math.max(0, buffer.length - 8);
}

function _removeQuietCoreLogNoise(startHint = 0) {
  const buffer = coreGlobals?.logBuffer;
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
    coreGlobals.logBuffer = head.concat(out);
  }
}

async function loadWorker() {
  if (!variantDir || !projectDir) {
    throw new Error('worker missing paths');
  }
  const workerPath = path.join(projectDir, variantDir, 'worker.js');
  const workerUrl = pathToFileURL(workerPath).href;
  const mod = await import(workerUrl);
  if (!mod?.handleRequest) {
    throw new Error('worker.js missing handleRequest export');
  }
  handleRequest = mod.handleRequest;
  for (const globalsRel of ['configs/globals.js', 'config/globals.js']) {
    try {
      const globalsUrl = pathToFileURL(path.join(projectDir, variantDir, globalsRel)).href;
      const globalsMod = await import(globalsUrl);
      coreGlobals = globalsMod?.globals || globalsMod?.Globals || null;
      if (coreGlobals) break;
    } catch {
      // try next layout
    }
  }
}

await loadWorker();
parentPort.postMessage({ type: 'ready' });

parentPort.on('message', async (msg) => {
  if (!msg || typeof msg !== 'object') return;

  if (msg.type === 'setEnv') {
    applyEnvSnapshot(msg.env || {});
    return;
  }

  if (msg.type !== 'request') return;

  const id = msg.id;
  try {
    const url = String(msg.url || '');
    const method = String(msg.method || 'GET').toUpperCase();
    const headersArr = Array.isArray(msg.headers) ? msg.headers : [];
    const headers = new Headers();
    for (const pair of headersArr) {
      if (!pair || pair.length < 2) continue;
      try { headers.append(String(pair[0]), String(pair[1])); } catch {}
    }

    const body = msg.body ? Buffer.from(msg.body) : undefined;
    const req = new Request(url, { method, headers, body });
    const clientIp = String(msg.clientIp || '');

    const shouldQuietCoreLogs = _isQuietCoreLogRequest(url, clientIp);
    const quietLogStart = shouldQuietCoreLogs ? _quietLogScanStartHint() : 0;
    let res;
    try {
      res = await handleRequest(req, process.env, 'node', clientIp);
    } finally {
      if (shouldQuietCoreLogs) {
        _removeQuietCoreLogNoise(quietLogStart);
      }
    }
    const respHeaders = Array.from(res.headers.entries());
    let setCookie = [];
    try {
      if (typeof res.headers.getSetCookie === 'function') {
        setCookie = res.headers.getSetCookie() || [];
      } else {
        const sc = res.headers.get('set-cookie');
        if (sc) setCookie = [sc];
      }
    } catch {}

    const ab = await res.arrayBuffer();
    const outBody = ab && ab.byteLength ? Buffer.from(ab) : null;
    parentPort.postMessage({
      type: 'response',
      id,
      status: res.status || 200,
      headers: respHeaders,
      setCookie,
      body: outBody,
    });
  } catch (e) {
    parentPort.postMessage({
      type: 'error',
      id,
      error: e?.stack || e?.message || String(e),
    });
  }
});
