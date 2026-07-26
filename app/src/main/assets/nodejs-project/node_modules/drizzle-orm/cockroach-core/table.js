import { getCockroachColumnBuilders } from "./columns/all.js";
import { entityKind } from "../entity.js";
import { Table } from "../table.js";
import { getCasingFn } from "../casing.js";

//#region src/cockroach-core/table.ts
/** @internal */
const InlineForeignKeys = Symbol.for("drizzle:CockroachInlineForeignKeys");
/** @internal */
const EnableRLS = Symbol.for("drizzle:EnableRLS");
var CockroachTable = class extends Table {
	static [entityKind] = "CockroachTable";
	/** @internal */
	static Symbol = Object.assign({}, Table.Symbol, {
		InlineForeignKeys,
		EnableRLS
	});
	/**@internal */
	[InlineForeignKeys] = [];
	/** @internal */
	[EnableRLS] = false;
	/** @internal */
	[Table.Symbol.ExtraConfigBuilder] = void 0;
	/** @internal */
	[Table.Symbol.ExtraConfigColumns] = {};
};
/** @internal */
function cockroachTableWithSchema(name, columns, extraConfig, schema, casing, baseName = name) {
	const casingFn = getCasingFn(casing);
	const rawTable = new CockroachTable(name, schema, baseName);
	const parsedColumns = typeof columns === "function" ? columns(getCockroachColumnBuilders()) : columns;
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
	table[Table.Symbol.Columns] = builtColumns;
	table[Table.Symbol.ExtraConfigColumns] = builtColumnsForExtraConfig;
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
export { CockroachTable, EnableRLS, InlineForeignKeys, cockroachTable, cockroachTableCreator, cockroachTableWithCasing, cockroachTableWithSchema };
//# sourceMappingURL=table.js.map