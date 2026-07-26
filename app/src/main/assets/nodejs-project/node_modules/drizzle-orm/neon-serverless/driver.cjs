Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_neon_serverless_codecs = require('./codecs.cjs');
const require_neon_serverless_session = require('./session.cjs');
let __entity_ts = require("../entity.cjs");
let __utils_ts = require("../utils.cjs");
let __logger_ts = require("../logger.cjs");
let __pg_core_async_db_ts = require("../pg-core/async/db.cjs");
let __pg_core_dialect_ts = require("../pg-core/dialect.cjs");
let _neondatabase_serverless = require("@neondatabase/serverless");

//#region src/neon-serverless/driver.ts
var NeonDatabase = class extends __pg_core_async_db_ts.PgAsyncDatabase {
	static [__entity_ts.entityKind] = "NeonServerlessDatabase";
};
function construct(client, config = {}) {
	const dialect = new __pg_core_dialect_ts.PgDialect({
		useJitMappers: (0, __utils_ts.jitCompatCheck)(config.jit),
		codecs: config.codecs ?? require_neon_serverless_codecs.neonServerlessCodecs
	});
	let logger;
	if (config.logger === true) logger = new __logger_ts.DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new NeonDatabase(dialect, new require_neon_serverless_session.NeonSession(client, dialect, relations, {
		logger,
		cache: config.cache
	}), relations);
	db.$client = client;
	db.$cache = config.cache;
	if (db.$cache) db.$cache["invalidate"] = config.cache?.onMutate;
	return db;
}
function drizzle(...params) {
	if (typeof params[0] === "string") return construct(new _neondatabase_serverless.Pool({ connectionString: params[0] }), params[1]);
	const { connection, client, ws, ...DrizzlePgConfig } = params[0];
	if (ws) _neondatabase_serverless.neonConfig.webSocketConstructor = ws;
	if (client) return construct(client, DrizzlePgConfig);
	return construct(typeof connection === "string" ? new _neondatabase_serverless.Pool({ connectionString: connection }) : new _neondatabase_serverless.Pool(connection), DrizzlePgConfig);
}
(function(_drizzle) {
	function mock(config) {
		return construct({}, config);
	}
	_drizzle.mock = mock;
})(drizzle || (drizzle = {}));

//#endregion
exports.NeonDatabase = NeonDatabase;
Object.defineProperty(exports, 'drizzle', {
  enumerable: true,
  get: function () {
    return drizzle;
  }
});
//# sourceMappingURL=driver.cjs.map