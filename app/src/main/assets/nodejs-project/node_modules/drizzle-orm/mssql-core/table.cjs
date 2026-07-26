Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_columns_all = require('./columns/all.cjs');
let __entity_ts = require("../entity.cjs");
let __table_ts = require("../table.cjs");
let __casing_ts = require("../casing.cjs");

//#region src/mssql-core/table.ts
/** @internal */
const InlineForeignKeys = Symbol.for("drizzle:MsSqlInlineForeignKeys");
var MsSqlTable = class extends __table_ts.Table {
	static [__entity_ts.entityKind] = "MsSqlTable";
	/** @internal */
	static Symbol = Object.assign({}, __table_ts.Table.Symbol, { InlineForeignKeys });
	/** @internal */
	[__table_ts.Table.Symbol.Columns];
	/** @internal */
	[InlineForeignKeys] = [];
	/** @internal */
	[__table_ts.Table.Symbol.ExtraConfigBuilder] = void 0;
};
function mssqlTableWithSchema(name, columns, extraConfig, schema, casing, baseName = name) {
	const casingFn = (0, __casing_ts.getCasingFn)(casing);
	const rawTable = new MsSqlTable(name, schema, baseName);
	const parsedColumns = typeof columns === "function" ? columns(require_mssql_core_columns_all.getMsSqlColumnBuilders()) : columns;
	const builtColumns = Object.fromEntries(Object.entries(parsedColumns).map(([name, colBuilderBase]) => {
		const colBuilder = colBuilderBase;
		colBuilder.setName(name, casingFn);
		const column = colBuilder.build(rawTable).postBuild();
		rawTable[InlineForeignKeys].push(...colBuilder.buildForeignKeys(column, rawTable));
		return [name, column];
	}));
	const table = Object.assign(rawTable, builtColumns);
	table[__table_ts.Table.Symbol.Columns] = builtColumns;
	table[__table_ts.Table.Symbol.ExtraConfigColumns] = builtColumns;
	if (extraConfig) table[MsSqlTable.Symbol.ExtraConfigBuilder] = extraConfig;
	return table;
}
function mssqlTableWithCasing(casing) {
	return (name, columns, extraConfig) => {
		return mssqlTableWithSchema(name, columns, extraConfig, void 0, casing, name);
	};
}
const mssqlTable = mssqlTableWithCasing(void 0);
function mssqlTableCreator(customizeTableName, casing) {
	return (name, columns, extraConfig) => {
		return mssqlTableWithSchema(customizeTableName(name), columns, extraConfig, void 0, casing, name);
	};
}

//#endregion
exports.InlineForeignKeys = InlineForeignKeys;
exports.MsSqlTable = MsSqlTable;
exports.mssqlTable = mssqlTable;
exports.mssqlTableCreator = mssqlTableCreator;
exports.mssqlTableWithCasing = mssqlTableWithCasing;
exports.mssqlTableWithSchema = mssqlTableWithSchema;
//# sourceMappingURL=table.cjs.map