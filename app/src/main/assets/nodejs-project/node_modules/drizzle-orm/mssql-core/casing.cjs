Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_mssql_core_table = require('./table.cjs');
const require_mssql_core_view = require('./view.cjs');
const require_mssql_core_schema = require('./schema.cjs');

//#region src/mssql-core/casing.ts
const snakeCase = {
	table: require_mssql_core_table.mssqlTableWithCasing("snake_case"),
	view: require_mssql_core_view.mssqlViewWithCasing("snake_case"),
	schema: (name) => {
		return require_mssql_core_schema.mssqlSchema(name, "snake_case");
	}
};
const camelCase = {
	table: require_mssql_core_table.mssqlTableWithCasing("camelCase"),
	view: require_mssql_core_view.mssqlViewWithCasing("camelCase"),
	schema: (name) => {
		return require_mssql_core_schema.mssqlSchema(name, "camelCase");
	}
};

//#endregion
exports.camelCase = camelCase;
exports.snakeCase = snakeCase;
//# sourceMappingURL=casing.cjs.map