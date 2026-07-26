Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_mysql_core_columns_all = require('./columns/all.cjs');
let __entity_ts = require("../entity.cjs");
let __table_ts = require("../table.cjs");
let __casing_ts = require("../casing.cjs");

//#region src/mysql-core/table.ts
/** @internal */
const InlineForeignKeys = Symbol.for("drizzle:MySqlInlineForeignKeys");
var MySqlTable = class extends __table_ts.Table {
	static [__entity_ts.entityKind] = "MySqlTable";
	/** @internal */
	static Symbol = Object.assign({}, __table_ts.Table.Symbol, { InlineForeignKeys });
	/** @internal */
	[__table_ts.Table.Symbol.Columns];
	/** @internal */
	[InlineForeignKeys] = [];
	/** @internal */
	[__table_ts.Table.Symbol.ExtraConfigBuilder] = void 0;
};
function mysqlTableWithSchema(name, columns, extraConfig, schema, casing, baseName = name) {
	const casingFn = (0, __casing_ts.getCasingFn)(casing);
	const rawTable = new MySqlTable(name, schema, baseName);
	const parsedColumns = typeof columns === "function" ? columns(require_mysql_core_columns_all.getMySqlColumnBuilders()) : columns;
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
	if (extraConfig) table[MySqlTable.Symbol.ExtraConfigBuilder] = extraConfig;
	return table;
}
/** @internal */
function mysqlTableWithCasing(casing) {
	return (name, columns, extraConfig) => mysqlTableWithSchema(name, columns, extraConfig, void 0, casing, name);
}
const mysqlTable = mysqlTableWithCasing(void 0);
function mysqlTableCreator(customizeTableName, casing) {
	return (name, columns, extraConfig) => {
		return mysqlTableWithSchema(customizeTableName(name), columns, extraConfig, void 0, casing, name);
	};
}

//#endregion
exports.InlineForeignKeys = InlineForeignKeys;
exports.MySqlTable = MySqlTable;
exports.mysqlTable = mysqlTable;
exports.mysqlTableCreator = mysqlTableCreator;
exports.mysqlTableWithCasing = mysqlTableWithCasing;
exports.mysqlTableWithSchema = mysqlTableWithSchema;
//# sourceMappingURL=table.cjs.map