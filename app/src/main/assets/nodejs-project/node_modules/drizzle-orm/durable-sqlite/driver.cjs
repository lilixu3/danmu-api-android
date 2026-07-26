Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_durable_sqlite_session = require('./session.cjs');
let __entity_ts = require("../entity.cjs");
let __logger_ts = require("../logger.cjs");
let __sqlite_core_async_db_ts = require("../sqlite-core/async/db.cjs");
let __sqlite_core_dialect_ts = require("../sqlite-core/dialect.cjs");

//#region src/durable-sqlite/driver.ts
var DrizzleSqliteDODatabase = class extends __sqlite_core_async_db_ts.SQLiteAsyncDatabase {
	static [__entity_ts.entityKind] = "DrizzleSqliteDODatabase";
};
function drizzle(client, config = {}) {
	const dialect = new __sqlite_core_dialect_ts.SQLiteDialect();
	let logger;
	if (config.logger === true) logger = new __logger_ts.DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new DrizzleSqliteDODatabase("sync", dialect, new require_durable_sqlite_session.SQLiteDOSession(client, dialect, relations, { logger }), relations, true);
	db.$client = client;
	return db;
}

//#endregion
exports.DrizzleSqliteDODatabase = DrizzleSqliteDODatabase;
exports.drizzle = drizzle;
//# sourceMappingURL=driver.cjs.map