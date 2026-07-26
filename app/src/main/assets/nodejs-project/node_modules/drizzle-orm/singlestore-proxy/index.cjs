Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_singlestore_proxy_session = require('./session.cjs');
const require_singlestore_proxy_driver = require('./driver.cjs');

exports.PreparedQuery = require_singlestore_proxy_session.PreparedQuery;
exports.SingleStoreProxyTransaction = require_singlestore_proxy_session.SingleStoreProxyTransaction;
exports.SingleStoreRemoteDatabase = require_singlestore_proxy_driver.SingleStoreRemoteDatabase;
exports.SingleStoreRemoteSession = require_singlestore_proxy_session.SingleStoreRemoteSession;
exports.drizzle = require_singlestore_proxy_driver.drizzle;