Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_pg_proxy_session = require('./session.cjs');
const require_pg_proxy_driver = require('./driver.cjs');

exports.PgRemoteDatabase = require_pg_proxy_driver.PgRemoteDatabase;
exports.PgRemoteSession = require_pg_proxy_session.PgRemoteSession;
exports.drizzle = require_pg_proxy_driver.drizzle;