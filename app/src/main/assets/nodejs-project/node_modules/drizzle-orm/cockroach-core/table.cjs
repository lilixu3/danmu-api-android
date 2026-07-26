Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_all = require('./columns/all.cjs');
let __entity_ts = require("../entity.cjs");
let __table_ts = require("../table.cjs");
let __casing_ts = require("../casing.cjs");

//#region src/cockroach-core/table.ts
/** @internal */
const InlineForeignKeys = Symbol.for("drizzle:CockroachInlineForeignKeys");
/** @internal */
const EnableRLS = Symbol.for("drizzle:EnableRLS");
var CockroachTable = class extends __table_ts.Table {
	static [__entity_ts.entityKind] = "CockroachTable";
	/** @internal */
	static Symbol = Object.assign({}, __table_ts.Table.Symbol, {
		InlineForeignKeys,
		EnableRLS
	});
	/**@internal */
	[InlineForeignKeys] = [];
	/** @internal */
	[EnableRLS] = false;
	/** @internal */
	[__table_ts.Table.Symbol.ExtraConfigBuilder] = void 0;
	/** @internal */
	[__table_ts.Table.Symbol.ExtraConfigColumns] = {};
};
/** @internal */
function cockroachTableWithSchema(name, columns, extraConfig, schema, casing, baseName = name) {
	const casingFn = (0, __casing_ts.getCasingFn)(casing);
	const rawTable = new CockroachTable(name, schema, baseName);
	const parsedColumns = typeof columns === "function" ? columns(require_cockroach_core_columns_all.getCockroachColumnBuilders()) : columns;
	const builtColumns = Object.fromEntries(Object.entries(parsedColumns).map(([name, colBuilderBase]) => {
		const colBuilder = colBuilderBase;
		colBuilder.setName(name, casingFn);
		const column = colBuilder.build(rawTable).postBuild();
		rawTable[InlineForeignKeys].push(...colBuilder.buildForeignKeys(column, rawTable));
		return [name, column];
	}));
	const builtColumnsForExtraConfig = Object.fromEntries(Object.entries(parsedColumns).map(([name, colBuilderBase]) => {
		const colBuilder = colBuilderBase;
		colBuilder.setName(name, casingFn);
		return [name, colBuilder.buildExtraConfigColumn(rawTable)];
	}));
	const table = Object.assign(rawTable, builtColumns);
	table[__table_ts.Table.Symbol.Columns] = builtColumns;
	table[__table_ts.Table.Symbol.ExtraConfigColumns] = builtColumnsForExtraConfig;
	if (extraConfig) table[CockroachTable.Symbol.ExtraConfigBuilder] = extraConfig;
	return Object.assign(table, { enableRLS: () => {
		table[CockroachTable.Symbol.EnableRLS] = true;
		return table;
	} });
}
/** @internal */
function cockroachTableWithCasing(casing) {
	const cockroachTableInternal = (name, columns, extraConfig) => {
		return cockroachTableWithSchema(name, columns, extraConfig, void 0, casing);
	};
	const cockroachTableWithRLS = (name, columns, extraConfig) => {
		const table = cockroachTableWithSchema(name, columns, extraConfig, void 0, casing);
		table[EnableRLS] = true;
		return table;
	};
	return Object.assign(cockroachTableInternal, { withRLS: cockroachTableWithRLS });
}
const cockroachTable = cockroachTableWithCasing(void 0);
function cockroachTableCreator(customizeTableName, casing) {
	const fn = (name, columns, extraConfig) => {
		return cockroachTableWithSchema(customizeTableName(name), columns, extraConfig, void 0, casing, name);
	};
	return Object.assign(fn, { withRLS: ((name, columns, extraConfig) => {
		const table = cockroachTableWithSchema(customizeTableName(name), columns, extraConfig, void 0, casing, name);
		table[EnableRLS] = true;
		return table;
	}) });
}

//#endregion
exports.CockroachTable = CockroachTable;
exports.EnableRLS = EnableRLS;
exports.InlineForeignKeys = InlineForeignKeys;
exports.cockroachTable = cockroachTable;
exports.cockroachTableCreator = cockroachTableCreator;
exports.cockroachTableWithCasing = cockroachTableWithCasing;
exports.cockroachTableWithSchema = cockroachTableWithSchema;
//# sourceMappingURL=table.cjs.map