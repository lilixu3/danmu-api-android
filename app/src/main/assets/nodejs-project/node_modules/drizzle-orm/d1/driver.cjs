Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_d1_session = require('./session.cjs');
let __entity_ts = require("../entity.cjs");
let __logger_ts = require("../logger.cjs");
let __sqlite_core_async_db_ts = require("../sqlite-core/async/db.cjs");
let __sqlite_core_dialect_ts = require("../sqlite-core/dialect.cjs");

//#region src/d1/driver.ts
var DrizzleD1Database = class extends __sqlite_core_async_db_ts.SQLiteAsyncDatabase {
	static [__entity_ts.entityKind] = "D1Database";
	async batch(batch) {
		return this.session.batch(batch);
	}
};
function drizzle(client, config = {}) {
	const dialect = new __sqlite_core_dialect_ts.SQLiteDialect();
	let logger;
	if (config.logger === true) logger = new __logger_ts.DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new DrizzleD1Database("async", dialect, new require_d1_session.SQLiteD1Session(client, dialect, relations, {
		logger,
		cache: config.cache
	}), relations, true);
	db.$client = client;
	db.$cache = config.cache;
	if (db.$cache) db.$cache["invalidate"] = config.cache?.onMutate;
	return db;
}

//#endregion
exports.DrizzleD1Database = DrizzleD1Database;
exports.drizzle = drizzle;
//# sourceMappingURL=driver.cjs.map