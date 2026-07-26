Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_neon_http_codecs = require('./codecs.cjs');
const require_neon_http_session = require('./session.cjs');
let __entity_ts = require("../entity.cjs");
let __utils_ts = require("../utils.cjs");
let __logger_ts = require("../logger.cjs");
let __pg_core_async_db_ts = require("../pg-core/async/db.cjs");
let __pg_core_dialect_ts = require("../pg-core/dialect.cjs");
let _neondatabase_serverless = require("@neondatabase/serverless");

//#region src/neon-http/driver.ts
var NeonHttpDatabase = class NeonHttpDatabase extends __pg_core_async_db_ts.PgAsyncDatabase {
	static [__entity_ts.entityKind] = "NeonHttpDatabase";
	$withAuth(token) {
		const session = new require_neon_http_session.NeonHttpSession(this.session.client, this.dialect, this._.relations, {
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
	const dialect = new __pg_core_dialect_ts.PgDialect({
		useJitMappers: (0, __utils_ts.jitCompatCheck)(config.jit),
		codecs: config.codecs ?? require_neon_http_codecs.neonHttpCodecs
	});
	let logger;
	if (config.logger === true) logger = new __logger_ts.DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const session = new require_neon_http_session.NeonHttpSession(client, dialect, relations ?? {}, {
		logger,
		cache: config.cache
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
	const db = new NeonHttpDatabase(dialect, session, relations);
	db.$client = client;
	db.$cache = config.cache;
	if (db.$cache) db.$cache["invalidate"] = config.cache?.onMutate;
	return db;
}
function drizzle(...params) {
	if (typeof params[0] === "string") return construct((0, _neondatabase_serverless.neon)(params[0]), params[1]);
	const { connection, client, ...DrizzlePgConfig } = params[0];
	if (client) return construct(client, DrizzlePgConfig);
	if (typeof connection === "object") {
		const { connectionString, ...options } = connection;
		return construct((0, _neondatabase_serverless.neon)(connectionString, options), DrizzlePgConfig);
	}
	return construct((0, _neondatabase_serverless.neon)(connection), DrizzlePgConfig);
}
(function(_drizzle) {
	function mock(config) {
		return construct({}, config);
	}
	_drizzle.mock = mock;
})(drizzle || (drizzle = {}));

//#endregion
exports.NeonHttpDatabase = NeonHttpDatabase;
Object.defineProperty(exports, 'drizzle', {
  enumerable: true,
  get: function () {
    return drizzle;
  }
});
//# sourceMappingURL=driver.cjs.map