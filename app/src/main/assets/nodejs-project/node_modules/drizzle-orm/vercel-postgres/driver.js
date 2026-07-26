import { vercelPgCodecs } from "./codecs.js";
import { VercelPgSession } from "./session.js";
import { entityKind } from "../entity.js";
import { isConfig, jitCompatCheck } from "../utils.js";
import { DefaultLogger } from "../logger.js";
import { PgAsyncDatabase } from "../pg-core/async/db.js";
import { PgDialect } from "../pg-core/dialect.js";
import { sql } from "@vercel/postgres";

//#region src/vercel-postgres/driver.ts
var VercelPgDatabase = class extends PgAsyncDatabase {
	static [entityKind] = "VercelPgDatabase";
};
function construct(client, config = {}) {
	const dialect = new PgDialect({
		useJitMappers: jitCompatCheck(config.jit),
		codecs: config.codecs ?? vercelPgCodecs
	});
	let logger;
	if (config.logger === true) logger = new DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new VercelPgDatabase(dialect, new VercelPgSession(client, dialect, relations ?? {}, {
		logger,
		cache: config.cache
	}), relations);
	db.$client = client;
	db.$cache = config.cache;
	if (db.$cache) db.$cache["invalidate"] = config.cache?.onMutate;
	return db;
}
function drizzle(...params) {
	if (isConfig(params[0])) {
		const { client, ...DrizzlePgConfig } = params[0];
		return construct(client ?? sql, DrizzlePgConfig);
	}
	return construct(params[0] ?? sql, params[1]);
}
(function(_drizzle) {
	function mock(config) {
		return construct({}, config);
	}
	_drizzle.mock = mock;
})(drizzle || (drizzle = {}));

//#endregion
export { VercelPgDatabase, drizzle };
//# sourceMappingURL=driver.js.map