import { neonHttpCodecs } from "./codecs.js";
import { NeonHttpSession } from "./session.js";
import { entityKind } from "../entity.js";
import { jitCompatCheck } from "../utils.js";
import { DefaultLogger } from "../logger.js";
import { PgAsyncDatabase } from "../pg-core/async/db.js";
import { PgDialect } from "../pg-core/dialect.js";
import { neon, types } from "@neondatabase/serverless";

//#region src/neon-http/driver.ts
var NeonHttpDatabase = class NeonHttpDatabase extends PgAsyncDatabase {
	static [entityKind] = "NeonHttpDatabase";
	$withAuth(token) {
		const session = new NeonHttpSession(this.session.client, this.dialect, this._.relations, {
			...this.session.options,
			authToken: token
		});
		return new NeonHttpDatabase(this.dialect, session, this._.relations);
	}
	async batch(batch) {
		return this.session.batch(batch);
	}
};
function construct(client, config = {}) {
	const dialect = new PgDialect({
		useJitMappers: jitCompatCheck(config.jit),
		codecs: config.codecs ?? neonHttpCodecs
	});
	let logger;
	if (config.logger === true) logger = new DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const session = new NeonHttpSession(client, dialect, relations ?? {}, {
		logger,
		cache: config.cache
	});
	types.setTypeParser(types.builtins.TIMESTAMPTZ, (val) => val);
	types.setTypeParser(types.builtins.TIMESTAMP, (val) => val);
	types.setTypeParser(types.builtins.DATE, (val) => val);
	types.setTypeParser(types.builtins.INTERVAL, (val) => val);
	types.setTypeParser(1231, (val) => val);
	types.setTypeParser(1115, (val) => val);
	types.setTypeParser(1185, (val) => val);
	types.setTypeParser(1187, (val) => val);
	types.setTypeParser(1182, (val) => val);
	const db = new NeonHttpDatabase(dialect, session, relations);
	db.$client = client;
	db.$cache = config.cache;
	if (db.$cache) db.$cache["invalidate"] = config.cache?.onMutate;
	return db;
}
function drizzle(...params) {
	if (typeof params[0] === "string") return construct(neon(params[0]), params[1]);
	const { connection, client, ...DrizzlePgConfig } = params[0];
	if (client) return construct(client, DrizzlePgConfig);
	if (typeof connection === "object") {
		const { connectionString, ...options } = connection;
		return construct(neon(connectionString, options), DrizzlePgConfig);
	}
	return construct(neon(connection), DrizzlePgConfig);
}
(function(_drizzle) {
	function mock(config) {
		return construct({}, config);
	}
	_drizzle.mock = mock;
})(drizzle || (drizzle = {}));

//#endregion
export { NeonHttpDatabase, drizzle };
//# sourceMappingURL=driver.js.map