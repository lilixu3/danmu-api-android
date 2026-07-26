Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_cache_core_cache = require('./cache.cjs');

exports.Cache = require_cache_core_cache.Cache;
exports.NoopCache = require_cache_core_cache.NoopCache;
exports.hashQuery = require_cache_core_cache.hashQuery;
exports.strategyFor = require_cache_core_cache.strategyFor;