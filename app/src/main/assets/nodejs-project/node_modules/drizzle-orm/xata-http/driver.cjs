Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_xata_http_codecs = require('./codecs.cjs');
const require_xata_http_session = require('./session.cjs');
let __entity_ts = require("../entity.cjs");
let __utils_ts = require("../utils.cjs");
let __logger_ts = require("../logger.cjs");
let __pg_core_async_db_ts = require("../pg-core/async/db.cjs");
let __pg_core_dialect_ts = require("../pg-core/dialect.cjs");

//#region src/xata-http/driver.ts
var XataHttpDatabase = class extends __pg_core_async_db_ts.PgAsyncDatabase {
	static [__entity_ts.entityKind] = "XataHttpDatabase";
};
function drizzle(client, config = {}) {
	const dialect = new __pg_core_dialect_ts.PgDialect({
		useJitMappers: (0, __utils_ts.jitCompatCheck)(config.jit),
		codecs: config.codecs ?? require_xata_http_codecs.xataHttpCodecs
	});
	let logger;
	if (config.logger === true) logger = new __logger_ts.DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new XataHttpDatabase(dialect, new require_xata_http_session.XataHttpSession(client, dialect, relations ?? {}, {
		logger,
		cache: config.cache
	}), relations);
	db.$client = client;
	db.$cache = config.cache;
	if (db.$cache) db.$cache["invalidate"] = config.cache?.onMutate;
	return db;
}

//#endregion
exports.XataHttpDatabase = XataHttpDatabase;
exports.drizzle = drizzle;
//# sourceMappingURL=driver.cjs.map