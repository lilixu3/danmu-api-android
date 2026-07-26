Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_all = require('./columns/all.cjs');
let __entity_ts = require("../entity.cjs");
let __table_ts = require("../table.cjs");
let __casing_ts = require("../casing.cjs");

//#region src/singlestore-core/table.ts
var SingleStoreTable = class extends __table_ts.Table {
	static [__entity_ts.entityKind] = "SingleStoreTable";
	/** @internal */
	static Symbol = Object.assign({}, __table_ts.Table.Symbol, {});
	/** @internal */
	[__table_ts.Table.Symbol.Columns];
	/** @internal */
	[__table_ts.Table.Symbol.ExtraConfigBuilder] = void 0;
};
function singlestoreTableWithSchema(name, columns, extraConfig, schema, casing, baseName = name) {
	const casingFn = (0, __casing_ts.getCasingFn)(casing);
	const rawTable = new SingleStoreTable(name, schema, baseName);
	const parsedColumns = typeof columns === "function" ? columns(require_singlestore_core_columns_all.getSingleStoreColumnBuilders()) : columns;
	const builtColumns = Object.fromEntries(Object.entries(parsedColumns).map(([name, colBuilderBase]) => {
		const colBuilder = colBuilderBase;
		colBuilder.setName(name, casingFn);
		return [name, colBuilder.build(rawTable).postBuild()];
	}));
	const table = Object.assign(rawTable, builtColumns);
	table[__table_ts.Table.Symbol.Columns] = builtColumns;
	table[__table_ts.Table.Symbol.ExtraConfigColumns] = builtColumns;
	if (extraConfig) table[SingleStoreTable.Symbol.ExtraConfigBuilder] = extraConfig;
	return table;
}
/** @internal */
function singlestoreTableWithCasing(casing) {
	return (name, columns, extraConfig) => singlestoreTableWithSchema(name, columns, extraConfig, void 0, casing, name);
}
const singlestoreTable = singlestoreTableWithCasing(void 0);
function singlestoreTableCreator(customizeTableName, casing) {
	return (name, columns, extraConfig) => {
		return singlestoreTableWithSchema(customizeTableName(name), columns, extraConfig, void 0, casing, name);
	};
}

//#endregion
exports.SingleStoreTable = SingleStoreTable;
exports.singlestoreTable = singlestoreTable;
exports.singlestoreTableCreator = singlestoreTableCreator;
exports.singlestoreTableWithCasing = singlestoreTableWithCasing;
exports.singlestoreTableWithSchema = singlestoreTableWithSchema;
//# sourceMappingURL=table.cjs.map