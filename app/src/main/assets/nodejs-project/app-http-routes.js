'use strict';

const path = require('node:path');

function buildHeaders(req) {
  const headers = {};
  for (const [key, value] of Object.entries(req.headers || {})) {
    if (typeof value === 'undefined') continue;
    headers[key] = value;
  }
  return headers;
}

async function toNodeResponse(webResponse, res) {
  res.statusCode = webResponse.status;
  if (typeof webResponse.headers.getSetCookie === 'function') {
    const cookies = webResponse.headers.getSetCookie();
    if (cookies && cookies.length) res.setHeader('set-cookie', cookies);
  } else {
    const cookie = webResponse.headers.get('set-cookie');
    if (cookie) res.setHeader('set-cookie', cookie);
  }

  webResponse.headers.forEach((value, key) => {
    if (key.toLowerCase() === 'set-cookie') return;
    try { res.setHeader(key, value); } catch {}
  });

  const body = Buffer.from(await webResponse.arrayBuffer());
  res.end(body);
}

function createHttpRoutes(options) {
  const http = options.http;
  const appManagement = options.appManagement;
  const readClientIp = options.readClientIp;
  const readRequestBody = options.readRequestBody;
  const normalizeClientIp = options.normalizeClientIp;
  const guardSession = options.guardSession;
  const runtimeDataContext = options.runtimeDataContext;
  const configRuntime = options.configRuntime;
  const accessControl = options.accessControl;
  const danmakuCache = options.danmakuCache;
  const workerRuntime = options.workerRuntime;
  const sourceProbe = options.sourceProbe;
  const appOutboundDiagnostics = options.appOutboundDiagnostics;
  const sendJson = options.sendJson;
  const stripTokenPrefix = options.stripTokenPrefix;
  const isAdminAuthorized = options.isAdminAuthorized;
  const getEnvSnapshot = options.getEnvSnapshot;
  const getOutboundSnapshot = options.getOutboundSnapshot;
  const getRequestHandler = options.getRequestHandler;
  const getVariant = options.getVariant;
  const getVariantInfo = options.getVariantInfo;
  const getLoadedCoreDirectory = options.getLoadedCoreDirectory;
  const getLoadedRuntimeVariant = options.getLoadedRuntimeVariant;
  const getRuntimeIdentity = options.getRuntimeIdentity;
  const getHost = options.getHost;
  const getPort = options.getPort;
  const getProxyPort = options.getProxyPort;
  const getCacheProbeDir = options.getCacheProbeDir;
  const checkCacheProbeWritable = options.checkCacheProbeWritable;
  const getMetrics = options.getMetrics;
  const getLogFile = options.getLogFile;
  const getHome = options.getHome;
  const getEnv = options.getEnv || (() => process.env);
  const getProcess = options.getProcess || (() => process);
  const getNetworkFingerprint = options.getNetworkFingerprint || (() => undefined);
  const maybeReloadCoreOnTraffic = options.maybeReloadCoreOnTraffic || (() => {});
  const removeQuietCoreLogNoise = options.removeQuietCoreLogNoise || (() => {});
  const quietLogScanStartHint = options.quietLogScanStartHint || (() => 0);
  const isQuietCoreLogRequest = options.isQuietCoreLogRequest || (() => false);
  const applyDanmuXmlOverrides = options.applyDanmuXmlOverrides;
  const refreshLogLevel = options.refreshLogLevel || (() => {});
  const refreshLogConfig = options.refreshLogConfig || (() => {});
  const syncEnvToWorker = options.syncEnvToWorker || (() => {});
  const requestShutdown = options.requestShutdown || (() => {});
  const envFile = options.envFile;
  const setTimeoutFn = options.setTimeout || setTimeout;
  const now = options.now || Date.now;

  function metricsSnapshot() {
    return typeof getMetrics === 'function' ? getMetrics() : getMetrics;
  }

  async function handleSourceProbe(req, res, fullUrl, method) {
    const clientIp = readClientIp(req);
    if (!isAdminAuthorized(req, fullUrl, normalizeClientIp(req.socket?.remoteAddress || ''))) {
      res.writeHead(403);
      res.end('Forbidden');
      return;
    }
    const info = getVariantInfo(getVariant());
    const directory = getLoadedCoreDirectory() || path.join(options.projectDir, info.dir);
    const env = getEnvSnapshot();
    if (method === 'GET') {
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(JSON.stringify(sourceProbe.catalog(directory, env)));
      return;
    }
    if (method !== 'POST') {
      res.writeHead(405);
      res.end();
      return;
    }

    const controller = new AbortController();
    const disconnect = () => { if (!res.writableEnded) controller.abort(); };
    res.once('close', disconnect);
    try {
      const source = fullUrl.searchParams.get('source');
      const state = workerRuntime.activeState;
      const result = state?.ready
        ? (await workerRuntime.sendWorkerRequest(
          state,
          { type: 'sourceProbe', source },
          20000,
          controller.signal
        )).result
        : await sourceProbe.probeSource(source, {
          directory,
          env,
          snapshot: getOutboundSnapshot(),
          signal: controller.signal,
        });
      if (!res.destroyed) {
        res.writeHead(200, { 'content-type': 'application/json' });
        res.end(JSON.stringify(result));
      }
    } catch {
      if (!res.destroyed) {
        res.writeHead(400, { 'content-type': 'application/json' });
        res.end(JSON.stringify({
          connected: false,
          status: 'failed',
          reason: '来源探测未完成，请检查来源与服务状态',
        }));
      }
    } finally {
      res.removeListener('close', disconnect);
    }
  }

  async function handleOutbound(req, res, fullUrl, method) {
    if (!isAdminAuthorized(req, fullUrl, normalizeClientIp(req.socket?.remoteAddress || ''))) {
      res.writeHead(403);
      res.end('Forbidden');
      return;
    }
    const current = getOutboundSnapshot() || {};
    if (method !== 'POST') {
      const { status, reason, config } = current;
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(JSON.stringify({
        status,
        reason,
        config: config ? { ...config, proxy: undefined } : undefined,
      }));
      return;
    }

    const source = fullUrl.searchParams.get('source');
    if (!appOutboundDiagnostics.diagnosticSources.includes(source)) {
      res.writeHead(400);
      res.end('Invalid source');
      return;
    }
    if (current.status !== 'ready' || !current.config?.enabled || !current.config.sources.includes(source)) {
      res.writeHead(409, { 'content-type': 'application/json' });
      res.end(JSON.stringify({
        ok: false,
        reason: current.reason || '请先启用对应来源并等待组件就绪',
      }));
      return;
    }

    const controller = new AbortController();
    const disconnect = () => { if (!res.writableEnded) controller.abort(); };
    res.once('close', disconnect);
    try {
      const result = await appOutboundDiagnostics.diagnoseSource(
        source,
        current,
        getEnvSnapshot(),
        { signal: controller.signal }
      );
      if (!res.destroyed) {
        const { httpStatus = 200, ...publicResult } = result;
        res.writeHead(httpStatus, { 'content-type': 'application/json' });
        res.end(JSON.stringify(publicResult));
      }
    } catch {
      if (!res.destroyed) {
        res.writeHead(200, { 'content-type': 'application/json' });
        res.end(JSON.stringify({ ok: false, reason: '连接失败或取消' }));
      }
    } finally {
      res.removeListener('close', disconnect);
    }
  }

  function handleHealth(res, dataContext) {
    const variantKey = getLoadedRuntimeVariant() || getVariant();
    const info = getVariantInfo(variantKey);
    const cacheProbeDir = getCacheProbeDir();
    const cacheProbeWritable = checkCacheProbeWritable(cacheProbeDir);
    const runtimeIdentity = getRuntimeIdentity();
    const processRef = getProcess();
    const metrics = metricsSnapshot();
    const payload = {
      ok: true,
      time: new Date(now()).toISOString(),
      pid: processRef.pid,
      node: processRef.version,
      networkRuntimeFingerprint: getNetworkFingerprint(),
      uptimeSec: Math.floor(processRef.uptime()),
      host: getHost(),
      ports: { main: getPort(), proxy: getProxyPort() },
      cwd: processRef.cwd(),
      envHome: String(getEnv().DANMU_API_HOME || ''),
      resolvedHome: getHome(),
      cacheProbeDir,
      cacheProbeWritable,
      variant: variantKey,
      variantLabel: info.label,
      runtimeIdentity: runtimeIdentity || null,
      ...dataContext,
      requestCount: metrics.requestCount,
      lastRequestAt: metrics.lastRequestAt,
      lastRequestPath: metrics.lastRequestPath,
      lastClientIp: metrics.lastClientIp,
      envFileMtimeMs: configRuntime.lastEnvMtimeMs,
      logFile: getLogFile(),
      logLevel: String(getEnv().LOG_LEVEL || 'info'),
      accessControl: {
        mode: accessControl.config().mode,
        whitelistCount: 0,
        blacklistCount: accessControl.config().blacklistCount,
        blockedRequests: accessControl.stats().totalBlockedRequests,
      },
    };
    res.statusCode = 200;
    res.setHeader('content-type', 'application/json; charset=utf-8');
    res.end(JSON.stringify(payload));
  }

  function handleShutdown(req, res, fullUrl, clientIp) {
    if (!isAdminAuthorized(req, fullUrl, clientIp)) {
      res.statusCode = 403;
      res.setHeader('content-type', 'text/plain; charset=utf-8');
      res.end('Forbidden');
      return;
    }
    res.statusCode = 200;
    res.setHeader('content-type', 'text/plain; charset=utf-8');
    res.end('OK');
    setTimeoutFn(() => {
      try { requestShutdown(); } catch {}
    }, 10);
  }

  async function handleManagementConfig(req, res, rawPathname, host) {
    configRuntime.loadConfigOnce();
    const action = appManagement.routeForAdmin(
      rawPathname,
      String(getEnv().ADMIN_TOKEN || '').trim()
    );
    const origin = String(req.headers.origin || '');
    if (!action || !appManagement.isManagementOriginAllowed(origin, host)) {
      sendJson(res, 403, { success: false, message: '需要管理员权限' });
      return;
    }
    try {
      const data = await appManagement.readManagementBody(req);
      appManagement.saveEnvValue(envFile, data.key, data.value, action === 'del');
      configRuntime.loadConfigOnce();
      refreshLogLevel();
      refreshLogConfig();
      syncEnvToWorker();
      sendJson(res, 200, { success: true, message: '配置已保存并热更新' });
    } catch (error) {
      sendJson(res, error?.statusCode || 400, {
        success: false,
        message: '配置保存失败，请检查配置内容与目录权限',
      });
    }
  }

  async function forwardToCore(requestOptions) {
    const {
      req,
      res,
      fullUrl,
      strippedPathname,
      clientIp,
      method,
      headers,
      body,
    } = requestOptions;

    const offsetMsRaw = fullUrl.searchParams.get('offsetMs') || fullUrl.searchParams.get('offset_ms');
    const offsetRaw = fullUrl.searchParams.get('offset') || fullUrl.searchParams.get('danmu_offset');
    const fontRaw = fullUrl.searchParams.get('fontSize') || fullUrl.searchParams.get('font_size') ||
      fullUrl.searchParams.get('fontsize');

    let offsetSec = 0;
    if (offsetMsRaw && String(offsetMsRaw).trim() !== '') {
      const milliseconds = Number.parseFloat(String(offsetMsRaw));
      if (Number.isFinite(milliseconds)) offsetSec = milliseconds / 1000.0;
    } else if (offsetRaw && String(offsetRaw).trim() !== '') {
      const seconds = Number.parseFloat(String(offsetRaw));
      if (Number.isFinite(seconds)) offsetSec = seconds;
    }

    let fontSize = null;
    if (fontRaw && String(fontRaw).trim() !== '') {
      const parsed = Number.parseInt(String(fontRaw), 10);
      if (Number.isFinite(parsed) && parsed > 0) fontSize = parsed;
    }

    const shouldQuietCoreLogs = isQuietCoreLogRequest(fullUrl, clientIp);
    const quietLogStart = shouldQuietCoreLogs ? quietLogScanStartHint() : 0;
    const wantsOverride = strippedPathname.startsWith('/api/v2/comment/') &&
      (Math.abs(offsetSec) > 1e-6 || fontSize !== null);

    const handler = getRequestHandler();
    if (typeof handler !== 'function') {
      throw new Error('core handler not ready');
    }

    let coreResponse;
    if (wantsOverride) {
      const cleanUrl = new URL(fullUrl.toString());
      for (const key of ['offset', 'danmu_offset', 'offsetMs', 'offset_ms', 'fontSize', 'font_size', 'fontsize']) {
        cleanUrl.searchParams.delete(key);
      }
      const coreRequest = new Request(cleanUrl.toString(), { method, headers, body });
      try {
        coreResponse = await handler(coreRequest, getEnvSnapshot(), 'node', clientIp);
      } finally {
        if (shouldQuietCoreLogs) removeQuietCoreLogNoise(quietLogStart);
      }

      const contentType = String(coreResponse.headers.get('content-type') || '').toLowerCase();
      const rawText = await coreResponse.text();
      const outText = contentType.includes('xml') || rawText.trimStart().startsWith('<')
        ? applyDanmuXmlOverrides(rawText, { offsetSec, fontSize })
        : rawText;
      const outHeaders = new Headers(coreResponse.headers);
      outHeaders.delete('content-length');
      outHeaders.delete('content-encoding');
      await toNodeResponse(new Response(outText, {
        status: coreResponse.status,
        headers: outHeaders,
      }), res);
      return;
    }

    const coreRequest = new Request(fullUrl.toString(), { method, headers, body });
    try {
      coreResponse = await handler(coreRequest, getEnvSnapshot(), 'node', clientIp);
    } finally {
      if (shouldQuietCoreLogs) removeQuietCoreLogNoise(quietLogStart);
    }

    const section = fullUrl.searchParams.get('app_section');
    if (method === 'GET' && coreResponse.status === 200 && ['env', 'logs'].includes(section) &&
        String(coreResponse.headers.get('content-type') || '').toLowerCase().includes('text/html')) {
      const html = appManagement.injectWebSection(await coreResponse.text(), section);
      const rewrittenHeaders = new Headers(coreResponse.headers);
      rewrittenHeaders.delete('content-length');
      rewrittenHeaders.delete('content-encoding');
      rewrittenHeaders.set('cache-control', 'no-store');
      coreResponse = new Response(html, { status: coreResponse.status, headers: rewrittenHeaders });
    }
    await toNodeResponse(coreResponse, res);
  }

  function createServer() {
    return http.createServer(async (req, res) => {
      try {
        const host = req.headers.host || `127.0.0.1:${getPort()}`;
        const fullUrl = new URL(req.url || '/', `http://${host}`);
        const rawPathname = fullUrl.pathname || '/';
        const strippedPathname = stripTokenPrefix(rawPathname);
        const clientIp = readClientIp(req);
        const method = (req.method || 'GET').toUpperCase();

        if (rawPathname === '/.well-known/security.txt') {
          res.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' });
          res.end('Not Found');
          return;
        }

        try {
          const metrics = metricsSnapshot();
          metrics.requestCount++;
          metrics.lastRequestAt = now();
          metrics.lastRequestPath = fullUrl.pathname || '';
          metrics.lastClientIp = clientIp || '';
        } catch {}

        configRuntime.maybeReloadOnTraffic();
        maybeReloadCoreOnTraffic();

        const dataContext = runtimeDataContext();
        if (!guardSession(req, res, dataContext)) return;

        if (strippedPathname === '/__source-probe') {
          await handleSourceProbe(req, res, fullUrl, method);
          return;
        }
        if (strippedPathname === '/__outbound') {
          await handleOutbound(req, res, fullUrl, method);
          return;
        }
        if (strippedPathname === '/__health') {
          handleHealth(res, dataContext);
          return;
        }
        if (strippedPathname === '/__shutdown') {
          handleShutdown(req, res, fullUrl, clientIp);
          return;
        }
        if (strippedPathname === '/__access-control') {
          await accessControl.handleEndpoint(req, res, fullUrl, clientIp);
          return;
        }

        const accessResult = accessControl.evaluate(clientIp, rawPathname);
        const userAgent = String(req.headers['user-agent'] || '').trim();
        if (!accessResult.allowed) {
          accessControl.recordDeviceAccess({
            clientIp: accessResult.clientIp,
            method,
            pathname: strippedPathname,
            allowed: false,
            reason: accessResult.reason,
            userAgent,
          });
          sendJson(res, 403, {
            success: false,
            errorCode: 403,
            errorMessage: '设备访问受限',
            mode: accessControl.config().mode,
            clientIp: accessResult.clientIp,
            reason: accessResult.reason,
          });
          return;
        }
        if (!accessControl.isInternalPath(fullUrl.pathname || '/')) {
          accessControl.recordDeviceAccess({
            clientIp: accessResult.clientIp || clientIp,
            method,
            pathname: strippedPathname,
            allowed: true,
            reason: accessResult.reason,
            userAgent,
          });
        }

        if (method === 'POST' && /\/api\/env\/(set|add|del)$/.test(rawPathname)) {
          await handleManagementConfig(req, res, rawPathname, host);
          return;
        }

        const headers = buildHeaders(req);
        let body;
        if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
          const buffer = await readRequestBody(req);
          body = buffer.length ? buffer : undefined;
        }
        if (!guardSession(req, res, runtimeDataContext())) return;

        if (strippedPathname === '/__danmaku/prepare') {
          await danmakuCache.handlePrepare(req, res, fullUrl, clientIp, headers);
          return;
        }
        if (strippedPathname.startsWith('/__danmaku/prepared/')) {
          danmakuCache.handlePrepared(req, res, strippedPathname, fullUrl, clientIp);
          return;
        }

        await forwardToCore({
          req,
          res,
          fullUrl,
          strippedPathname,
          clientIp,
          method,
          headers,
          body,
        });
      } catch (error) {
        res.statusCode = 500;
        res.setHeader('content-type', 'text/plain; charset=utf-8');
        res.end(`danmu_api server error: ${error?.stack || error}`);
      }
    });
  }

  return { createServer, forwardToCore, toNodeResponse };
}

module.exports = {
  buildHeaders,
  createHttpRoutes,
  toNodeResponse,
};
