Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_sql_js_session = require('./session.cjs');
const require_sql_js_driver = require('./driver.cjs');

exports.SQLJsDatabase = require_sql_js_driver.SQLJsDatabase;
exports.SQLJsSession = require_sql_js_session.SQLJsSession;
exports.SQLJsTransaction = require_sql_js_session.SQLJsTransaction;
exports.drizzle = require_sql_js_driver.drizzle;