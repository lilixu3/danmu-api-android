const assert = require('assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { pathToFileURL } = require('url');

const root = path.resolve(__dirname, '..');
const hostPath = path.join(
  root,
  'app/src/main/assets/nodejs-project/favorite-scheduler-host.js'
);
const { startFavoriteSchedulerHost } = require(hostPath);

function write(rootDir, relativePath, content) {
  const target = path.join(rootDir, relativePath);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, content, 'utf8');
}

async function waitFor(predicate, timeoutMs = 2000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (predicate()) return;
    await new Promise((resolve) => setTimeout(resolve, 25));
  }
  throw new Error('timed out waiting for favorite persistence');
}

async function main() {
  const projectDir = fs.mkdtempSync(path.join(os.tmpdir(), 'favorite-host-regression-'));
  const variantDir = 'core';
  let host = null;
  try {
    write(projectDir, 'package.json', '{"type":"commonjs"}\n');
    write(projectDir, `${variantDir}/package.json`, '{"type":"module"}\n');
    write(
      projectDir,
      `${variantDir}/configs/globals.js`,
      `export const Globals = {
  favoriteCache: new Map(),
  lastHashes: { favoriteCache: null },
  localCacheValid: false,
  redisValid: false,
  init() { globalThis.__favoriteTestGlobals = this; return this; }
};
export const globals = Globals;
`
    );
    write(
      projectDir,
      `${variantDir}/utils/favorite-util.js`,
      `import { Globals } from '../configs/globals.js';
export function loadFavorites(value = {}) {
  Globals.favoriteCache = new Map(Object.entries(value));
  return Globals.favoriteCache;
}
`
    );
    write(
      projectDir,
      `${variantDir}/utils/cache-util.js`,
      `import { Globals } from '../configs/globals.js';
export async function judgeLocalCacheValid() { Globals.localCacheValid = true; }
export async function getLocalCaches() {}
export async function updateLocalCaches() {}
`
    );
    write(
      projectDir,
      `${variantDir}/utils/redis-util.js`,
      `import { Globals } from '../configs/globals.js';
export async function judgeRedisValid() { Globals.redisValid = true; }
export async function getRedisCaches() {
  Globals.favoriteCache = new Map([['deleted-on-device', { source: 'stale-redis' }]]);
}
export async function updateRedisCaches() {
  globalThis.__favoriteRedisSnapshots ||= [];
  globalThis.__favoriteRedisSnapshots.push([...Globals.favoriteCache.keys()]);
  Globals.lastHashes.favoriteCache = 'synced';
}
`
    );
    write(
      projectDir,
      `${variantDir}/apis/favorite-api.js`,
      `import { Globals } from '../configs/globals.js';
export async function refreshFavoriteByKeyword(keyword) {
  await globalThis.__favoriteRefreshGate;
  Globals.favoriteCache.set(keyword, { source: 'late-refresh' });
  return { keyword };
}
export async function persistFavorites() {}
`
    );
    write(
      projectDir,
      `${variantDir}/utils/favorite-schedule-util.js`,
      `export async function startFavoriteScheduler(callbacks) {
  globalThis.__favoriteSchedulerCallbacks = callbacks;
  return true;
}
export function stopFavoriteScheduler() {}
`
    );
    write(
      projectDir,
      '.cache/favoritesCache',
      JSON.stringify({ kept: { source: 'local' } })
    );

    host = await startFavoriteSchedulerHost({
      projectDir,
      variantDir,
      env: {},
      log: () => {},
    });
    assert.equal(host.supported, true);

    const globals = globalThis.__favoriteTestGlobals;
    assert.deepEqual([...globals.favoriteCache.keys()], ['kept']);
    assert.deepEqual(globalThis.__favoriteRedisSnapshots.at(-1), ['kept']);

    let releaseRefresh;
    globalThis.__favoriteRefreshGate = new Promise((resolve) => {
      releaseRefresh = resolve;
    });
    const refresh = globalThis.__favoriteSchedulerCallbacks.refresh('kept');
    globals.favoriteCache.delete('kept');
    write(projectDir, '.cache/favoritesCache', '{}');
    releaseRefresh();

    await assert.rejects(refresh, /定时刷新过程中删除/);
    assert.equal(globals.favoriteCache.has('kept'), false);

    await host.sync();
    assert.deepEqual(globalThis.__favoriteRedisSnapshots.at(-1), []);

    const snapshotsBeforeFileFallback = globalThis.__favoriteRedisSnapshots.length;
    globals.favoriteCache.set('stale-after-file-write', { source: 'stale-memory' });
    write(projectDir, '.cache/favoritesCache', '{}');
    await waitFor(() => {
      const snapshots = globalThis.__favoriteRedisSnapshots || [];
      return snapshots.length > snapshotsBeforeFileFallback && snapshots.at(-1).length === 0;
    });

    console.log('favorite scheduler host regression passed');
  } finally {
    try { host?.stop(); } catch {}
    fs.rmSync(projectDir, { recursive: true, force: true });
    delete globalThis.__favoriteTestGlobals;
    delete globalThis.__favoriteRedisSnapshots;
    delete globalThis.__favoriteRefreshGate;
    delete globalThis.__favoriteSchedulerCallbacks;
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
