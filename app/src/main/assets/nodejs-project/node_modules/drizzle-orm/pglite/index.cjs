Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_pglite_codecs = require('./codecs.cjs');
const require_pglite_session = require('./session.cjs');
const require_pglite_driver = require('./driver.cjs');

exports.PgliteDatabase = require_pglite_driver.PgliteDatabase;
exports.PgliteSession = require_pglite_session.PgliteSession;
exports.PgliteTransaction = require_pglite_session.PgliteTransaction;
Object.defineProperty(exports, 'drizzle', {
  enumerable: true,
  get: function () {
    return require_pglite_driver.drizzle;
  }
});
exports.pgliteCodecs = require_pglite_codecs.pgliteCodecs;