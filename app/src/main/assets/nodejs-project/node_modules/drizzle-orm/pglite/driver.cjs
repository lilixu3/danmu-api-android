Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_pglite_codecs = require('./codecs.cjs');
const require_pglite_session = require('./session.cjs');
let __entity_ts = require("../entity.cjs");
let __utils_ts = require("../utils.cjs");
let __logger_ts = require("../logger.cjs");
let __pg_core_async_db_ts = require("../pg-core/async/db.cjs");
let __pg_core_dialect_ts = require("../pg-core/dialect.cjs");
let _electric_sql_pglite = require("@electric-sql/pglite");

//#region src/pglite/driver.ts
var PgliteDatabase = class extends __pg_core_async_db_ts.PgAsyncDatabase {
	static [__entity_ts.entityKind] = "PgliteDatabase";
};
function construct(client, config = {}) {
	const dialect = new __pg_core_dialect_ts.PgDialect({
		useJitMappers: (0, __utils_ts.jitCompatCheck)(config.jit),
		codecs: config.codecs ?? require_pglite_codecs.pgliteCodecs
	});
	let logger;
	if (config.logger === true) logger = new __logger_ts.DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new PgliteDatabase(dialect, new require_pglite_session.PgliteSession(client, dialect, relations, {
		logger,
		cache: config.cache
	}), relations);
	db.$client = client;
	db.$cache = config.cache;
	if (db.$cache) db.$cache["invalidate"] = config.cache?.onMutate;
	return db;
}
function drizzle(...params) {
	if (params[0] === void 0 || typeof params[0] === "string") return construct(new _electric_sql_pglite.PGlite(params[0]), params[1]);
	const { connection, client, ...DrizzlePgConfig } = params[0];
	if (client) return construct(client, DrizzlePgConfig);
	if (typeof connection === "object") {
		const { dataDir, ...options } = connection;
		return construct(new _electric_sql_pglite.PGlite(dataDir, options), DrizzlePgConfig);
	}
	return construct(new _electric_sql_pglite.PGlite(connection), DrizzlePgConfig);
}
(function(_drizzle) {
	function mock(config) {
		return construct({}, config);
	}
	_drizzle.mock = mock;
})(drizzle || (drizzle = {}));

//#endregion
exports.PgliteDatabase = PgliteDatabase;
Object.defineProperty(exports, 'drizzle', {
  enumerable: true,
  get: function () {
    return drizzle;
  }
});
//# sourceMappingURL=driver.cjs.map