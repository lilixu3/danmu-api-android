import { BetterSQLiteSession } from "./session.js";
import { entityKind } from "../entity.js";
import { jitCompatCheck } from "../utils.js";
import { DefaultLogger } from "../logger.js";
import { SQLiteAsyncDatabase } from "../sqlite-core/async/db.js";
import { SQLiteDialect } from "../sqlite-core/dialect.js";
import Client from "better-sqlite3";

//#region src/better-sqlite3/driver.ts
var BetterSQLite3Database = class extends SQLiteAsyncDatabase {
	static [entityKind] = "BetterSQLite3Database";
};
function construct(client, config = {}) {
	const dialect = new SQLiteDialect({ useJitMappers: jitCompatCheck(config.jit) });
	let logger;
	if (config.logger === true) logger = new DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new BetterSQLite3Database("sync", dialect, new BetterSQLiteSession(client, dialect, relations, { logger }), relations);
	db.$client = client;
	return db;
}
function drizzle(...params) {
	if (params[0] === void 0 || typeof params[0] === "string") return construct(params[0] === void 0 ? new Client() : new Client(params[0]), params[1]);
	const { connection, client, ...DrizzleSQLiteConfig } = params[0];
	if (client) return construct(client, DrizzleSQLiteConfig);
	if (typeof connection === "object") {
		const { source, ...options } = connection;
		return construct(new Client(source, options), DrizzleSQLiteConfig);
	}
	return construct(new Client(connection), DrizzleSQLiteConfig);
}
(function(_drizzle) {
	function mock(config) {
		return construct({}, config);
	}
	_drizzle.mock = mock;
})(drizzle || (drizzle = {}));

//#endregion
export { BetterSQLite3Database, drizzle };
//# sourceMappingURL=driver.js.map