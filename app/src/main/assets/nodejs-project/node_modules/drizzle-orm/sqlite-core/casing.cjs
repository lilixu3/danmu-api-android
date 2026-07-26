Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_sqlite_core_table = require('./table.cjs');
const require_sqlite_core_view = require('./view.cjs');

//#region src/sqlite-core/casing.ts
const snakeCase = {
	table: require_sqlite_core_table.sqliteTableWithCasing("snake_case"),
	view: require_sqlite_core_view.sqliteViewWithCasing("snake_case")
};
const camelCase = {
	table: require_sqlite_core_table.sqliteTableWithCasing("camelCase"),
	view: require_sqlite_core_view.sqliteViewWithCasing("camelCase")
};

//#endregion
exports.camelCase = camelCase;
exports.snakeCase = snakeCase;
//# sourceMappingURL=casing.cjs.map