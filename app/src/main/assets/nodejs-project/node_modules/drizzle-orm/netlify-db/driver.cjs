Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_netlify_db_codecs = require('./codecs.cjs');
const require_netlify_db_session = require('./session.cjs');
let __entity_ts = require("../entity.cjs");
let __utils_ts = require("../utils.cjs");
let __logger_ts = require("../logger.cjs");
let __pg_core_async_db_ts = require("../pg-core/async/db.cjs");
let __pg_core_dialect_ts = require("../pg-core/dialect.cjs");
let _neondatabase_serverless = require("@neondatabase/serverless");
let __node_postgres_driver_ts = require("../node-postgres/driver.cjs");

//#region src/netlify-db/driver.ts
var NetlifyDbDatabase = class NetlifyDbDatabase extends __pg_core_async_db_ts.PgAsyncDatabase {
	static [__entity_ts.entityKind] = "NetlifyDbDatabase";
	$withAuth(token) {
		const session = new require_netlify_db_session.NetlifyDbSession(this.session.httpClient, this.session.pool, this.dialect, this._.relations, {
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
	const dialect = new __pg_core_dialect_ts.PgDialect({
		codecs: config.codecs ?? require_netlify_db_codecs.netlifyDbCodecs,
		useJitMappers: (0, __utils_ts.jitCompatCheck)(config.jit)
	});
	let logger;
	if (config.logger === true) logger = new __logger_ts.DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const session = new require_netlify_db_session.NetlifyDbSession(httpClient, pool, dialect, relations ?? {}, {
		logger,
		cache: config.cache,
		transactionCodecs: config.transactionCodecs ?? require_netlify_db_codecs.netlifyDbTransactionCodecs
	});
	_neondatabase_serverless.types.setTypeParser(_neondatabase_serverless.types.builtins.TIMESTAMPTZ, (val) => val);
	_neondatabase_serverless.types.setTypeParser(_neondatabase_serverless.types.builtins.TIMESTAMP, (val) => val);
	_neondatabase_serverless.types.setTypeParser(_neondatabase_serverless.types.builtins.DATE, (val) => val);
	_neondatabase_serverless.types.setTypeParser(_neondatabase_serverless.types.builtins.INTERVAL, (val) => val);
	_neondatabase_serverless.types.setTypeParser(1231, (val) => val);
	_neondatabase_serverless.types.setTypeParser(1115, (val) => val);
	_neondatabase_serverless.types.setTypeParser(1185, (val) => val);
	_neondatabase_serverless.types.setTypeParser(1187, (val) => val);
	_neondatabase_serverless.types.setTypeParser(1182, (val) => val);
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
	if (params.length === 0 || params.length === 1 && (0, __utils_ts.isConfig)(params[0]) && !("connection" in params[0]) && !("client" in params[0])) {
		const drizzleConfig = params[0] ?? {};
		const connectionString = process.env["NETLIFY_DB_URL"];
		if (!connectionString) throw new Error("NETLIFY_DB_URL environment variable is not set. Provide a connection string or client to drizzle().");
		if (process.env["NETLIFY_DB_DRIVER"] === "server") return (0, __node_postgres_driver_ts.drizzle)({
			connection: connectionString,
			...drizzleConfig
		});
		return construct((0, _neondatabase_serverless.neon)(connectionString), new _neondatabase_serverless.Pool({ connectionString }), drizzleConfig);
	}
	if (typeof params[0] === "string") {
		const connectionString = params[0];
		return construct((0, _neondatabase_serverless.neon)(connectionString), new _neondatabase_serverless.Pool({ connectionString }), params[1]);
	}
	if ((0, __utils_ts.isConfig)(params[0])) {
		const { connection, client, ...drizzleConfig } = params[0];
		if (client) {
			if ("driver" in client) {
				if (client.driver === "serverless") return construct(client.httpClient, client.pool, drizzleConfig);
				return (0, __node_postgres_driver_ts.drizzle)({
					client: client.pool,
					...drizzleConfig
				});
			}
			return construct(client.http, client.pool, drizzleConfig);
		}
		const connectionString = typeof connection === "string" ? connection : connection.connectionString;
		return construct((0, _neondatabase_serverless.neon)(connectionString), new _neondatabase_serverless.Pool({ connectionString }), drizzleConfig);
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
exports.NetlifyDbDatabase = NetlifyDbDatabase;
Object.defineProperty(exports, 'drizzle', {
  enumerable: true,
  get: function () {
    return drizzle;
  }
});
//# sourceMappingURL=driver.cjs.map