import { BunSQLiteSession } from "./session.js";
import { entityKind } from "../../entity.js";
import { jitCompatCheck } from "../../utils.js";
import { DefaultLogger } from "../../logger.js";
import { SQLiteAsyncDatabase } from "../../sqlite-core/async/db.js";
import { SQLiteDialect } from "../../sqlite-core/dialect.js";
import { SQL } from "bun";

//#region src/bun-sql/sqlite/driver.ts
var BunSQLiteDatabase = class extends SQLiteAsyncDatabase {
	static [entityKind] = "BunSQLiteDatabase";
};
function construct(client, config = {}) {
	const dialect = new SQLiteDialect({ useJitMappers: jitCompatCheck(config.jit) });
	let logger;
	if (config.logger === true) logger = new DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new BunSQLiteDatabase("async", dialect, new BunSQLiteSession(client, dialect, relations, {
		logger,
		cache: config.cache
	}), relations);
	db.$client = client;
	db.$cache = config.cache;
	if (db.$cache) db.$cache["invalidate"] = config.cache?.onMutate;
	return db;
}
function drizzle(...params) {
	if (typeof params[0] === "string") return construct(new SQL(params[0]), params[1]);
	const { connection, client, ...DrizzleSQLiteConfig } = params[0];
	if (client) return construct(client, DrizzleSQLiteConfig);
	if (typeof connection === "object" && connection.url !== void 0) {
		const { url, ...config } = connection;
		return construct(new SQL({
			url,
			...config
		}), DrizzleSQLiteConfig);
	}
	return construct(new SQL(connection), DrizzleSQLiteConfig);
}
(function(_drizzle) {
	function mock(config) {
		return construct({ options: {
			parsers: {},
			serializers: {}
		} }, config);
	}
	_drizzle.mock = mock;
})(drizzle || (drizzle = {}));

//#endregion
export { BunSQLiteDatabase, drizzle };
//# sourceMappingURL=driver.js.map