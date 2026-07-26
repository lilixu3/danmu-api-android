import { xataHttpCodecs } from "./codecs.js";
import { XataHttpSession } from "./session.js";
import { entityKind } from "../entity.js";
import { jitCompatCheck } from "../utils.js";
import { DefaultLogger } from "../logger.js";
import { PgAsyncDatabase } from "../pg-core/async/db.js";
import { PgDialect } from "../pg-core/dialect.js";

//#region src/xata-http/driver.ts
var XataHttpDatabase = class extends PgAsyncDatabase {
	static [entityKind] = "XataHttpDatabase";
};
function drizzle(client, config = {}) {
	const dialect = new PgDialect({
		useJitMappers: jitCompatCheck(config.jit),
		codecs: config.codecs ?? xataHttpCodecs
	});
	let logger;
	if (config.logger === true) logger = new DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new XataHttpDatabase(dialect, new XataHttpSession(client, dialect, relations ?? {}, {
		logger,
		cache: config.cache
	}), relations);
	db.$client = client;
	db.$cache = config.cache;
	if (db.$cache) db.$cache["invalidate"] = config.cache?.onMutate;
	return db;
}

//#endregion
export { XataHttpDatabase, drizzle };
//# sourceMappingURL=driver.js.map