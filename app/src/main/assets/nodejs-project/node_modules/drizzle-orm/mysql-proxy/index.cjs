Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_mysql_proxy_session = require('./session.cjs');
const require_mysql_proxy_driver = require('./driver.cjs');

exports.MySqlRemoteDatabase = require_mysql_proxy_driver.MySqlRemoteDatabase;
exports.MySqlRemoteSession = require_mysql_proxy_session.MySqlRemoteSession;
exports.drizzle = require_mysql_proxy_driver.drizzle;