'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const net = require('node:net');
const { spawn } = require('node:child_process');
const { once } = require('node:events');
const runtimeRoot = path.resolve('app/src/main/assets/nodejs-project');
const { createRuntimeContext, libraryIdentity, detectCacheClearSupport } = require(path.join(runtimeRoot, 'app-runtime-context.js'));
const pause = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));

test('physical library identity survives restart and variant switch, but not home switch', () => {
  const home = '/data/data/example/files/nodejs-project';
  const first = createRuntimeContext(home, () => 'stable', () => ({}));
  const second = createRuntimeContext(home, () => 'dev', () => ({}));
  assert.equal(first().localDanmuLibraryId, second().localDanmuLibraryId);
  assert.notEqual(first().runtimeSessionId, second().runtimeSessionId);
  assert.equal(libraryIdentity(home), libraryIdentity('/data/user/0/example/files/nodejs-project/'));
  assert.notEqual(libraryIdentity(home), libraryIdentity('/data/adb/danmuapi_runtime/nodejs-project'));
  assert.doesNotMatch(libraryIdentity(home), /example|secret/);
});

test('query cache configuration never merges Node file libraries or prevents runtime startup', () => {
  const normalHome = '/data/data/example/files/nodejs-project';
  const rootHome = '/data/adb/danmuapi_runtime/example/nodejs-project';
  let env = {};
  const normal = createRuntimeContext(normalHome, () => 'stable', () => env);
  const root = createRuntimeContext(rootHome, () => 'stable', () => env);
  normal.markLoaded();
  root.markLoaded();
  let previousSession = normal().runtimeSessionId;
  for (const configuration of [
    { UPSTASH_REDIS_REST_URL: 'https://cache.example.com/', UPSTASH_REDIS_REST_TOKEN: 'secret-one' },
    { UPSTASH_REDIS_REST_URL: 'https://cache.example.com/', UPSTASH_REDIS_REST_TOKEN: 'secret-two' },
    { UPSTASH_REDIS_REST_URL: 'https://other.example.com/', UPSTASH_REDIS_REST_TOKEN: 'secret-two' },
    { UPSTASH_REDIS_REST_URL: 'not-a-url', UPSTASH_REDIS_REST_TOKEN: 'secret-two' },
    { LOCAL_REDIS_URL: 'redis://cache.example.com:6379' },
    {}
  ]) {
    env = configuration;
    const current = normal();
    assert.equal(current.coreReady, true);
    assert.equal(current.localDanmuLibraryId, libraryIdentity(normalHome));
    assert.equal(root().localDanmuLibraryId, libraryIdentity(rootHome));
    assert.notEqual(current.localDanmuLibraryId, root().localDanmuLibraryId);
    assert.notEqual(current.runtimeSessionId, previousSession);
    assert.doesNotThrow(() => normal.markLoaded());
    previousSession = normal().runtimeSessionId;
  }
});

test('core revision and environment invalidate sessions without inventing a new physical library', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'danmu-context-'));
  try {
    let variant = 'stable';
    let env = {};
    const core = path.join(directory, 'danmu_api_stable');
    fs.mkdirSync(core);
    fs.writeFileSync(path.join(core, 'worker.js'), 'first');
    const snapshot = createRuntimeContext(directory, () => variant, () => env);
    assert.equal(snapshot().coreReady, false);
    snapshot.markLoaded();
    const original = snapshot();
    assert.equal(original.coreReady, true);
    assert.equal(original.runtimeSessionId, snapshot().runtimeSessionId);
    fs.writeFileSync(path.join(core, 'worker.js'), 'different core bytes');
    const changed = snapshot();
    assert.notEqual(original.runtimeSessionId, changed.runtimeSessionId);
    assert.equal(changed.coreReady, false);
    assert.equal(original.localDanmuLibraryId, changed.localDanmuLibraryId);
    snapshot.markLoaded();
    const reloaded = snapshot();
    assert.equal(reloaded.coreReady, true);
    snapshot.markLoaded();
    assert.notEqual(reloaded.runtimeSessionId, snapshot().runtimeSessionId);
    env = { TOKEN: 'changed-token' };
    assert.notEqual(reloaded.runtimeSessionId, snapshot().runtimeSessionId);
    variant = 'dev';
    assert.equal(snapshot().coreReady, false);
    assert.equal(original.localDanmuLibraryId, snapshot().localDanmuLibraryId);
  } finally {
    fs.rmSync(directory, { recursive: true, force: true });
  }
});

test('cache clearing capability follows actual core files and fails closed when unavailable', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'danmu-capability-'));
  try {
    assert.equal(detectCacheClearSupport(directory), 'Unknown');
    fs.mkdirSync(path.join(directory, 'apis'));
    fs.writeFileSync(path.join(directory, 'worker.js'), '/api/cache/clear handleClearCache(req)');
    fs.writeFileSync(path.join(directory, 'apis/system-api.js'), 'legacy implementation');
    assert.equal(detectCacheClearSupport(directory), 'LegacyAllOnly');
    fs.writeFileSync(path.join(directory, 'apis/system-api.js'),
      'clearActions Array.isArray effectiveItems parsed.items ' +
      'searchCache: commentCache: requestHistory: animes: bangumiData: episodeIds: episodeNum: lastSelectMap:');
    assert.equal(detectCacheClearSupport(directory), 'Selective');
  } finally {
    fs.rmSync(directory, { recursive: true, force: true });
  }
});

async function freePort() {
  const server = net.createServer();
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  const port = server.address().port;
  await new Promise(resolve => server.close(resolve));
  return port;
}

for (const worker of [false, true]) for (const malformedRedis of [false, true]) test(`real host fences stale mutations before core dispatch (${worker ? 'Worker' : 'direct'}, ${malformedRedis ? 'malformed Redis' : 'no Redis'})`, { timeout: 30000 }, async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'danmu-runtime-host-'));
  let child;
  let output = '';
  try {
    for (const name of fs.readdirSync(runtimeRoot)) {
      if (name.endsWith('.js') || name === 'package.json' || name === 'app-runtime-assets.json') {
        fs.copyFileSync(path.join(runtimeRoot, name), path.join(directory, name));
      }
    }
    fs.symlinkSync(path.join(runtimeRoot, 'node_modules'), path.join(directory, 'node_modules'));
    fs.mkdirSync(path.join(directory, 'config'));
    fs.writeFileSync(path.join(directory, 'config/.env'), `TOKEN=fixture-token\nDANMU_API_WORKER=${worker}\nDANMU_API_HOT_RELOAD=false\n` +
      (malformedRedis ? 'UPSTASH_REDIS_REST_URL=not-a-url\nUPSTASH_REDIS_REST_TOKEN=fixture-redis-token\n' : ''));
    const core = path.join(directory, 'danmu_api_stable');
    fs.mkdirSync(core);
    fs.writeFileSync(path.join(core, 'package.json'), '{"type":"module"}');
    fs.writeFileSync(path.join(core, 'worker.js'), `let mutations = 0;
      export function handleRequest(req, env) {
        if (!new URL(req.url).pathname.startsWith('/' + env.TOKEN + '/')) return Response.json({errorMessage:'Unauthorized'}, {status:401});
        if (req.method === 'POST') mutations++;
        return Response.json({mutations});
      }`);
    const port = await freePort();
    child = spawn(process.execPath, [path.join(directory, 'main.js')], {
      cwd: directory,
      env: { ...process.env, DANMU_API_HOME: directory, DANMU_API_PORT: String(port), DANMU_API_HOST: '127.0.0.1', DANMU_API_VARIANT: 'stable',
        DANMU_API_PROXY_PORT: String(await freePort()), OUTBOUND_MODE: 'off', DANMU_APP_OUTBOUND_CONFIG: '', PROXY_URL: '' },
      stdio: ['ignore', 'pipe', 'pipe']
    });
    child.stdout.on('data', data => output += data);
    child.stderr.on('data', data => output += data);
    const base = `http://127.0.0.1:${port}`;
    let health;
    for (let attempt = 0; attempt < 150; attempt++) {
      if (child.exitCode !== null) break;
      try { health = await (await fetch(base + '/__health')).json(); } catch {}
      if (health?.coreReady) break;
      await pause(100);
    }
    assert.equal(health?.coreReady, true, output);
    assert.equal(health.resolvedHome, directory);
    assert.equal(health.localDanmuLibraryId, libraryIdentity(directory));
    assert.equal(health.variant, 'stable');
    assert.equal((await fetch(base + '/fixture-token/api/v2/search/anime?keyword=fixture')).status, 200);
    assert.equal((await fetch(base + '/fixture-token/api/v2/comment/123')).status, 200);
    const headers = { 'X-Danmu-App-Session': health.runtimeSessionId };
    const url = base + '/fixture-token/api/local-danmu/upload';
    const stale = await fetch(url, { method: 'POST', headers: { 'X-Danmu-App-Session': 'previous-instance' }, body: 'upload' });
    assert.equal(stale.status, 409);
    assert.equal((await stale.json()).errorCode, 'RUNTIME_CHANGED');
    assert.equal((await (await fetch(url, { headers })).json()).mutations, 0);
    assert.equal((await (await fetch(url, { method: 'POST', headers, body: 'upload' })).json()).mutations, 1);
    assert.equal((await fetch(base + '/wrong-token/api/local-danmu/upload', { method: 'POST', headers, body: 'upload' })).status, 401);
    fs.appendFileSync(path.join(core, 'worker.js'), '\nexport const updated = true;\n');
    const changed = await (await fetch(base + '/__health')).json();
    assert.equal(changed.coreReady, false);
    assert.equal(changed.localDanmuLibraryId, health.localDanmuLibraryId);
    assert.notEqual(changed.runtimeSessionId, health.runtimeSessionId);
    assert.equal((await fetch(url, { method: 'POST', headers, body: 'upload' })).status, 409);
    assert.equal((await fetch(url, { method: 'POST', headers: { 'X-Danmu-App-Session': changed.runtimeSessionId }, body: 'upload' })).status, 409);
    assert.equal((await (await fetch(url)).json()).mutations, 1);
  } finally {
    if (child && child.exitCode === null) {
      child.kill('SIGTERM');
      await Promise.race([once(child, 'exit'), pause(2000)]);
      if (child.exitCode === null) { child.kill('SIGKILL'); await once(child, 'exit'); }
    }
    fs.rmSync(directory, { recursive: true, force: true });
  }
});

test('runtime data consumers cannot silently use the normal-mode directory', () => {
  const sourceRoot = path.resolve('app/src/main/java/com/example/danmuapiapp');
  for (const name of ['data/repository/LocalDanmuRepositoryImpl.kt', 'data/repository/CacheRepositoryImpl.kt',
    'data/service/AppBackupService.kt', 'ui/screen/diagnostics/DiagnosticsViewModel.kt']) {
    const source = fs.readFileSync(path.join(sourceRoot, name), 'utf8');
    assert.doesNotMatch(source, /(?:RuntimePaths|NodeProjectManager)\.(?:normalProjectDir|projectDir)\s*\(/, name);
    assert.match(source, /RuntimeDataAccess/, name);
  }
  for (const name of ['RuntimePaths.kt', 'NodeProjectManager.kt']) {
    const source = fs.readFileSync(path.join(sourceRoot, 'data/service', name), 'utf8');
    assert.match(source, /@Deprecated\([^]*?DeprecationLevel\.ERROR[^]*?fun projectDir\(context: Context\)/, name);
  }
  for (const name of ['ui/screen/localdanmu/LocalDanmuScreen.kt', 'ui/screen/download/DanmuDownloadScreen.kt']) {
    assert.doesNotMatch(fs.readFileSync(path.join(sourceRoot, name), 'utf8'), /\.importedToLocal\b/, name);
  }
});
