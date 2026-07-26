Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_node_sqlite_session = require('./session.cjs');
let __entity_ts = require("../entity.cjs");
let __utils_ts = require("../utils.cjs");
let __logger_ts = require("../logger.cjs");
let __sqlite_core_async_db_ts = require("../sqlite-core/async/db.cjs");
let __sqlite_core_dialect_ts = require("../sqlite-core/dialect.cjs");
let node_sqlite = require("node:sqlite");

//#region src/node-sqlite/driver.ts
var NodeSQLiteDatabase = class extends __sqlite_core_async_db_ts.SQLiteAsyncDatabase {
	static [__entity_ts.entityKind] = "NodeSQLiteDatabase";
};
function construct(client, config = {}) {
	const dialect = new __sqlite_core_dialect_ts.SQLiteDialect({ useJitMappers: (0, __utils_ts.jitCompatCheck)(config.jit) });
	let logger;
	if (config.logger === true) logger = new __logger_ts.DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new NodeSQLiteDatabase("sync", dialect, new require_node_sqlite_session.NodeSQLiteSession(client, dialect, relations, { logger }), relations);
	db.$client = client;
	return db;
}
function drizzle(...params) {
	if (params[0] === void 0 || typeof params[0] === "string") return construct(params[0] === void 0 ? new node_sqlite.DatabaseSync(":memory:") : new node_sqlite.DatabaseSync(params[0]), params[1]);
	const { connection, client, ...config } = params[0];
	if (client) return construct(client, config);
	if (typeof connection === "object") {
		const { path, ...options } = connection;
		return construct(new node_sqlite.DatabaseSync(path ?? ":memory:", options), config);
	}
	return construct(new node_sqlite.DatabaseSync(connection ?? ":memory:"), config);
}
(function(_drizzle) {
	function mock(config) {
		return construct({}, config);
	}
	_drizzle.mock = mock;
})(drizzle || (drizzle = {}));

//#endregion
exports.NodeSQLiteDatabase = NodeSQLiteDatabase;
Object.defineProperty(exports, 'drizzle', {
  enumerable: true,
  get: function () {
    return drizzle;
  }
});
//# sourceMappingURL=driver.cjs.map