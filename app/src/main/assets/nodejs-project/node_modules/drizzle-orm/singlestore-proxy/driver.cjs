Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_singlestore_proxy_session = require('./session.cjs');
let __entity_ts = require("../entity.cjs");
let __utils_ts = require("../utils.cjs");
let ___relations_ts = require("../_relations.cjs");
let __logger_ts = require("../logger.cjs");
let __singlestore_core_db_ts = require("../singlestore-core/db.cjs");
let __singlestore_core_dialect_ts = require("../singlestore-core/dialect.cjs");

//#region src/singlestore-proxy/driver.ts
var SingleStoreRemoteDatabase = class extends __singlestore_core_db_ts.SingleStoreDatabase {
	static [__entity_ts.entityKind] = "SingleStoreRemoteDatabase";
};
function drizzle(callback, config = {}) {
	const dialect = new __singlestore_core_dialect_ts.SingleStoreDialect();
	let logger;
	if (config.logger === true) logger = new __logger_ts.DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	let schema;
	if (config.schema) {
		const tablesConfig = (0, ___relations_ts.extractTablesRelationalConfig)(config.schema, ___relations_ts.createTableRelationsHelpers);
		schema = {
			fullSchema: config.schema,
			schema: tablesConfig.tables,
			tableNamesMap: tablesConfig.tableNamesMap
		};
	}
	const relations = config.relations ?? {};
	return new SingleStoreRemoteDatabase(dialect, new require_singlestore_proxy_session.SingleStoreRemoteSession(callback, dialect, relations, schema, {
		logger,
		useJitMappers: (0, __utils_ts.jitCompatCheck)(config.jit)
	}), relations, schema);
}

//#endregion
exports.SingleStoreRemoteDatabase = SingleStoreRemoteDatabase;
exports.drizzle = drizzle;
//# sourceMappingURL=driver.cjs.map