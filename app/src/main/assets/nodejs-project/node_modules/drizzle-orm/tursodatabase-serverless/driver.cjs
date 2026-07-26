Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_tursodatabase_serverless_session = require('./session.cjs');
let __entity_ts = require("../entity.cjs");
let __utils_ts = require("../utils.cjs");
let __logger_ts = require("../logger.cjs");
let __sqlite_core_async_db_ts = require("../sqlite-core/async/db.cjs");
let __sqlite_core_dialect_ts = require("../sqlite-core/dialect.cjs");
let _tursodatabase_serverless = require("@tursodatabase/serverless");

//#region src/tursodatabase-serverless/driver.ts
var TursoDatabaseServerlessDatabase = class extends __sqlite_core_async_db_ts.SQLiteAsyncDatabase {
	static [__entity_ts.entityKind] = "TursoDatabaseServerlessDatabase";
};
function construct(client, config = {}) {
	const dialect = new __sqlite_core_dialect_ts.SQLiteDialect({ useJitMappers: (0, __utils_ts.jitCompatCheck)(config.jit) });
	let logger;
	if (config.logger === true) logger = new __logger_ts.DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new TursoDatabaseServerlessDatabase("async", dialect, new require_tursodatabase_serverless_session.TursoDatabaseServerlessSession(client, dialect, relations, {
		logger,
		cache: config.cache
	}), relations);
	db.$client = client;
	db.$cache = config.cache;
	if (db.$cache) db.$cache["invalidate"] = config.cache?.onMutate;
	return db;
}
function drizzle(...params) {
	if (typeof params[0] === "string") return construct((0, _tursodatabase_serverless.connect)({ url: params[0] }), params[1]);
	const { connection, client, ...drizzleConfig } = params[0];
	if (client) return construct(client, drizzleConfig);
	return construct(typeof connection === "string" ? (0, _tursodatabase_serverless.connect)({ url: connection }) : (0, _tursodatabase_serverless.connect)(connection), drizzleConfig);
}
(function(_drizzle) {
	function mock(config) {
		return construct({}, config);
	}
	_drizzle.mock = mock;
})(drizzle || (drizzle = {}));

//#endregion
exports.TursoDatabaseServerlessDatabase = TursoDatabaseServerlessDatabase;
Object.defineProperty(exports, 'drizzle', {
  enumerable: true,
  get: function () {
    return drizzle;
  }
});
//# sourceMappingURL=driver.cjs.map