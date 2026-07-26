import { bunSqlPgCodecs } from "./codecs.js";
import { BunSQLSession } from "./session.js";
import { entityKind } from "../../entity.js";
import { jitCompatCheck } from "../../utils.js";
import { DefaultLogger } from "../../logger.js";
import { PgAsyncDatabase } from "../../pg-core/async/db.js";
import { PgDialect } from "../../pg-core/dialect.js";
import { SQL } from "bun";

//#region src/bun-sql/postgres/driver.ts
var BunSQLDatabase = class extends PgAsyncDatabase {
	static [entityKind] = "BunSQLDatabase";
};
function construct(client, config = {}) {
	client.options.bigint = true;
	const dialect = new PgDialect({
		useJitMappers: jitCompatCheck(config.jit),
		codecs: config.codecs ?? bunSqlPgCodecs
	});
	let logger;
	if (config.logger === true) logger = new DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new BunSQLDatabase(dialect, new BunSQLSession(client, dialect, relations, {
		logger,
		cache: config.cache
	}), relations, false, true);
	db.$client = client;
	db.$cache = config.cache;
	if (db.$cache) db.$cache["invalidate"] = config.cache?.onMutate;
	return db;
}
function drizzle(...params) {
	if (typeof params[0] === "string") return construct(new SQL(params[0]), params[1]);
	const { connection, client, ...DrizzlePgConfig } = params[0];
	if (client) return construct(client, DrizzlePgConfig);
	if (typeof connection === "object" && connection.url !== void 0) {
		const { url, ...config } = connection;
		return construct(new SQL({
			url,
			...config
		}), DrizzlePgConfig);
	}
	return construct(new SQL(connection), DrizzlePgConfig);
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
export { BunSQLDatabase, drizzle };
//# sourceMappingURL=driver.js.map