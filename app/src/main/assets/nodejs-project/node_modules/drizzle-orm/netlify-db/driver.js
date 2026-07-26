import { netlifyDbCodecs, netlifyDbTransactionCodecs } from "./codecs.js";
import { NetlifyDbSession } from "./session.js";
import { entityKind } from "../entity.js";
import { isConfig, jitCompatCheck } from "../utils.js";
import { DefaultLogger } from "../logger.js";
import { PgAsyncDatabase } from "../pg-core/async/db.js";
import { PgDialect } from "../pg-core/dialect.js";
import { Pool, neon, types } from "@neondatabase/serverless";
import { drizzle as drizzle$1 } from "../node-postgres/driver.js";

//#region src/netlify-db/driver.ts
var NetlifyDbDatabase = class NetlifyDbDatabase extends PgAsyncDatabase {
	static [entityKind] = "NetlifyDbDatabase";
	$withAuth(token) {
		const session = new NetlifyDbSession(this.session.httpClient, this.session.pool, this.dialect, this._.relations, {
			...this.session.options,
			authToken: token
		});
		return new NetlifyDbDatabase(this.dialect, session, this._.relations);
	}
	async batch(batch) {
		return this.session.batch(batch);
	}
};
function construct(httpClient, pool, config = {}) {
	const dialect = new PgDialect({
		codecs: config.codecs ?? netlifyDbCodecs,
		useJitMappers: jitCompatCheck(config.jit)
	});
	let logger;
	if (config.logger === true) logger = new DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const session = new NetlifyDbSession(httpClient, pool, dialect, relations ?? {}, {
		logger,
		cache: config.cache,
		transactionCodecs: config.transactionCodecs ?? netlifyDbTransactionCodecs
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
	const db = new NetlifyDbDatabase(dialect, session, relations);
	db.$client = {
		http: httpClient,
		pool
	};
	db.$cache = config.cache;
	if (db.$cache) db.$cache["invalidate"] = config.cache?.onMutate;
	return db;
}
function drizzle(...params) {
	if (params.length === 0 || params.length === 1 && isConfig(params[0]) && !("connection" in params[0]) && !("client" in params[0])) {
		const drizzleConfig = params[0] ?? {};
		const connectionString = process.env["NETLIFY_DB_URL"];
		if (!connectionString) throw new Error("NETLIFY_DB_URL environment variable is not set. Provide a connection string or client to drizzle().");
		if (process.env["NETLIFY_DB_DRIVER"] === "server") return drizzle$1({
			connection: connectionString,
			...drizzleConfig
		});
		return construct(neon(connectionString), new Pool({ connectionString }), drizzleConfig);
	}
	if (typeof params[0] === "string") {
		const connectionString = params[0];
		return construct(neon(connectionString), new Pool({ connectionString }), params[1]);
	}
	if (isConfig(params[0])) {
		const { connection, client, ...drizzleConfig } = params[0];
		if (client) {
			if ("driver" in client) {
				if (client.driver === "serverless") return construct(client.httpClient, client.pool, drizzleConfig);
				return drizzle$1({
					client: client.pool,
					...drizzleConfig
				});
			}
			return construct(client.http, client.pool, drizzleConfig);
		}
		const connectionString = typeof connection === "string" ? connection : connection.connectionString;
		return construct(neon(connectionString), new Pool({ connectionString }), drizzleConfig);
	}
	throw new Error("Invalid arguments. Expected a connection string or a config object.");
}
(function(_drizzle) {
	function mock(config) {
		return construct({}, {}, config);
	}
	_drizzle.mock = mock;
})(drizzle || (drizzle = {}));

//#endregion
export { NetlifyDbDatabase, drizzle };
//# sourceMappingURL=driver.js.map