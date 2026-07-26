import { postgresJsCodecs } from "./codecs.js";
import { PostgresJsSession } from "./session.js";
import { entityKind } from "../entity.js";
import { jitCompatCheck } from "../utils.js";
import { DefaultLogger } from "../logger.js";
import { PgAsyncDatabase } from "../pg-core/async/db.js";
import { PgDialect } from "../pg-core/dialect.js";
import pgClient from "postgres";

//#region src/postgres-js/driver.ts
var PostgresJsDatabase = class extends PgAsyncDatabase {
	static [entityKind] = "PostgresJsDatabase";
};
function construct(client, config = {}) {
	const transparentParser = (val) => val;
	for (const type of [
		"1184",
		"1082",
		"1083",
		"1114",
		"1182",
		"1185",
		"1115",
		"1231"
	]) {
		client.options.parsers[type] = transparentParser;
		client.options.serializers[type] = transparentParser;
	}
	client.options.serializers["114"] = transparentParser;
	client.options.serializers["3802"] = transparentParser;
	const dialect = new PgDialect({
		useJitMappers: jitCompatCheck(config.jit),
		codecs: config.codecs ?? postgresJsCodecs
	});
	let logger;
	if (config.logger === true) logger = new DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new PostgresJsDatabase(dialect, new PostgresJsSession(client, dialect, relations, {
		logger,
		cache: config.cache
	}), relations);
	db.$client = client;
	db.$cache = config.cache;
	if (db.$cache) db.$cache["invalidate"] = config.cache?.onMutate;
	return db;
}
function drizzle(...params) {
	if (typeof params[0] === "string") return construct(pgClient(params[0]), params[1]);
	const { connection, client, ...DrizzlePgConfig } = params[0];
	if (client) return construct(client, DrizzlePgConfig);
	if (typeof connection === "object" && connection.url !== void 0) {
		const { url, ...config } = connection;
		return construct(pgClient(url, config), DrizzlePgConfig);
	}
	return construct(pgClient(connection), DrizzlePgConfig);
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
export { PostgresJsDatabase, drizzle };
//# sourceMappingURL=driver.js.map