Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_effect_pglite_codecs = require('./codecs.cjs');
const require_effect_pglite_session = require('./session.cjs');
const require_effect_pglite_driver = require('./driver.cjs');
let __effect_core_index_ts = require("../effect-core/index.cjs");
let __effect_core_defaults_ts = require("../effect-core/defaults.cjs");

Object.defineProperty(exports, 'DefaultServices', {
  enumerable: true,
  get: function () {
    return __effect_core_defaults_ts.DefaultServices;
  }
});
Object.defineProperty(exports, 'EffectLogger', {
  enumerable: true,
  get: function () {
    return __effect_core_index_ts.EffectLogger;
  }
});
exports.EffectPgDatabase = require_effect_pglite_driver.EffectPgDatabase;
exports.EffectPgSession = require_effect_pglite_session.EffectPgSession;
exports.EffectPgTransaction = require_effect_pglite_session.EffectPgTransaction;
exports.effectPgliteCodecs = require_effect_pglite_codecs.effectPgliteCodecs;
exports.make = require_effect_pglite_driver.make;
exports.makeWithDefaults = require_effect_pglite_driver.makeWithDefaults;