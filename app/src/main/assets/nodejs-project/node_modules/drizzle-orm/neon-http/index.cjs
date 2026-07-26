Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_neon_http_codecs = require('./codecs.cjs');
const require_neon_http_session = require('./session.cjs');
const require_neon_http_driver = require('./driver.cjs');

exports.NeonHttpDatabase = require_neon_http_driver.NeonHttpDatabase;
exports.NeonHttpSession = require_neon_http_session.NeonHttpSession;
Object.defineProperty(exports, 'drizzle', {
  enumerable: true,
  get: function () {
    return require_neon_http_driver.drizzle;
  }
});
exports.neonHttpCodecs = require_neon_http_codecs.neonHttpCodecs;