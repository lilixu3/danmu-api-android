Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_tursodatabase_serverless_session = require('./session.cjs');
const require_tursodatabase_serverless_driver = require('./driver.cjs');

exports.TursoDatabaseServerlessDatabase = require_tursodatabase_serverless_driver.TursoDatabaseServerlessDatabase;
exports.TursoDatabaseServerlessSession = require_tursodatabase_serverless_session.TursoDatabaseServerlessSession;
exports.TursoDatabaseServerlessTransaction = require_tursodatabase_serverless_session.TursoDatabaseServerlessTransaction;
Object.defineProperty(exports, 'drizzle', {
  enumerable: true,
  get: function () {
    return require_tursodatabase_serverless_driver.drizzle;
  }
});