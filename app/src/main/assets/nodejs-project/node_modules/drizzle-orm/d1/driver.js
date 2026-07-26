import { SQLiteD1Session } from "./session.js";
import { entityKind } from "../entity.js";
import { DefaultLogger } from "../logger.js";
import { SQLiteAsyncDatabase } from "../sqlite-core/async/db.js";
import { SQLiteDialect } from "../sqlite-core/dialect.js";

//#region src/d1/driver.ts
var DrizzleD1Database = class extends SQLiteAsyncDatabase {
	static [entityKind] = "D1Database";
	async batch(batch) {
		return this.session.batch(batch);
	}
};
function drizzle(client, config = {}) {
	const dialect = new SQLiteDialect();
	let logger;
	if (config.logger === true) logger = new DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new DrizzleD1Database("async", dialect, new SQLiteD1Session(client, dialect, relations, {
		logger,
		cache: config.cache
	}), relations, true);
	db.$client = client;
	db.$cache = config.cache;
	if (db.$cache) db.$cache["invalidate"] = config.cache?.onMutate;
	return db;
}

//#endregion
export { DrizzleD1Database, drizzle };
//# sourceMappingURL=driver.js.map