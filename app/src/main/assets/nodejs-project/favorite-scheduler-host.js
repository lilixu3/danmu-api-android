'use strict';

const fs = require('fs');
const path = require('path');
const { pathToFileURL } = require('url');

const INIT_TIMEOUT_MS = 8000;
const FAVORITE_CACHE_FILE = 'favoritesCache';
const REDIS_SYNC_DEBOUNCE_MS = 100;
const REDIS_SYNC_POLL_MS = 250;

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

function favoriteCacheFile(projectDir) {
  return path.join(projectDir, '.cache', FAVORITE_CACHE_FILE);
}

function decodeFavoriteDocument(raw) {
  let value = JSON.parse(raw);
  for (let depth = 0; depth < 3 && typeof value === 'string'; depth += 1) {
    value = JSON.parse(value);
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('favoritesCache must contain a JSON object');
  }
  return value;
}

function readFavoriteSnapshotFile(file) {
  if (!fs.existsSync(file)) return { file, exists: false, valid: false, value: null };
  try {
    return {
      file,
      exists: true,
      valid: true,
      value: decodeFavoriteDocument(fs.readFileSync(file, 'utf8')),
    };
  } catch (error) {
    return { file, exists: true, valid: false, value: null, error };
  }
}

function readLocalFavoriteSnapshot(projectDir) {
  return readFavoriteSnapshotFile(favoriteCacheFile(projectDir));
}

function restoreFavorites(globals, value, loadFavorites) {
  if (typeof loadFavorites === 'function') return loadFavorites(value);
  globals.favoriteCache = new Map(Object.entries(value || {}));
  return globals.favoriteCache;
}

function markFavoriteHashDirty(globals) {
  if (!globals.lastHashes || typeof globals.lastHashes !== 'object') {
    globals.lastHashes = {};
  }
  globals.lastHashes.favoriteCache = null;
}

function favoriteFileStamp(file) {
  try {
    const stat = fs.statSync(file, { bigint: true });
    return [stat.dev, stat.ino, stat.size, stat.mtimeNs, stat.ctimeNs].join(':');
  } catch (error) {
    if (error?.code === 'ENOENT') return 'missing';
    return `error:${error?.code || error?.message || 'unknown'}`;
  }
}

function installFavoriteDeleteTracker(globals) {
  const deleteVersions = new Map();
  let trackedMap = null;
  let previousDescriptor = null;
  let trackedDelete = null;

  const restoreTrackedMap = () => {
    if (!trackedMap || trackedMap.delete !== trackedDelete) return;
    if (previousDescriptor) {
      Object.defineProperty(trackedMap, 'delete', previousDescriptor);
    } else {
      delete trackedMap.delete;
    }
  };

  const ensureInstalled = () => {
    const current = globals.favoriteCache;
    if (!(current instanceof Map) || current === trackedMap) return;
    restoreTrackedMap();
    trackedMap = current;
    previousDescriptor = Object.getOwnPropertyDescriptor(current, 'delete') || null;
    trackedDelete = function deleteFavorite(key) {
      const normalized = String(key ?? '').trim();
      if (normalized) {
        deleteVersions.set(normalized, (deleteVersions.get(normalized) || 0) + 1);
      }
      return Map.prototype.delete.call(this, key);
    };
    Object.defineProperty(current, 'delete', {
      configurable: true,
      writable: true,
      value: trackedDelete,
    });
  };

  return {
    refresh() {
      ensureInstalled();
    },
    version(keyword) {
      ensureInstalled();
      return deleteVersions.get(String(keyword ?? '').trim()) || 0;
    },
    removeWithoutTracking(keyword) {
      ensureInstalled();
      const current = globals.favoriteCache;
      if (!(current instanceof Map)) return false;
      return Map.prototype.delete.call(current, keyword);
    },
    stop() {
      restoreTrackedMap();
      trackedMap = null;
      trackedDelete = null;
      previousDescriptor = null;
    },
  };
}

function startFavoriteRedisBridge({ cacheFile, globals, loadFavorites, updateRedisCaches, log }) {
  if (typeof updateRedisCaches !== 'function') {
    return { sync: async () => {}, stop: () => {} };
  }

  let stopped = false;
  let timer = null;
  let activeSync = null;
  let pending = false;
  let lastFileStamp = favoriteFileStamp(cacheFile);

  const sync = () => {
    if (stopped || !globals.redisValid) return Promise.resolve();
    pending = true;
    if (activeSync) return activeSync;
    activeSync = (async () => {
      try {
        while (pending && !stopped) {
          pending = false;
          const localSnapshot = readFavoriteSnapshotFile(cacheFile);
          if (localSnapshot.valid) {
            restoreFavorites(globals, localSnapshot.value, loadFavorites);
          } else if (localSnapshot.error) {
            log('[favorite] invalid local snapshot skipped during Redis sync:', localSnapshot.error.message);
          }
          markFavoriteHashDirty(globals);
          await updateRedisCaches();
        }
      } catch (error) {
        log('[favorite] Redis reconciliation failed:', error?.message || error);
      } finally {
        activeSync = null;
      }
    })();
    return activeSync;
  };

  const pollFile = () => {
    const currentFileStamp = favoriteFileStamp(cacheFile);
    if (currentFileStamp === lastFileStamp) return;
    lastFileStamp = currentFileStamp;
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => {
      timer = null;
      void sync();
    }, REDIS_SYNC_DEBOUNCE_MS);
  };

  // Capture the baseline synchronously, then compare it ourselves. fs.watchFile
  // can miss a write that lands before its first internal stat completes.
  const poller = setInterval(pollFile, REDIS_SYNC_POLL_MS);
  poller.unref?.();
  return {
    sync,
    stop() {
      stopped = true;
      if (timer) clearTimeout(timer);
      timer = null;
      clearInterval(poller);
    },
  };
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
  const [globalsModule, scheduleModule, favoriteModule, favoriteUtilModule, cacheModule, redisModule] = await Promise.all([
    importCoreModule(projectDir, variantDir, 'configs/globals.js'),
    importCoreModule(projectDir, variantDir, 'utils/favorite-schedule-util.js'),
    importCoreModule(projectDir, variantDir, 'apis/favorite-api.js'),
    importCoreModule(projectDir, variantDir, 'utils/favorite-util.js'),
    importCoreModule(projectDir, variantDir, 'utils/cache-util.js'),
    importCoreModule(projectDir, variantDir, 'utils/redis-util.js'),
  ]);

  const globals = resolveGlobals(globalsModule);
  const start = scheduleModule?.startFavoriteScheduler;
  const stop = scheduleModule?.stopFavoriteScheduler;
  const refreshFavoriteByKeyword = favoriteModule?.refreshFavoriteByKeyword;
  const persistFavorites = favoriteModule?.persistFavorites;
  const loadFavorites = favoriteUtilModule?.loadFavorites;
  const judgeLocalCacheValid = cacheModule?.judgeLocalCacheValid;
  const getLocalCaches = cacheModule?.getLocalCaches;
  const updateLocalCaches = cacheModule?.updateLocalCaches;
  const judgeRedisValid = redisModule?.judgeRedisValid;
  const getRedisCaches = redisModule?.getRedisCaches;
  const updateRedisCaches = redisModule?.updateRedisCaches;

  if (
    !globals ||
    typeof start !== 'function' ||
    typeof stop !== 'function' ||
    typeof refreshFavoriteByKeyword !== 'function' ||
    typeof persistFavorites !== 'function'
  ) {
    log('[favorite] scheduler is not supported by the selected core');
    return { supported: false, sync: async () => {}, stop: () => {} };
  }

  let deleteTracker = null;
  let redisBridge = null;
  let disposed = false;
  try {
    if (typeof globals.init === 'function') globals.init(env || process.env);
    globals.deployPlatform = 'node';
    const localSnapshot = readLocalFavoriteSnapshot(projectDir);

    // Restore all core caches first, then make an existing local favorite file
    // authoritative. Redis and local cache utilities share one hash slot, so
    // loading Redis last would otherwise revive a deletion saved only locally.
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
    let redisLoadFinished = true;
    let redisLoadPromise = null;
    if (globals.redisValid && typeof getRedisCaches === 'function') {
      redisLoadFinished = false;
      redisLoadPromise = Promise.resolve(getRedisCaches()).finally(() => {
        redisLoadFinished = true;
      });
      await withTimeout(redisLoadPromise, INIT_TIMEOUT_MS, undefined);
    }

    const reconcileLocalFavoriteState = async (snapshot) => {
      if (snapshot.valid) {
        restoreFavorites(globals, snapshot.value, loadFavorites);
        if (globals.redisValid && typeof updateRedisCaches === 'function') {
          markFavoriteHashDirty(globals);
          await withTimeout(updateRedisCaches(), INIT_TIMEOUT_MS, undefined);
        }
      } else if (globals.localCacheValid && typeof updateLocalCaches === 'function') {
        if (snapshot.error) {
          log('[favorite] invalid local snapshot replaced from runtime state:', snapshot.error.message);
        }
        markFavoriteHashDirty(globals);
        await withTimeout(updateLocalCaches(), INIT_TIMEOUT_MS, undefined);
      }
    };
    await reconcileLocalFavoriteState(localSnapshot);

    deleteTracker = installFavoriteDeleteTracker(globals);
    redisBridge = startFavoriteRedisBridge({
      cacheFile: localSnapshot.file,
      globals,
      loadFavorites,
      updateRedisCaches,
      log,
    });

    if (redisLoadPromise && !redisLoadFinished) {
      void redisLoadPromise.then(async () => {
        if (disposed) return;
        await reconcileLocalFavoriteState(readLocalFavoriteSnapshot(projectDir));
        if (disposed) return;
        deleteTracker?.refresh();
      }).catch((error) => {
        log('[favorite] late Redis reconciliation failed:', error?.message || error);
      });
    }

    // A reload can reuse the same module instance. Always clear its previous
    // timer before installing the current host callback.
    stop();
    const refreshUrl = new URL(`http://127.0.0.1:${Number(port) || 9321}/api/v2/favorite/refresh`);
    await start({
      refresh: async (keyword) => {
        const deleteVersion = deleteTracker.version(keyword);
        const result = await refreshFavoriteByKeyword(keyword, refreshUrl, { persist: false });
        if (deleteTracker.version(keyword) !== deleteVersion) {
          deleteTracker.removeWithoutTracking(keyword);
          throw new Error('收藏已在定时刷新过程中删除');
        }
        return result;
      },
      persist: async () => {
        await persistFavorites();
        await redisBridge.sync();
      },
    });
    log('[favorite] scheduler started (Asia/Shanghai)');

    let stopped = false;
    return {
      supported: true,
      sync: redisBridge.sync,
      stop: () => {
        if (stopped) return;
        stopped = true;
        disposed = true;
        try { redisBridge?.stop(); } catch {}
        try { deleteTracker?.stop(); } catch {}
        try { stop(); } catch {}
      },
    };
  } catch (error) {
    disposed = true;
    log('[favorite] scheduler unavailable:', error?.message || error);
    try { redisBridge?.stop(); } catch {}
    try { deleteTracker?.stop(); } catch {}
    try { stop(); } catch {}
    return { supported: false, sync: async () => {}, stop: () => {} };
  }
}

module.exports = { startFavoriteSchedulerHost };
