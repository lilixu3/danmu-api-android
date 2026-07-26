Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_bun_sqlite_session = require('./session.cjs');
let __entity_ts = require("../entity.cjs");
let __utils_ts = require("../utils.cjs");
let __logger_ts = require("../logger.cjs");
let __sqlite_core_async_db_ts = require("../sqlite-core/async/db.cjs");
let __sqlite_core_dialect_ts = require("../sqlite-core/dialect.cjs");
let bun_sqlite = require("bun:sqlite");

//#region src/bun-sqlite/driver.ts
var SQLiteBunDatabase = class extends __sqlite_core_async_db_ts.SQLiteAsyncDatabase {
	static [__entity_ts.entityKind] = "SQLiteBunDatabase";
};
function construct(client, config = {}) {
	const dialect = new __sqlite_core_dialect_ts.SQLiteDialect({ useJitMappers: (0, __utils_ts.jitCompatCheck)(config.jit) });
	let logger;
	if (config.logger === true) logger = new __logger_ts.DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	const relations = config.relations ?? {};
	const db = new SQLiteBunDatabase("sync", dialect, new require_bun_sqlite_session.SQLiteBunSession(client, dialect, relations, { logger }), relations);
	db.$client = client;
	return db;
}
function drizzle(...params) {
	if (params[0] === void 0 || typeof params[0] === "string") return construct(params[0] === void 0 ? new bun_sqlite.Database() : new bun_sqlite.Database(params[0]), params[1]);
	const { connection, client, ...DrizzleSQLiteConfig } = params[0];
	if (client) return construct(client, DrizzleSQLiteConfig);
	if (typeof connection === "object") {
		const { source, ...opts } = connection;
		return construct(new bun_sqlite.Database(source, Object.values(opts).filter((v) => v !== void 0).length ? opts : void 0), DrizzleSQLiteConfig);
	}
	return construct(new bun_sqlite.Database(connection), DrizzleSQLiteConfig);
}
(function(_drizzle) {
	function mock(config) {
		return construct({}, config);
	}
	_drizzle.mock = mock;
})(drizzle || (drizzle = {}));

//#endregion
exports.SQLiteBunDatabase = SQLiteBunDatabase;
Object.defineProperty(exports, 'drizzle', {
  enumerable: true,
  get: function () {
    return drizzle;
  }
});
//# sourceMappingURL=driver.cjs.map