'use strict';

const http = require('node:http');

function parseForwardProxy(proxyUrl) {
  const parsed = new URL(proxyUrl);
  return {
    type: parsed.protocol.slice(0, -1),
    host: parsed.hostname,
    port: Number(parsed.port || (parsed.protocol === 'https:' ? 443 : 80)),
    username: decodeURIComponent(parsed.username),
    password: decodeURIComponent(parsed.password),
  };
}

function createProxyHelper(options) {
  const readClientIp = options.readClientIp;
  const stripTokenPrefix = options.stripTokenPrefix;
  const isAdminAuthorized = options.isAdminAuthorized;
  const accessControl = options.accessControl;
  const readRequestBody = options.readRequestBody;
  const coreProxyConfig = options.coreProxyConfig;
  const requestThroughProxy = options.requestThroughProxy;
  const getEnv = options.getEnv || (() => process.env);
  const maybeReloadConfigOnTraffic = options.maybeReloadConfigOnTraffic || (() => {});
  const maybeReloadCoreOnTraffic = options.maybeReloadCoreOnTraffic || (() => {});
  const getListenOptions = options.getListenOptions;
  const getListenUrl = options.getListenUrl;
  const getHost = options.getHost || (() => '');
  const getPort = options.getPort;
  const isMainServerListening = options.isMainServerListening || (() => true);
  const isShuttingDown = options.isShuttingDown || (() => false);
  const closeServer = options.closeServer;
  const log = options.log || (() => {});
  const httpModule = options.http || http;

  let server = null;
  let syncPromise = Promise.resolve();

  function hasDemand() {
    return Boolean(coreProxyConfig(getEnv().PROXY_URL).forward);
  }

  function createServer() {
    return httpModule.createServer(async (req, res) => {
      if (!req.url) {
        res.writeHead(400);
        res.end('Invalid request');
        return;
      }

      try {
        const urlObj = new URL(req.url, `http://${req.headers.host || '127.0.0.1'}`);
        const proxyPath = stripTokenPrefix(urlObj.pathname || '/');
        const clientIp = readClientIp(req);
        const method = (req.method || 'GET').toUpperCase();
        const userAgent = String(req.headers['user-agent'] || '').trim();

        maybeReloadConfigOnTraffic();
        maybeReloadCoreOnTraffic();

        if (proxyPath !== '/proxy') {
          res.writeHead(404, { 'Content-Type': 'text/plain' });
          res.end('Not found');
          return;
        }

        const accessResult = accessControl.evaluate(clientIp, proxyPath);
        if (!accessResult.allowed) {
          accessControl.recordDeviceAccess({
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
            mode: accessControl.config().mode,
            clientIp: accessResult.clientIp,
            reason: accessResult.reason,
          }));
          return;
        }
        accessControl.recordDeviceAccess({
          clientIp: accessResult.clientIp || clientIp,
          method,
          pathname: proxyPath,
          allowed: true,
          reason: accessResult.reason,
          userAgent,
        });

        if (!isAdminAuthorized(req, urlObj, clientIp)) {
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

        const proxyUrl = coreProxyConfig(getEnv().PROXY_URL).forward;
        if (!proxyUrl) throw new Error('未配置核心 HTTP 代理');
        let proxyNode;
        try {
          proxyNode = parseForwardProxy(proxyUrl);
        } catch {
          throw new Error('核心 HTTP 代理地址无效，请检查 PROXY_URL');
        }

        const targetUrlObj = new URL(targetUrl);
        if (!['http:', 'https:'].includes(targetUrlObj.protocol)) throw new Error('仅支持 HTTP/HTTPS 目标');

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

        let body = null;
        if (method !== 'GET') body = await readRequestBody(req);

        const proxyReq = requestThroughProxy(targetUrlObj, requestOptions, proxyNode, proxyRes => {
          res.writeHead(proxyRes.statusCode || 502, proxyRes.headers);
          proxyRes.on('data', chunk => res.write(chunk));
          proxyRes.on('end', () => res.end());
        });

        proxyReq.on('error', error => {
          res.writeHead(500, { 'Content-Type': 'text/plain' });
          res.end(`Proxy error: ${error.message}`);
        });

        if (body && body.length) proxyReq.write(body);
        proxyReq.end();
      } catch (error) {
        res.writeHead(500, { 'Content-Type': 'text/plain' });
        res.end(`Error: ${error.message}`);
      }
    });
  }

  async function sync() {
    if (isShuttingDown() || !isMainServerListening()) return;
    if (!hasDemand()) {
      const previous = server;
      server = null;
      await closeServer(previous);
      return;
    }
    if (server?.listening) return;
    const next = createServer();
    server = next;
    try {
      await new Promise((resolve, reject) => {
        next.once('error', reject);
        const port = getPort();
        next.listen(getListenOptions(port), () => {
          log(`Proxy server listening on ${getListenUrl(port, '/proxy?url=...')}`);
          resolve();
        });
      });
    } catch (error) {
      const port = getPort();
      log(`Proxy helper unavailable on ${getHost()}:${port}:`, error.code || 'listen failed');
      await closeServer(next);
      if (server === next) server = null;
    }
  }

  function syncForwardProxy() {
    syncPromise = syncPromise.then(sync, sync);
    return syncPromise;
  }

  function takeServer() {
    const current = server;
    server = null;
    return current;
  }

  return {
    createServer,
    get server() { return server; },
    hasDemand,
    sync: syncForwardProxy,
    takeServer,
  };
}

module.exports = { createProxyHelper, parseForwardProxy };
