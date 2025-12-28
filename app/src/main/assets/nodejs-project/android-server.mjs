import http from 'http';
import https from 'https';
import fs from 'fs';
import path from 'path';
import { URL, fileURLToPath } from 'url';
import yaml from 'js-yaml';
import { HttpsProxyAgent } from 'https-proxy-agent';

import { handleRequest } from './danmu_api/worker.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Persistent home (config/logs are stored outside the module folder)
const HOME = process.env.DANMU_API_HOME || __dirname;
const CONFIG_DIR = path.join(HOME, 'config');
const LOG_DIR = path.join(HOME, 'logs');

const HOST = process.env.DANMU_API_HOST || '0.0.0.0';
const PORT = Number(process.env.DANMU_API_PORT || 9321);
const PROXY_PORT = Number(process.env.DANMU_API_PROXY_PORT || 9322);

// Will be set after servers are created (used by the Android app to stop Node gracefully)
let shutdownFn = null;

function log(...args) {
  console.log('[danmu_api]', ...args);
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

  const envFile = path.join(CONFIG_DIR, '.env');
  const yamlFile = path.join(CONFIG_DIR, 'config.yaml');

  // 1) YAML first (lower priority)
  if (fs.existsSync(yamlFile)) {
    try {
      const y = fs.readFileSync(yamlFile, 'utf-8');
      const parsed = yaml.load(y) || {};
      const flat = flattenObject(parsed);
      applyEnv(flat, { override: true });
      log('Loaded config.yaml:', yamlFile);
    } catch (e) {
      log('Failed to load config.yaml:', e?.message || e);
    }
  } else {
    log('config.yaml not found, skipping:', yamlFile);
  }

  // 2) .env second (higher priority)
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
}

function watchConfigs() {
  const envFile = path.join(CONFIG_DIR, '.env');
  const yamlFile = path.join(CONFIG_DIR, 'config.yaml');

  let timer = null;
  const scheduleReload = () => {
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => {
      log('Config changed, reloading ...');
      loadConfigOnce();
    }, 500);
  };

  // fs.watch might not be available for some FS; try/catch
  for (const f of [envFile, yamlFile]) {
    try {
      if (!fs.existsSync(f)) continue;
      fs.watch(f, { persistent: false }, () => scheduleReload());
    } catch {
      // fallback
      try {
        fs.watchFile(f, { interval: 2000, persistent: false }, () => scheduleReload());
      } catch {}
    }
  }
}

function getClientIp(req) {
  const xff = req.headers['x-forwarded-for'];
  if (typeof xff === 'string' && xff) return xff.split(',')[0].trim();
  const xrip = req.headers['x-real-ip'];
  if (typeof xrip === 'string' && xrip) return xrip.trim();
  return req.socket?.remoteAddress || '';
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

function createMainServer() {
  const server = http.createServer(async (req, res) => {
    try {
      const host = req.headers.host || `127.0.0.1:${PORT}`;
      const fullUrl = new URL(req.url || '/', `http://${host}`);

      // Android app uses this endpoint to request a graceful shutdown.
      if (fullUrl.pathname === '/__shutdown') {
        res.statusCode = 200;
        res.setHeader('content-type', 'text/plain; charset=utf-8');
        res.end('OK');
        // Delay a tick so the response has a chance to flush.
        setTimeout(() => {
          try { shutdownFn && shutdownFn(); } catch {}
        }, 10);
        return;
      }

      // Build headers (Node gives lower-cased keys)
      const headers = {};
      for (const [k, v] of Object.entries(req.headers)) {
        if (typeof v === 'undefined') continue;
        headers[k] = v;
      }

      const method = (req.method || 'GET').toUpperCase();
      let body;
      if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
        const buf = await readRequestBody(req);
        body = buf.length ? buf : undefined;
      }

      const webReq = new Request(fullUrl.toString(), {
        method,
        headers,
        body,
      });

      const clientIp = getClientIp(req);
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

      if (urlObj.pathname !== '/proxy') {
        res.writeHead(404, { 'Content-Type': 'text/plain' });
        res.end('Not found');
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
            proxyAgent = new HttpsProxyAgent(proxyConfig);
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
      if ((req.method || 'GET').toUpperCase() !== 'GET') {
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
  // 避免直接退出导致宿主 App 闪退
  process.exitCode = 1;
});
