import { NodeSQLiteSession } from "./session.js";
import { entityKind } from "../entity.js";
import { jitCompatCheck } from "../utils.js";
import { DefaultLogger } from "../logger.js";
import { SQLiteAsyncDatabase } from "../sqlite-core/async/db.js";
import { SQLiteDialect } from "../sqlite-core/dialect.js";
import { DatabaseSync } from "node:sqlite";

//#region src/node-sqlite/driver.ts
var NodeSQLiteDatabase = class extends SQLiteAsyncDatabase {
	static [entityKind] = "NodeSQLiteDatabase";
};
function construct(client, config = {}) {
	const dialect = new SQLiteDialect({ useJitMappers: jitCompatCheck(config.jit) });
	let logger;
	if (config.logger === true) logger = new DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new NodeSQLiteDatabase("sync", dialect, new NodeSQLiteSession(client, dialect, relations, { logger }), relations);
	db.$client = client;
	return db;
}
function drizzle(...params) {
	if (params[0] === void 0 || typeof params[0] === "string") return construct(params[0] === void 0 ? new DatabaseSync(":memory:") : new DatabaseSync(params[0]), params[1]);
	const { connection, client, ...config } = params[0];
	if (client) return construct(client, config);
	if (typeof connection === "object") {
		const { path, ...options } = connection;
		return construct(new DatabaseSync(path ?? ":memory:", options), config);
	}
	return construct(new DatabaseSync(connection ?? ":memory:"), config);
}
(function(_drizzle) {
	function mock(config) {
		return construct({}, config);
	}
	_drizzle.mock = mock;
})(drizzle || (drizzle = {}));

//#endregion
export { NodeSQLiteDatabase, drizzle };
//# sourceMappingURL=driver.js.map