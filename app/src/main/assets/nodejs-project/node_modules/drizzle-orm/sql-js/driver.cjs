Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_sql_js_session = require('./session.cjs');
let __entity_ts = require("../entity.cjs");
let __utils_ts = require("../utils.cjs");
let __logger_ts = require("../logger.cjs");
let __sqlite_core_async_db_ts = require("../sqlite-core/async/db.cjs");
let __sqlite_core_dialect_ts = require("../sqlite-core/dialect.cjs");

//#region src/sql-js/driver.ts
var SQLJsDatabase = class extends __sqlite_core_async_db_ts.SQLiteAsyncDatabase {
	static [__entity_ts.entityKind] = "SQLJsDatabase";
};
function drizzle(client, config = {}) {
	const dialect = new __sqlite_core_dialect_ts.SQLiteDialect({ useJitMappers: (0, __utils_ts.jitCompatCheck)(config.jit) });
	let logger;
	if (config.logger === true) logger = new __logger_ts.DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new SQLJsDatabase("sync", dialect, new require_sql_js_session.SQLJsSession(client, dialect, relations, { logger }), relations);
	db.$client = client;
	return db;
}

//#endregion
exports.SQLJsDatabase = SQLJsDatabase;
exports.drizzle = drizzle;
//# sourceMappingURL=driver.cjs.map