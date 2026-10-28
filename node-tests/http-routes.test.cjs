'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const os = require('node:os');
const path = require('node:path');
const { once } = require('node:events');
const { createHttpRoutes } = require('../app/src/main/assets/nodejs-project/app-http-routes.js');
const { applyDanmuXmlOverrides } = require('../app/src/main/assets/nodejs-project/app-danmaku-cache.js');
const appManagement = require('../app/src/main/assets/nodejs-project/app-management.js');

const runtimeRoot = path.resolve('app/src/main/assets/nodejs-project');
const delay = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));

async function readRequestBody(req) {
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  return Buffer.concat(chunks);
}

function sendJson(res, statusCode, payload) {
  res.statusCode = statusCode;
  res.setHeader('content-type', 'application/json; charset=utf-8');
  res.end(JSON.stringify(payload));
}

function createFixture(options = {}) {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), 'danmu-http-routes-'));
  const envFile = path.join(home, 'config', '.env');
  fs.mkdirSync(path.dirname(envFile), { recursive: true });
  fs.writeFileSync(envFile, options.envFileContent || 'ADMIN_TOKEN="admin"\nSOURCE_ORDER="old"\n');

  const env = { ADMIN_TOKEN: 'admin', TOKEN: 'token', DANMU_API_HOME: home, LOG_LEVEL: 'info', ...options.env };
  const metrics = options.metrics || {
    requestCount: 0,
    lastRequestAt: 0,
    lastRequestPath: '',
    lastClientIp: '',
  };
  const state = {
    port: 0,
    guardCalls: [],
    guardResults: options.guardResults || [],
    accessRecords: [],
    prepareCalls: [],
    preparedCalls: [],
    coreCalls: [],
    shutdownCalls: 0,
    loadConfigCalls: 0,
    refreshLogLevelCalls: 0,
    refreshLogConfigCalls: 0,
    syncEnvCalls: 0,
    now: 1_700_000_000_000,
    accessControl: {
      evaluate: options.evaluate || (() => ({ allowed: true, reason: 'mode_off', clientIp: '203.0.113.7' })),
      recordDeviceAccess: record => state.accessRecords.push(record),
      isInternalPath: options.isInternalPath || (() => false),
      handleEndpoint: async () => {},
      config: () => ({ mode: 'off', blacklistCount: 0 }),
      stats: () => ({ totalBlockedRequests: 0, trackedDevices: 0 }),
    },
  };

  const handler = options.handler || (async request => Response.json({ url: request.url }));
  const routes = createHttpRoutes({
    http,
    appManagement,
    readClientIp: req => String(req.headers['x-forwarded-for'] || req.socket?.remoteAddress || ''),
    readRequestBody,
    normalizeClientIp: value => String(value || '').split(',')[0].trim(),
    guardSession: (req, res) => {
      state.guardCalls.push(req.url);
      const allowed = state.guardResults[state.guardCalls.length - 1] !== false;
      if (!allowed) {
        res.writeHead(409, { 'content-type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({ success: false, errorCode: 'RUNTIME_CHANGED' }));
        return false;
      }
      res.setHeader('x-danmu-app-session', 'session-1');
      return true;
    },
    runtimeDataContext: () => ({ runtimeSessionId: 'session-1', coreReady: true }),
    configRuntime: {
      loadConfigOnce: () => { state.loadConfigCalls += 1; },
      maybeReloadOnTraffic: () => false,
      lastEnvMtimeMs: 123456,
    },
    accessControl: state.accessControl,
    danmakuCache: {
      handlePrepare: async (req, res, fullUrl, clientIp, headers) => {
        state.prepareCalls.push({ url: fullUrl.toString(), clientIp, headers });
        res.statusCode = 200;
        res.end('prepared');
      },
      handlePrepared: (req, res, strippedPathname) => {
        state.preparedCalls.push(strippedPathname);
        res.statusCode = 200;
        res.end('prepared-body');
      },
    },
    workerRuntime: { activeState: null, sendWorkerRequest: async () => ({ result: {} }) },
    sourceProbe: { catalog: () => ({ sources: [] }), probeSource: async () => ({ connected: true }) },
    appOutboundDiagnostics: { diagnosticSources: [], diagnoseSource: async () => ({ ok: true }) },
    sendJson,
    stripTokenPrefix: pathname => pathname.startsWith('/token/') ? pathname.slice('/token'.length) : pathname,
    isAdminAuthorized: req => String(req.headers['x-admin-token'] || '') === 'admin',
    getEnvSnapshot: () => ({ ...env }),
    getOutboundSnapshot: () => ({ status: 'disabled', config: { enabled: false, sources: [] } }),
    getRequestHandler: () => async (request, requestEnv, platform, clientIp) => {
      state.coreCalls.push({ url: request.url, env: requestEnv, platform, clientIp });
      return handler(request);
    },
    getVariant: () => 'stable',
    getVariantInfo: () => ({ label: '稳定版 (fixture)' }),
    getLoadedCoreDirectory: () => '',
    getLoadedRuntimeVariant: () => 'stable',
    getRuntimeIdentity: () => 'fixture-runtime',
    getHost: () => '127.0.0.1',
    getPort: () => state.port,
    getProxyPort: () => 5321,
    getCacheProbeDir: () => path.join(home, '.cache'),
    checkCacheProbeWritable: () => true,
    getMetrics: () => metrics,
    getLogFile: () => path.join(home, 'logs', 'server.log'),
    getHome: () => home,
    getProcess: () => process,
    getEnv: () => env,
    getNetworkFingerprint: () => 'fixture-fingerprint',
    maybeReloadCoreOnTraffic: () => {},
    removeQuietCoreLogNoise: () => {},
    quietLogScanStartHint: () => 0,
    isQuietCoreLogRequest: () => false,
    applyDanmuXmlOverrides,
    refreshLogLevel: () => { state.refreshLogLevelCalls += 1; },
    refreshLogConfig: () => { state.refreshLogConfigCalls += 1; },
    syncEnvToWorker: () => { state.syncEnvCalls += 1; },
    requestShutdown: () => { state.shutdownCalls += 1; },
    envFile,
    projectDir: runtimeRoot,
    now: () => state.now,
  });

  return { home, env, envFile, metrics, routes, state };
}

async function withServer(options, run) {
  const fixture = createFixture(options);
  const server = fixture.routes.createServer();
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  fixture.state.port = server.address().port;
  const base = `http://127.0.0.1:${fixture.state.port}`;
  try {
    await run(fixture, base, server);
  } finally {
    await new Promise(resolve => {
      server.close(resolve);
      server.closeAllConnections?.();
    });
    fs.rmSync(fixture.home, { recursive: true, force: true });
  }
}

test('security.txt is handled before metrics and session validation', async () => {
  await withServer({}, async ({ state, metrics }, base) => {
    const response = await fetch(`${base}/.well-known/security.txt`, {
      headers: { 'x-danmu-app-session': 'stale' },
    });
    assert.equal(response.status, 404);
    assert.equal(await response.text(), 'Not Found');
    assert.equal(metrics.requestCount, 0);
    assert.deepEqual(state.guardCalls, []);
  });
});

test('health reports metrics, listener details and configuration state', async () => {
  await withServer({ metrics: { requestCount: 4, lastRequestAt: 1, lastRequestPath: '/before', lastClientIp: '127.0.0.1' } }, async ({ state }, base) => {
    const response = await fetch(`${base}/__health`);
    const payload = await response.json();
    assert.equal(response.status, 200);
    assert.equal(response.headers.get('x-danmu-app-session'), 'session-1');
    assert.equal(payload.ok, true);
    assert.equal(payload.time, new Date(state.now).toISOString());
    assert.equal(payload.requestCount, 5);
    assert.equal(payload.lastRequestAt, state.now);
    assert.equal(payload.lastRequestPath, '/__health');
    assert.deepEqual(payload.ports, { main: state.port, proxy: 5321 });
    assert.equal(payload.variant, 'stable');
    assert.equal(payload.variantLabel, '稳定版 (fixture)');
    assert.equal(payload.runtimeIdentity, 'fixture-runtime');
    assert.equal(payload.runtimeSessionId, 'session-1');
    assert.equal(payload.networkRuntimeFingerprint, 'fixture-fingerprint');
    assert.equal(payload.envFileMtimeMs, 123456);
    assert.equal(payload.logLevel, 'info');
    assert.deepEqual(payload.accessControl, {
      mode: 'off',
      whitelistCount: 0,
      blacklistCount: 0,
      blockedRequests: 0,
    });
    assert.equal(state.guardCalls.length, 1);
  });
});

test('shutdown requires admin authorization and triggers after the response', async () => {
  await withServer({}, async ({ state }, base) => {
    const denied = await fetch(`${base}/__shutdown`);
    assert.equal(denied.status, 403);
    assert.equal(await denied.text(), 'Forbidden');
    await delay(25);
    assert.equal(state.shutdownCalls, 0);

    const accepted = await fetch(`${base}/__shutdown`, { headers: { 'x-admin-token': 'admin' } });
    assert.equal(accepted.status, 200);
    assert.equal(await accepted.text(), 'OK');
    assert.equal(state.shutdownCalls, 0);
    await delay(30);
    assert.equal(state.shutdownCalls, 1);
    assert.equal(state.guardCalls.length, 2);
  });
});

test('blacklisted devices are rejected before management and core dispatch', async () => {
  await withServer({
    evaluate: () => ({ allowed: false, clientIp: '203.0.113.7', reason: 'blacklist_hit' }),
  }, async ({ state }, base) => {
    const response = await fetch(`${base}/api/v2/search/anime`, { headers: { 'x-admin-token': 'admin' } });
    const payload = await response.json();
    assert.equal(response.status, 403);
    assert.equal(payload.errorCode, 403);
    assert.equal(payload.errorMessage, '设备访问受限');
    assert.equal(payload.reason, 'blacklist_hit');
    assert.equal(state.accessRecords.length, 1);
    assert.equal(state.accessRecords[0].allowed, false);
    assert.equal(state.accessRecords[0].pathname, '/api/v2/search/anime');
    assert.equal(state.coreCalls.length, 0);
    assert.equal(state.guardCalls.length, 1);
  });
});

test('management env writes persist before the second session check', async () => {
  await withServer({}, async ({ state, envFile }, base) => {
    const response = await fetch(`${base}/admin/api/env/set`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'x-admin-token': 'admin' },
      body: JSON.stringify({ key: 'SOURCE_ORDER', value: 'bahamut,tmdb' }),
    });
    const payload = await response.json();
    assert.equal(response.status, 200);
    assert.equal(payload.success, true);
    assert.match(fs.readFileSync(envFile, 'utf8'), /SOURCE_ORDER="bahamut,tmdb"/);
    assert.equal(state.loadConfigCalls, 2);
    assert.equal(state.refreshLogLevelCalls, 1);
    assert.equal(state.refreshLogConfigCalls, 1);
    assert.equal(state.syncEnvCalls, 1);
    assert.equal(state.guardCalls.length, 1);
    assert.equal(state.coreCalls.length, 0);
  });
});

test('the second session guard stops core dispatch after request bodies are read', async () => {
  await withServer({ guardResults: [true, false] }, async ({ state }, base) => {
    const response = await fetch(`${base}/api/v2/search/anime`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'x-admin-token': 'admin' },
      body: JSON.stringify({ keyword: 'fixture' }),
    });
    assert.equal(response.status, 409);
    assert.equal((await response.json()).errorCode, 'RUNTIME_CHANGED');
    assert.equal(state.guardCalls.length, 2);
    assert.equal(state.coreCalls.length, 0);
  });
});

test('danmaku prepare and prepared routes dispatch to the cache handler', async () => {
  await withServer({}, async ({ state }, base) => {
    const prepared = await fetch(`${base}/token/__danmaku/prepare?url=http%3A%2F%2Fexample.test`, {
      headers: { 'x-admin-token': 'admin', 'user-agent': 'fixture-agent' },
    });
    assert.equal(prepared.status, 200);
    assert.equal(await prepared.text(), 'prepared');
    assert.equal(state.prepareCalls.length, 1);
    assert.equal(state.prepareCalls[0].clientIp, '127.0.0.1');
    assert.equal(state.prepareCalls[0].headers['user-agent'], 'fixture-agent');

    const served = await fetch(`${base}/__danmaku/prepared/abc.xml`, { headers: { 'x-admin-token': 'admin' } });
    assert.equal(served.status, 200);
    assert.equal(await served.text(), 'prepared-body');
    assert.deepEqual(state.preparedCalls, ['/__danmaku/prepared/abc.xml']);
    assert.equal(state.coreCalls.length, 0);
    assert.equal(state.guardCalls.length, 4);
  });
});

test('ordinary requests reach the loaded core and preserve its response', async () => {
  await withServer({
    handler: async request => new Response(`core:${new URL(request.url).pathname}`, {
      status: 201,
      headers: { 'content-type': 'text/plain', 'x-core': 'fixture' },
    }),
  }, async ({ state }, base) => {
    const response = await fetch(`${base}/token/api/v2/search/anime?foo=1`, {
      headers: { 'x-admin-token': 'admin' },
    });
    assert.equal(response.status, 201);
    assert.equal(await response.text(), 'core:/token/api/v2/search/anime');
    assert.equal(response.headers.get('x-core'), 'fixture');
    assert.equal(state.coreCalls.length, 1);
    assert.equal(state.coreCalls[0].platform, 'node');
    assert.equal(state.coreCalls[0].clientIp, '127.0.0.1');
    assert.equal(state.guardCalls.length, 2);
  });
});

test('danmaku offset and font size overrides rewrite XML after a clean core call', async () => {
  await withServer({
    handler: async () => new Response('<i><d p="1,1,25,white">a</d></i>', {
      headers: { 'content-type': 'application/xml; charset=utf-8' },
    }),
  }, async ({ state }, base) => {
    const response = await fetch(`${base}/token/api/v2/comment/123?offset=2&fontSize=36`, {
      headers: { 'x-admin-token': 'admin' },
    });
    assert.equal(response.status, 200);
    assert.equal(await response.text(), '<i><d p="3,1,36,white">a</d></i>');
    assert.equal(state.coreCalls.length, 1);
    const coreUrl = new URL(state.coreCalls[0].url);
    assert.equal(coreUrl.searchParams.has('offset'), false);
    assert.equal(coreUrl.searchParams.has('fontSize'), false);
  });
});

test('app_section deep links inject the requested core section without caching', async () => {
  await withServer({
    handler: async () => new Response('<html><body>core</body></html>', {
      headers: { 'content-type': 'text/html; charset=utf-8' },
    }),
  }, async (_fixture, base) => {
    const response = await fetch(`${base}/token?app_section=env`, { headers: { 'x-admin-token': 'admin' } });
    const html = await response.text();
    assert.equal(response.status, 200);
    assert.match(html, /data-danmu-app-section/);
    assert.match(html, /switchSection\('env'\)/);
    assert.equal(response.headers.get('cache-control'), 'no-store');
  });
});
