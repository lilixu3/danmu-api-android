'use strict';

const fs = require('fs');
const path = require('path');
const { pathToFileURL } = require('url');

const INIT_TIMEOUT_MS = 8000;

function moduleCandidates(projectDir, variantDir, relativePath) {
  const base = path.join(projectDir, variantDir);
  return [
    path.join(base, relativePath),
    path.join(base, 'danmu_api', relativePath),
  ];
}

async function importCoreModule(projectDir, variantDir, relativePath) {
  const candidates = moduleCandidates(projectDir, variantDir, relativePath);
  let importError = null;
  for (const candidate of candidates) {
    if (!fs.existsSync(candidate)) continue;
    try {
      return await import(pathToFileURL(candidate).href);
    } catch (error) {
      importError ||= error;
    }
  }
  if (importError) throw importError;
  return null;
}

function withTimeout(promise, timeoutMs, fallback) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => resolve(fallback), timeoutMs);
    Promise.resolve(promise).then(
      (value) => {
        clearTimeout(timer);
        resolve(value);
      },
      (error) => {
        clearTimeout(timer);
        reject(error);
      }
    );
  });
}

function resolveGlobals(module) {
  return module?.Globals || module?.globals || null;
}

/**
 * Starts the core scheduler inside the same JS isolate as the request handler.
 * The returned stop function is intentionally idempotent so hosts can call it
 * during a variant reload and during process shutdown.
 */
async function startFavoriteSchedulerHost({
  projectDir,
  variantDir,
  env,
  port = 9321,
  log = () => {},
}) {
  const [globalsModule, scheduleModule, favoriteModule, cacheModule, redisModule] = await Promise.all([
    importCoreModule(projectDir, variantDir, 'configs/globals.js'),
    importCoreModule(projectDir, variantDir, 'utils/favorite-schedule-util.js'),
    importCoreModule(projectDir, variantDir, 'apis/favorite-api.js'),
    importCoreModule(projectDir, variantDir, 'utils/cache-util.js'),
    importCoreModule(projectDir, variantDir, 'utils/redis-util.js'),
  ]);

  const globals = resolveGlobals(globalsModule);
  const start = scheduleModule?.startFavoriteScheduler;
  const stop = scheduleModule?.stopFavoriteScheduler;
  const refreshFavoriteByKeyword = favoriteModule?.refreshFavoriteByKeyword;
  const persistFavorites = favoriteModule?.persistFavorites;
  const judgeLocalCacheValid = cacheModule?.judgeLocalCacheValid;
  const getLocalCaches = cacheModule?.getLocalCaches;
  const judgeRedisValid = redisModule?.judgeRedisValid;
  const getRedisCaches = redisModule?.getRedisCaches;

  if (
    !globals ||
    typeof start !== 'function' ||
    typeof stop !== 'function' ||
    typeof refreshFavoriteByKeyword !== 'function' ||
    typeof persistFavorites !== 'function'
  ) {
    log('[favorite] scheduler is not supported by the selected core');
    return { supported: false, stop: () => {} };
  }

  try {
    if (typeof globals.init === 'function') globals.init(env || process.env);
    globals.deployPlatform = 'node';

    // Match server.js startup: restore local/Redis favorites before the first
    // scheduler tick. Cache/Redis helpers are optional for older core layouts.
    if (typeof judgeLocalCacheValid === 'function') {
      await withTimeout(
        judgeLocalCacheValid('/api/v2/favorite/list', 'node'),
        INIT_TIMEOUT_MS,
        undefined
      );
    }
    if (globals.localCacheValid && typeof getLocalCaches === 'function') {
      await withTimeout(getLocalCaches(), INIT_TIMEOUT_MS, undefined);
    }
    if (typeof judgeRedisValid === 'function') {
      await withTimeout(judgeRedisValid('/api/v2/favorite/list'), INIT_TIMEOUT_MS, undefined);
    }
    if (globals.redisValid && typeof getRedisCaches === 'function') {
      await withTimeout(getRedisCaches(), INIT_TIMEOUT_MS, undefined);
    }

    // A reload can reuse the same module instance. Always clear its previous
    // timer before installing the current host callback.
    stop();
    const refreshUrl = new URL(`http://127.0.0.1:${Number(port) || 9321}/api/v2/favorite/refresh`);
    await start({
      refresh: (keyword) => refreshFavoriteByKeyword(keyword, refreshUrl, { persist: false }),
      persist: persistFavorites,
    });
    log('[favorite] scheduler started (Asia/Shanghai)');

    let stopped = false;
    return {
      supported: true,
      stop: () => {
        if (stopped) return;
        stopped = true;
        try { stop(); } catch {}
      },
    };
  } catch (error) {
    log('[favorite] scheduler unavailable:', error?.message || error);
    try { stop(); } catch {}
    return { supported: false, stop: () => {} };
  }
}

module.exports = { startFavoriteSchedulerHost };
