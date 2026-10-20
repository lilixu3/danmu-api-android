'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { createHash, randomUUID } = require('node:crypto');

const digest = value => createHash('sha256').update(value).digest('hex');
const normalizeHome = home => path.resolve(home).replace(/^\/data\/user\/0\//, '/data/data/').replace(/\/+$/, '');

function libraryIdentity(home) {
  return 'file:' + digest(normalizeHome(home) + '/.cache/local-danmu');
}

function createRuntimeContext(home, getVariant, getEnv) {
  const bootId = randomUUID();
  let capabilityRevision = '';
  let cacheClearSupport = 'Unknown';
  let loadedRevision = '';
  let loadGeneration = 0;
  const snapshot = () => {
    const variant = getVariant();
    const env = getEnv() || {};
    const files = ['.danmuapiapp-core-source.json', 'package.json', 'worker.js', 'danmu_api/worker.js', 'danmu-api/worker.js',
      'apis/system-api.js', 'danmu_api/apis/system-api.js', 'danmu-api/apis/system-api.js'];
    const revision = digest(files.map(name => {
      try {
        const stat = fs.statSync(path.join(home, 'danmu_api_' + variant, name));
        return `${name}:${stat.size}:${stat.mtimeMs}:${stat.ino}`;
      } catch { return `${name}:missing`; }
    }).join('|'));
    const localDanmuLibraryId = libraryIdentity(home);
    if (capabilityRevision !== revision) {
      cacheClearSupport = detectCacheClearSupport(path.join(home, 'danmu_api_' + variant));
      capabilityRevision = revision;
    }
    const configuration = digest(JSON.stringify(Object.keys(env).sort().map(key => [key, env[key]])));
    return { runtimeSessionId: bootId + ':' + digest([variant, revision, configuration, loadGeneration].join('|')),
      localDanmuLibraryId, coreRevision: revision, cacheClearSupport,
      coreReady: loadedRevision === variant + ':' + revision };
  };
  snapshot.markLoaded = () => {
    loadGeneration++;
    loadedRevision = getVariant() + ':' + snapshot().coreRevision;
  };
  return snapshot;
}

function guardSession(req, res, context) {
  const expected = req.headers['x-danmu-app-session'];
  if (expected && (expected !== context.runtimeSessionId || context.coreReady === false)) {
    res.writeHead(409, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
    res.end(JSON.stringify({ success: false, errorCode: 'RUNTIME_CHANGED', errorMessage: '运行实例已变化，请刷新后重试；请求未执行' }));
    return false;
  }
  res.setHeader('x-danmu-app-session', context.runtimeSessionId);
  return true;
}

function detectCacheClearSupport(coreDir) {
  const read = name => {
    for (const prefix of ['', 'danmu_api', 'danmu-api']) {
      try {
        const file = path.join(coreDir, prefix, name);
        if (fs.statSync(file).size > 2 * 1024 * 1024) return null;
        return fs.readFileSync(file, 'utf8');
      } catch (error) { if (error.code !== 'ENOENT') return null; }
    }
    return null;
  };
  const source = read('apis/system-api.js');
  const worker = read('worker.js');
  if (!source || !worker) return 'Unknown';
  const keys = ['searchCache', 'commentCache', 'requestHistory', 'animes', 'bangumiData', 'episodeIds', 'episodeNum', 'lastSelectMap'];
  const selective = ['clearActions', 'Array.isArray', 'effectiveItems', 'parsed.items'].every(marker => source.includes(marker)) &&
    keys.every(key => new RegExp('\\b' + key + '\\s*:').test(source)) && worker.includes('/api/cache/clear') &&
    /handleClearCache\s*\(\s*req\s*\)/.test(worker);
  return selective ? 'Selective' : 'LegacyAllOnly';
}

module.exports = { createRuntimeContext, guardSession, libraryIdentity, normalizeHome, detectCacheClearSupport };
