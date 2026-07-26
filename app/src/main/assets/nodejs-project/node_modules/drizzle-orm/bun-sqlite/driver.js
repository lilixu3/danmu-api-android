import { SQLiteBunSession } from "./session.js";
import { entityKind } from "../entity.js";
import { jitCompatCheck } from "../utils.js";
import { DefaultLogger } from "../logger.js";
import { SQLiteAsyncDatabase } from "../sqlite-core/async/db.js";
import { SQLiteDialect } from "../sqlite-core/dialect.js";
import { Database } from "bun:sqlite";

//#region src/bun-sqlite/driver.ts
var SQLiteBunDatabase = class extends SQLiteAsyncDatabase {
	static [entityKind] = "SQLiteBunDatabase";
};
function construct(client, config = {}) {
	const dialect = new SQLiteDialect({ useJitMappers: jitCompatCheck(config.jit) });
	let logger;
	if (config.logger === true) logger = new DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new SQLiteBunDatabase("sync", dialect, new SQLiteBunSession(client, dialect, relations, { logger }), relations);
	db.$client = client;
	return db;
}
function drizzle(...params) {
	if (params[0] === void 0 || typeof params[0] === "string") return construct(params[0] === void 0 ? new Database() : new Database(params[0]), params[1]);
	const { connection, client, ...DrizzleSQLiteConfig } = params[0];
	if (client) return construct(client, DrizzleSQLiteConfig);
	if (typeof connection === "object") {
		const { source, ...opts } = connection;
		return construct(new Database(source, Object.values(opts).filter((v) => v !== void 0).length ? opts : void 0), DrizzleSQLiteConfig);
	}
	return construct(new Database(connection), DrizzleSQLiteConfig);
}
(function(_drizzle) {
	function mock(config) {
		return construct({}, config);
	}
	_drizzle.mock = mock;
})(drizzle || (drizzle = {}));

//#endregion
export { SQLiteBunDatabase, drizzle };
//# sourceMappingURL=driver.js.map