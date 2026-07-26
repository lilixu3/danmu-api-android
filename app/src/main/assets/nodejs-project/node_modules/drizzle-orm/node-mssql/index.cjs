Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_node_mssql_session = require('./session.cjs');
const require_node_mssql_driver = require('./driver.cjs');
let __mssql_core_db_ts = require("../mssql-core/db.cjs");

Object.defineProperty(exports, 'MsSqlDatabase', {
  enumerable: true,
  get: function () {
    return __mssql_core_db_ts.MsSqlDatabase;
  }
});
exports.NodeMsSqlDriver = require_node_mssql_driver.NodeMsSqlDriver;
exports.NodeMsSqlPreparedQuery = require_node_mssql_session.NodeMsSqlPreparedQuery;
exports.NodeMsSqlSession = require_node_mssql_session.NodeMsSqlSession;
exports.NodeMsSqlTransaction = require_node_mssql_session.NodeMsSqlTransaction;
Object.defineProperty(exports, 'drizzle', {
  enumerable: true,
  get: function () {
    return require_node_mssql_driver.drizzle;
  }
});
exports.getMsSqlConnectionParams = require_node_mssql_driver.getMsSqlConnectionParams;