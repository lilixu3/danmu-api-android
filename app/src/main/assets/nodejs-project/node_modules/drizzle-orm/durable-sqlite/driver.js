import { SQLiteDOSession } from "./session.js";
import { entityKind } from "../entity.js";
import { DefaultLogger } from "../logger.js";
import { SQLiteAsyncDatabase } from "../sqlite-core/async/db.js";
import { SQLiteDialect } from "../sqlite-core/dialect.js";

//#region src/durable-sqlite/driver.ts
var DrizzleSqliteDODatabase = class extends SQLiteAsyncDatabase {
	static [entityKind] = "DrizzleSqliteDODatabase";
};
function drizzle(client, config = {}) {
	const dialect = new SQLiteDialect();
	let logger;
	if (config.logger === true) logger = new DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new DrizzleSqliteDODatabase("sync", dialect, new SQLiteDOSession(client, dialect, relations, { logger }), relations, true);
	db.$client = client;
	return db;
}

//#endregion
export { DrizzleSqliteDODatabase, drizzle };
//# sourceMappingURL=driver.js.map