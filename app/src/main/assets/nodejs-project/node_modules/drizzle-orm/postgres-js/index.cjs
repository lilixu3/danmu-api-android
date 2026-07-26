Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_postgres_js_codecs = require('./codecs.cjs');
const require_postgres_js_session = require('./session.cjs');
const require_postgres_js_driver = require('./driver.cjs');

exports.PostgresJsDatabase = require_postgres_js_driver.PostgresJsDatabase;
exports.PostgresJsSession = require_postgres_js_session.PostgresJsSession;
exports.PostgresJsTransaction = require_postgres_js_session.PostgresJsTransaction;
Object.defineProperty(exports, 'drizzle', {
  enumerable: true,
  get: function () {
    return require_postgres_js_driver.drizzle;
  }
});
exports.postgresJsCodecs = require_postgres_js_codecs.postgresJsCodecs;