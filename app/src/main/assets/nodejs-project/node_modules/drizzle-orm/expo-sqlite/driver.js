import { ExpoSQLiteSession } from "./session.js";
import { entityKind } from "../entity.js";
import { jitCompatCheck } from "../utils.js";
import { DefaultLogger } from "../logger.js";
import { SQLiteAsyncDatabase } from "../sqlite-core/async/db.js";
import { SQLiteDialect } from "../sqlite-core/dialect.js";

//#region src/expo-sqlite/driver.ts
var ExpoSQLiteDatabase = class extends SQLiteAsyncDatabase {
	static [entityKind] = "ExpoSQLiteDatabase";
};
function drizzle(client, config = {}) {
	const dialect = new SQLiteDialect({ useJitMappers: jitCompatCheck(config.jit) });
	let logger;
	if (config.logger === true) logger = new DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new ExpoSQLiteDatabase("sync", dialect, new ExpoSQLiteSession(client, dialect, relations, { logger }), relations);
	db.$client = client;
	return db;
}

//#endregion
export { ExpoSQLiteDatabase, drizzle };
//# sourceMappingURL=driver.js.map