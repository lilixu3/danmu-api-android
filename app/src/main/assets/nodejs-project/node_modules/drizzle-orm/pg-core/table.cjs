Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_all = require('./columns/all.cjs');
let __entity_ts = require("../entity.cjs");
let __table_ts = require("../table.cjs");
let __casing_ts = require("../casing.cjs");

//#region src/pg-core/table.ts
/** @internal */
const InlineForeignKeys = Symbol.for("drizzle:PgInlineForeignKeys");
/** @internal */
const EnableRLS = Symbol.for("drizzle:EnableRLS");
var PgTable = class extends __table_ts.Table {
	static [__entity_ts.entityKind] = "PgTable";
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
function pgTableWithSchema(name, columns, extraConfig, schema, casing, baseName = name) {
	const casingFn = (0, __casing_ts.getCasingFn)(casing);
	const rawTable = new PgTable(name, schema, baseName);
	const parsedColumns = typeof columns === "function" ? columns(require_pg_core_columns_all.getPgColumnBuilders()) : columns;
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
	if (extraConfig) table[PgTable.Symbol.ExtraConfigBuilder] = extraConfig;
	return Object.assign(table, { enableRLS: () => {
		table[PgTable.Symbol.EnableRLS] = true;
		return table;
	} });
}
/** @internal */
function pgTableWithCasing(casing) {
	const pgTableInternal = (name, columns, extraConfig) => {
		return pgTableWithSchema(name, columns, extraConfig, void 0, casing);
	};
	const pgTableWithRLS = (name, columns, extraConfig) => {
		const table = pgTableWithSchema(name, columns, extraConfig, void 0, casing);
		table[EnableRLS] = true;
		return table;
	};
	return Object.assign(pgTableInternal, { withRLS: pgTableWithRLS });
}
const pgTable = pgTableWithCasing(void 0);
function pgTableCreator(customizeTableName, casing) {
	const fn = (name, columns, extraConfig) => {
		return pgTableWithSchema(customizeTableName(name), columns, extraConfig, void 0, casing, name);
	};
	return Object.assign(fn, { withRLS: ((name, columns, extraConfig) => {
		const table = pgTableWithSchema(customizeTableName(name), columns, extraConfig, void 0, casing, name);
		table[EnableRLS] = true;
		return table;
	}) });
}

//#endregion
exports.EnableRLS = EnableRLS;
exports.InlineForeignKeys = InlineForeignKeys;
exports.PgTable = PgTable;
exports.pgTable = pgTable;
exports.pgTableCreator = pgTableCreator;
exports.pgTableWithCasing = pgTableWithCasing;
exports.pgTableWithSchema = pgTableWithSchema;
//# sourceMappingURL=table.cjs.map