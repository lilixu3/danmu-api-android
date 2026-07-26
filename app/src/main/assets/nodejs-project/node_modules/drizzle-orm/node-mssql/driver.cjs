Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_node_mssql_pool = require('./pool.cjs');
const require_node_mssql_session = require('./session.cjs');
let __entity_ts = require("../entity.cjs");
let __utils_ts = require("../utils.cjs");
let ___relations_ts = require("../_relations.cjs");
___relations_ts = require_runtime.__toESM(___relations_ts);
let __logger_ts = require("../logger.cjs");
let __mssql_core_dialect_ts = require("../mssql-core/dialect.cjs");
let __mssql_core_db_ts = require("../mssql-core/db.cjs");

//#region src/node-mssql/driver.ts
var NodeMsSqlDriver = class {
	static [__entity_ts.entityKind] = "NodeMsSqlDriver";
	constructor(client, dialect, options = {}) {
		this.client = client;
		this.dialect = dialect;
		this.options = options;
	}
	createSession(schema) {
		return new require_node_mssql_session.NodeMsSqlSession(this.client, this.dialect, schema, {
			logger: this.options.logger,
			useJitMappers: this.options.useJitMappers
		});
	}
};
function construct(client, config = {}) {
	const dialect = new __mssql_core_dialect_ts.MsSqlDialect();
	let logger;
	if (config.logger === true) logger = new __logger_ts.DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	if (isCallbackClient(client)) client = client.promise();
	let schema;
	if (config.schema) {
		const tablesConfig = ___relations_ts.extractTablesRelationalConfig(config.schema, ___relations_ts.createTableRelationsHelpers);
		schema = {
			fullSchema: config.schema,
			schema: tablesConfig.tables,
			tableNamesMap: tablesConfig.tableNamesMap
		};
	}
	const db = new __mssql_core_db_ts.MsSqlDatabase(dialect, new NodeMsSqlDriver(client, dialect, {
		logger,
		useJitMappers: (0, __utils_ts.jitCompatCheck)(config.jit)
	}).createSession(schema), schema);
	db.$client = client;
	return db;
}
function getMsSqlConnectionParams(connectionString) {
	try {
		const url = new URL(connectionString);
		return {
			user: url.username,
			password: url.password,
			server: url.hostname,
			port: Number.parseInt(url.port, 10),
			database: url.pathname.replace(/^\//, ""),
			options: {
				encrypt: url.searchParams.get("encrypt") === "true",
				trustServerCertificate: url.searchParams.get("trustServerCertificate") === "true"
			}
		};
	} catch {
		return connectionString;
	}
}
function drizzle(...params) {
	if (typeof params[0] === "string") return construct(new require_node_mssql_pool.AutoPool(getMsSqlConnectionParams(params[0])), params[1]);
	const { connection, client, ...drizzleConfig } = params[0];
	if (client) return construct(client, drizzleConfig);
	return construct(typeof connection === "string" ? new require_node_mssql_pool.AutoPool(getMsSqlConnectionParams(connection)) : new require_node_mssql_pool.AutoPool(connection), drizzleConfig);
}
function isCallbackClient(client) {
	return typeof client.promise === "function";
}
(function(_drizzle) {
	function mock(config) {
		return construct({}, config);
	}
	_drizzle.mock = mock;
})(drizzle || (drizzle = {}));

//#endregion
Object.defineProperty(exports, 'MsSqlDatabase', {
  enumerable: true,
  get: function () {
    return __mssql_core_db_ts.MsSqlDatabase;
  }
});
exports.NodeMsSqlDriver = NodeMsSqlDriver;
Object.defineProperty(exports, 'drizzle', {
  enumerable: true,
  get: function () {
    return drizzle;
  }
});
exports.getMsSqlConnectionParams = getMsSqlConnectionParams;
//# sourceMappingURL=driver.cjs.map