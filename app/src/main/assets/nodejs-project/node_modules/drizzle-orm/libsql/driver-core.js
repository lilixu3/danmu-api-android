import { LibSQLSession } from "./session.js";
import { entityKind } from "../entity.js";
import { jitCompatCheck } from "../utils.js";
import { DefaultLogger } from "../logger.js";
import { SQLiteAsyncDatabase } from "../sqlite-core/async/db.js";
import { SQLiteDialect } from "../sqlite-core/dialect.js";

//#region src/libsql/driver-core.ts
var LibSQLDatabase = class extends SQLiteAsyncDatabase {
	static [entityKind] = "LibSQLDatabase";
	async batch(batch) {
		return this.session.batch(batch);
	}
};
/** @internal */
function construct(client, config = {}) {
	const dialect = new SQLiteDialect({ useJitMappers: jitCompatCheck(config.jit) });
	let logger;
	if (config.logger === true) logger = new DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new LibSQLDatabase("async", dialect, new LibSQLSession(client, dialect, relations, {
		logger,
		cache: config.cache
	}, void 0), relations);
	db.$client = client;
	db.$cache = config.cache;
	if (db.$cache) db.$cache["invalidate"] = config.cache?.onMutate;
	return db;
}

//#endregion
export { LibSQLDatabase, construct };
//# sourceMappingURL=driver-core.js.map