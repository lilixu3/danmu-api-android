import { AutoPool } from "./pool.js";
import { NodeMsSqlSession } from "./session.js";
import { entityKind } from "../entity.js";
import { jitCompatCheck } from "../utils.js";
import * as V1 from "../_relations.js";
import { DefaultLogger } from "../logger.js";
import { MsSqlDialect } from "../mssql-core/dialect.js";
import { MsSqlDatabase, MsSqlDatabase as MsSqlDatabase$1 } from "../mssql-core/db.js";

//#region src/node-mssql/driver.ts
var NodeMsSqlDriver = class {
	static [entityKind] = "NodeMsSqlDriver";
	constructor(client, dialect, options = {}) {
		this.client = client;
		this.dialect = dialect;
		this.options = options;
	}
	createSession(schema) {
		return new NodeMsSqlSession(this.client, this.dialect, schema, {
			logger: this.options.logger,
			useJitMappers: this.options.useJitMappers
		});
	}
};
function construct(client, config = {}) {
	const dialect = new MsSqlDialect();
	let logger;
	if (config.logger === true) logger = new DefaultLogger();
	else if (config.logger !== false) logger = config.logger;
	if (isCallbackClient(client)) client = client.promise();
	let schema;
	if (config.schema) {
		const tablesConfig = V1.extractTablesRelationalConfig(config.schema, V1.createTableRelationsHelpers);
		schema = {
			fullSchema: config.schema,
			schema: tablesConfig.tables,
			tableNamesMap: tablesConfig.tableNamesMap
		};
	}
	const db = new MsSqlDatabase$1(dialect, new NodeMsSqlDriver(client, dialect, {
		logger,
		useJitMappers: jitCompatCheck(config.jit)
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
	if (typeof params[0] === "string") return construct(new AutoPool(getMsSqlConnectionParams(params[0])), params[1]);
	const { connection, client, ...drizzleConfig } = params[0];
	if (client) return construct(client, drizzleConfig);
	return construct(typeof connection === "string" ? new AutoPool(getMsSqlConnectionParams(connection)) : new AutoPool(connection), drizzleConfig);
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
export { MsSqlDatabase, NodeMsSqlDriver, drizzle, getMsSqlConnectionParams };
//# sourceMappingURL=driver.js.map