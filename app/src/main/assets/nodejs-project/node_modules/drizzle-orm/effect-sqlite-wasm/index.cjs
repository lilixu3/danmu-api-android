Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_effect_sqlite_wasm_session = require('./session.cjs');
const require_effect_sqlite_wasm_driver = require('./driver.cjs');
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
exports.EffectSQLiteWasmDatabase = require_effect_sqlite_wasm_driver.EffectSQLiteWasmDatabase;
exports.EffectSQLiteWasmSession = require_effect_sqlite_wasm_session.EffectSQLiteWasmSession;
exports.EffectSQLiteWasmTransaction = require_effect_sqlite_wasm_session.EffectSQLiteWasmTransaction;
exports.make = require_effect_sqlite_wasm_driver.make;
exports.makeWithDefaults = require_effect_sqlite_wasm_driver.makeWithDefaults;