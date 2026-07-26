Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_pg_core_table = require('./table.cjs');
const require_pg_core_view = require('./view.cjs');
const require_pg_core_schema = require('./schema.cjs');

//#region src/pg-core/casing.ts
const snakeCase = {
	table: require_pg_core_table.pgTableWithCasing("snake_case"),
	view: require_pg_core_view.pgViewWithCasing("snake_case"),
	materializedView: require_pg_core_view.pgMaterializedViewWithCasing("snake_case"),
	schema: (name) => {
		return require_pg_core_schema.pgSchema(name, "snake_case");
	}
};
const camelCase = {
	table: require_pg_core_table.pgTableWithCasing("camelCase"),
	view: require_pg_core_view.pgViewWithCasing("camelCase"),
	materializedView: require_pg_core_view.pgMaterializedViewWithCasing("camelCase"),
	schema: (name) => {
		return require_pg_core_schema.pgSchema(name, "camelCase");
	}
};

//#endregion
exports.camelCase = camelCase;
exports.snakeCase = snakeCase;
//# sourceMappingURL=casing.cjs.map