Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_singlestore_core_table = require('./table.cjs');
const require_singlestore_core_schema = require('./schema.cjs');

//#region src/singlestore-core/casing.ts
const snakeCase = {
	table: require_singlestore_core_table.singlestoreTableWithCasing("snake_case"),
	schema: (name) => {
		return require_singlestore_core_schema.singlestoreSchema(name, "snake_case");
	}
};
const camelCase = {
	table: require_singlestore_core_table.singlestoreTableWithCasing("camelCase"),
	schema: (name) => {
		return require_singlestore_core_schema.singlestoreSchema(name, "camelCase");
	}
};

//#endregion
exports.camelCase = camelCase;
exports.snakeCase = snakeCase;
//# sourceMappingURL=casing.cjs.map