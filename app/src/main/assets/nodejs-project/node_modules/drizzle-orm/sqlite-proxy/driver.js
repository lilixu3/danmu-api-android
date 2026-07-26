import { SQLiteRemoteSession } from "./session.js";
import { entityKind } from "../entity.js";
import { jitCompatCheck } from "../utils.js";
import { DefaultLogger } from "../logger.js";
import { SQLiteAsyncDatabase } from "../sqlite-core/async/db.js";
import { SQLiteDialect } from "../sqlite-core/dialect.js";

//#region src/sqlite-proxy/driver.ts
var SqliteRemoteDatabase = class extends SQLiteAsyncDatabase {
	static [entityKind] = "SqliteRemoteDatabase";
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
		if (_config.logger === true) logger = new DefaultLogger();
		else if (_config.logger !== false) {
			logger = _config.logger;
			cache = _config.cache;
		}
	}
	const dialect = new SQLiteDialect({ useJitMappers: jitCompatCheck(_config.jit) });
	const relations = _config.relations ?? {};
	const db = new SqliteRemoteDatabase("async", dialect, new SQLiteRemoteSession(callback, dialect, relations, _batchCallback, {
		logger,
		cache
	}), relations);
	db.$cache = cache;
	if (db.$cache) db.$cache["invalidate"] = cache?.onMutate;
	return db;
}

//#endregion
export { SqliteRemoteDatabase, drizzle };
//# sourceMappingURL=driver.js.map