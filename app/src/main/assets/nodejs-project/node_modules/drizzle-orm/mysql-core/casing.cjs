Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_mysql_core_table = require('./table.cjs');
const require_mysql_core_view = require('./view.cjs');
const require_mysql_core_schema = require('./schema.cjs');

//#region src/mysql-core/casing.ts
const snakeCase = {
	table: require_mysql_core_table.mysqlTableWithCasing("snake_case"),
	view: require_mysql_core_view.mysqlViewWithCasing("snake_case"),
	schema: (name) => {
		return require_mysql_core_schema.mysqlSchema(name, "snake_case");
	}
};
const camelCase = {
	table: require_mysql_core_table.mysqlTableWithCasing("camelCase"),
	view: require_mysql_core_view.mysqlViewWithCasing("camelCase"),
	schema: (name) => {
		return require_mysql_core_schema.mysqlSchema(name, "camelCase");
	}
};

//#endregion
exports.camelCase = camelCase;
exports.snakeCase = snakeCase;
//# sourceMappingURL=casing.cjs.map