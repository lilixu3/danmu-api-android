import { SQLJsSession } from "./session.js";
import { entityKind } from "../entity.js";
import { jitCompatCheck } from "../utils.js";
import { DefaultLogger } from "../logger.js";
import { SQLiteAsyncDatabase } from "../sqlite-core/async/db.js";
import { SQLiteDialect } from "../sqlite-core/dialect.js";

//#region src/sql-js/driver.ts
var SQLJsDatabase = class extends SQLiteAsyncDatabase {
	static [entityKind] = "SQLJsDatabase";
};
function drizzle(client, config = {}) {
	const dialect = new SQLiteDialect({ useJitMappers: jitCompatCheck(config.jit) });
	let logger;
	if (config.logger === true) logger = new DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new SQLJsDatabase("sync", dialect, new SQLJsSession(client, dialect, relations, { logger }), relations);
	db.$client = client;
	return db;
}

//#endregion
export { SQLJsDatabase, drizzle };
//# sourceMappingURL=driver.js.map