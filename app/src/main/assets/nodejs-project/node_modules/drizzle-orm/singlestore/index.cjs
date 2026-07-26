Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_singlestore_session = require('./session.cjs');
const require_singlestore_driver = require('./driver.cjs');
let __singlestore_core_db_ts = require("../singlestore-core/db.cjs");

Object.defineProperty(exports, 'SingleStoreDatabase', {
  enumerable: true,
  get: function () {
    return __singlestore_core_db_ts.SingleStoreDatabase;
  }
});
exports.SingleStoreDriverDatabase = require_singlestore_driver.SingleStoreDriverDatabase;
exports.SingleStoreDriverDriver = require_singlestore_driver.SingleStoreDriverDriver;
exports.SingleStoreDriverPreparedQuery = require_singlestore_session.SingleStoreDriverPreparedQuery;
exports.SingleStoreDriverSession = require_singlestore_session.SingleStoreDriverSession;
exports.SingleStoreDriverTransaction = require_singlestore_session.SingleStoreDriverTransaction;
Object.defineProperty(exports, 'drizzle', {
  enumerable: true,
  get: function () {
    return require_singlestore_driver.drizzle;
  }
});