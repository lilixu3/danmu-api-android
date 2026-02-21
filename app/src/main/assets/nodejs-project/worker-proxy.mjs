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

    const res = await handleRequest(req, process.env, 'node', clientIp);
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
