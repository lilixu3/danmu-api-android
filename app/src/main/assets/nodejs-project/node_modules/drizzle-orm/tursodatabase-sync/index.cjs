Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_tursodatabase_sync_session = require('./session.cjs');
const require_tursodatabase_sync_driver = require('./driver.cjs');

exports.TursoDatabaseSyncDatabase = require_tursodatabase_sync_driver.TursoDatabaseSyncDatabase;
exports.TursoDatabaseSyncSession = require_tursodatabase_sync_session.TursoDatabaseSyncSession;
exports.TursoDatabaseSyncTransaction = require_tursodatabase_sync_session.TursoDatabaseSyncTransaction;
Object.defineProperty(exports, 'drizzle', {
  enumerable: true,
  get: function () {
    return require_tursodatabase_sync_driver.drizzle;
  }
});