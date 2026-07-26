Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_sqlite_proxy_session = require('./session.cjs');
let __entity_ts = require("../entity.cjs");
let __utils_ts = require("../utils.cjs");
let __logger_ts = require("../logger.cjs");
let __sqlite_core_async_db_ts = require("../sqlite-core/async/db.cjs");
let __sqlite_core_dialect_ts = require("../sqlite-core/dialect.cjs");

//#region src/sqlite-proxy/driver.ts
var SqliteRemoteDatabase = class extends __sqlite_core_async_db_ts.SQLiteAsyncDatabase {
	static [__entity_ts.entityKind] = "SqliteRemoteDatabase";
	async batch(batch) {
		return this.session.batch(batch);
	}
};
function drizzle(callback, batchCallback, config) {
	let logger;
	let cache;
	let _batchCallback;
	let _config = {};
	if (batchCallback) {
		if (typeof batchCallback === "function") {
			_batchCallback = batchCallback;
			_config = config ?? {};
		} else {
			_batchCallback = void 0;
			_config = batchCallback;
		}
		if (_config.logger === true) logger = new __logger_ts.DefaultLogger();
		else if (_config.logger !== false) {
			logger = _config.logger;
			cache = _config.cache;
		}
	}
	const dialect = new __sqlite_core_dialect_ts.SQLiteDialect({ useJitMappers: (0, __utils_ts.jitCompatCheck)(_config.jit) });
	const relations = _config.relations ?? {};
	const db = new SqliteRemoteDatabase("async", dialect, new require_sqlite_proxy_session.SQLiteRemoteSession(callback, dialect, relations, _batchCallback, {
		logger,
		cache
	}), relations);
	db.$cache = cache;
	if (db.$cache) db.$cache["invalidate"] = cache?.onMutate;
	return db;
}

//#endregion
exports.SqliteRemoteDatabase = SqliteRemoteDatabase;
exports.drizzle = drizzle;
//# sourceMappingURL=driver.cjs.map