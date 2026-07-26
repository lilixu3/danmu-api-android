Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_cockroach_core_table = require('./table.cjs');
const require_cockroach_core_view = require('./view.cjs');
const require_cockroach_core_schema = require('./schema.cjs');

//#region src/cockroach-core/casing.ts
const snakeCase = {
	table: require_cockroach_core_table.cockroachTableWithCasing("snake_case"),
	view: require_cockroach_core_view.cockroachViewWithCasing("snake_case"),
	materializedView: require_cockroach_core_view.cockroachMaterializedViewWithCasing("snake_case"),
	schema: (name) => {
		return require_cockroach_core_schema.cockroachSchema(name, "snake_case");
	}
};
const camelCase = {
	table: require_cockroach_core_table.cockroachTableWithCasing("camelCase"),
	view: require_cockroach_core_view.cockroachViewWithCasing("camelCase"),
	materializedView: require_cockroach_core_view.cockroachMaterializedViewWithCasing("camelCase"),
	schema: (name) => {
		return require_cockroach_core_schema.cockroachSchema(name, "camelCase");
	}
};

//#endregion
exports.camelCase = camelCase;
exports.snakeCase = snakeCase;
//# sourceMappingURL=casing.cjs.map