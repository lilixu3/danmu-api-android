import { neonServerlessCodecs } from "./codecs.js";
import { NeonSession } from "./session.js";
import { entityKind } from "../entity.js";
import { jitCompatCheck } from "../utils.js";
import { DefaultLogger } from "../logger.js";
import { PgAsyncDatabase } from "../pg-core/async/db.js";
import { PgDialect } from "../pg-core/dialect.js";
import { Pool, neonConfig } from "@neondatabase/serverless";

//#region src/neon-serverless/driver.ts
var NeonDatabase = class extends PgAsyncDatabase {
	static [entityKind] = "NeonServerlessDatabase";
};
function construct(client, config = {}) {
	const dialect = new PgDialect({
		useJitMappers: jitCompatCheck(config.jit),
		codecs: config.codecs ?? neonServerlessCodecs
	});
	let logger;
	if (config.logger === true) logger = new DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new NeonDatabase(dialect, new NeonSession(client, dialect, relations, {
		logger,
		cache: config.cache
	}), relations);
	db.$client = client;
	db.$cache = config.cache;
	if (db.$cache) db.$cache["invalidate"] = config.cache?.onMutate;
	return db;
}
function drizzle(...params) {
	if (typeof params[0] === "string") return construct(new Pool({ connectionString: params[0] }), params[1]);
	const { connection, client, ws, ...DrizzlePgConfig } = params[0];
	if (ws) neonConfig.webSocketConstructor = ws;
	if (client) return construct(client, DrizzlePgConfig);
	return construct(typeof connection === "string" ? new Pool({ connectionString: connection }) : new Pool(connection), DrizzlePgConfig);
}
(function(_drizzle) {
	function mock(config) {
		return construct({}, config);
	}
	_drizzle.mock = mock;
})(drizzle || (drizzle = {}));

//#endregion
export { NeonDatabase, drizzle };
//# sourceMappingURL=driver.js.map