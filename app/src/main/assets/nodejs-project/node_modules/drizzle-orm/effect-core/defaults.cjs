Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_effect_core_logger = require('./logger.cjs');
let __cache_core_cache_effect_ts = require("../cache/core/cache-effect.cjs");
let effect = require("effect");

//#region src/effect-core/defaults.ts
const DefaultServices = effect.Layer.merge(__cache_core_cache_effect_ts.EffectCache.Default, require_effect_core_logger.EffectLogger.Default);

//#endregion
exports.DefaultServices = DefaultServices;
//# sourceMappingURL=defaults.cjs.map