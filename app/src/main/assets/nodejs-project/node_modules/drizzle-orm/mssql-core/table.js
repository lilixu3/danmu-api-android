import { getMsSqlColumnBuilders } from "./columns/all.js";
import { entityKind } from "../entity.js";
import { Table } from "../table.js";
import { getCasingFn } from "../casing.js";

//#region src/mssql-core/table.ts
/** @internal */
const InlineForeignKeys = Symbol.for("drizzle:MsSqlInlineForeignKeys");
var MsSqlTable = class extends Table {
	static [entityKind] = "MsSqlTable";
	/** @internal */
	static Symbol = Object.assign({}, Table.Symbol, { InlineForeignKeys });
	/** @internal */
	[Table.Symbol.Columns];
	/** @internal */
	[InlineForeignKeys] = [];
	/** @internal */
	[Table.Symbol.ExtraConfigBuilder] = void 0;
};
function mssqlTableWithSchema(name, columns, extraConfig, schema, casing, baseName = name) {
	const casingFn = getCasingFn(casing);
	const rawTable = new MsSqlTable(name, schema, baseName);
	const parsedColumns = typeof columns === "function" ? columns(getMsSqlColumnBuilders()) : columns;
	const builtColumns = Object.fromEntries(Object.entries(parsedColumns).map(([name, colBuilderBase]) => {
		const colBuilder = colBuilderBase;
		colBuilder.setName(name, casingFn);
		const column = colBuilder.build(rawTable).postBuild();
		rawTable[InlineForeignKeys].push(...colBuilder.buildForeignKeys(column, rawTable));
		return [name, column];
	}));
	const table = Object.assign(rawTable, builtColumns);
	table[Table.Symbol.Columns] = builtColumns;
	table[Table.Symbol.ExtraConfigColumns] = builtColumns;
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
export { InlineForeignKeys, MsSqlTable, mssqlTable, mssqlTableCreator, mssqlTableWithCasing, mssqlTableWithSchema };
//# sourceMappingURL=table.js.map