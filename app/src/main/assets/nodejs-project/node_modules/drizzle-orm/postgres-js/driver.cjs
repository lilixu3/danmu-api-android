Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_postgres_js_codecs = require('./codecs.cjs');
const require_postgres_js_session = require('./session.cjs');
let __entity_ts = require("../entity.cjs");
let __utils_ts = require("../utils.cjs");
let __logger_ts = require("../logger.cjs");
let __pg_core_async_db_ts = require("../pg-core/async/db.cjs");
let __pg_core_dialect_ts = require("../pg-core/dialect.cjs");
let postgres = require("postgres");
postgres = require_runtime.__toESM(postgres);

//#region src/postgres-js/driver.ts
var PostgresJsDatabase = class extends __pg_core_async_db_ts.PgAsyncDatabase {
	static [__entity_ts.entityKind] = "PostgresJsDatabase";
};
function construct(client, config = {}) {
	const transparentParser = (val) => val;
	for (const type of [
		"1184",
		"1082",
		"1083",
		"1114",
		"1182",
		"1185",
		"1115",
		"1231"
	]) {
		client.options.parsers[type] = transparentParser;
		client.options.serializers[type] = transparentParser;
	}
	client.options.serializers["114"] = transparentParser;
	client.options.serializers["3802"] = transparentParser;
	const dialect = new __pg_core_dialect_ts.PgDialect({
		useJitMappers: (0, __utils_ts.jitCompatCheck)(config.jit),
		codecs: config.codecs ?? require_postgres_js_codecs.postgresJsCodecs
	});
	let logger;
	if (config.logger === true) logger = new __logger_ts.DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new PostgresJsDatabase(dialect, new require_postgres_js_session.PostgresJsSession(client, dialect, relations, {
		logger,
		cache: config.cache
	}), relations);
	db.$client = client;
	db.$cache = config.cache;
	if (db.$cache) db.$cache["invalidate"] = config.cache?.onMutate;
	return db;
}
function drizzle(...params) {
	if (typeof params[0] === "string") return construct((0, postgres.default)(params[0]), params[1]);
	const { connection, client, ...DrizzlePgConfig } = params[0];
	if (client) return construct(client, DrizzlePgConfig);
	if (typeof connection === "object" && connection.url !== void 0) {
		const { url, ...config } = connection;
		return construct((0, postgres.default)(url, config), DrizzlePgConfig);
	}
	return construct((0, postgres.default)(connection), DrizzlePgConfig);
}
(function(_drizzle) {
	function mock(config) {
		return construct({ options: {
			parsers: {},
			serializers: {}
		} }, config);
	}
	_drizzle.mock = mock;
})(drizzle || (drizzle = {}));

//#endregion
exports.PostgresJsDatabase = PostgresJsDatabase;
Object.defineProperty(exports, 'drizzle', {
  enumerable: true,
  get: function () {
    return drizzle;
  }
});
//# sourceMappingURL=driver.cjs.map