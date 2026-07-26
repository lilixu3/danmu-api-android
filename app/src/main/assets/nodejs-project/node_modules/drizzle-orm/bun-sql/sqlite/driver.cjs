Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_bun_sql_sqlite_session = require('./session.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");
let __logger_ts = require("../../logger.cjs");
let __sqlite_core_async_db_ts = require("../../sqlite-core/async/db.cjs");
let __sqlite_core_dialect_ts = require("../../sqlite-core/dialect.cjs");
let bun = require("bun");

//#region src/bun-sql/sqlite/driver.ts
var BunSQLiteDatabase = class extends __sqlite_core_async_db_ts.SQLiteAsyncDatabase {
	static [__entity_ts.entityKind] = "BunSQLiteDatabase";
};
function construct(client, config = {}) {
	const dialect = new __sqlite_core_dialect_ts.SQLiteDialect({ useJitMappers: (0, __utils_ts.jitCompatCheck)(config.jit) });
	let logger;
	if (config.logger === true) logger = new __logger_ts.DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new BunSQLiteDatabase("async", dialect, new require_bun_sql_sqlite_session.BunSQLiteSession(client, dialect, relations, {
		logger,
		cache: config.cache
	}), relations);
	db.$client = client;
	db.$cache = config.cache;
	if (db.$cache) db.$cache["invalidate"] = config.cache?.onMutate;
	return db;
}
function drizzle(...params) {
	if (typeof params[0] === "string") return construct(new bun.SQL(params[0]), params[1]);
	const { connection, client, ...DrizzleSQLiteConfig } = params[0];
	if (client) return construct(client, DrizzleSQLiteConfig);
	if (typeof connection === "object" && connection.url !== void 0) {
		const { url, ...config } = connection;
		return construct(new bun.SQL({
			url,
			...config
		}), DrizzleSQLiteConfig);
	}
	return construct(new bun.SQL(connection), DrizzleSQLiteConfig);
}
(function(_drizzle) {
	function mock(config) {
		return construct({ options: {
			parsers: {},
			serializers: {}
		} }, config);
	}
	_drizzle.mock = mock;
})(drizzle || (drizzle = {}));

//#endregion
exports.BunSQLiteDatabase = BunSQLiteDatabase;
Object.defineProperty(exports, 'drizzle', {
  enumerable: true,
  get: function () {
    return drizzle;
  }
});
//# sourceMappingURL=driver.cjs.map