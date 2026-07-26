import { getSQLiteColumnBuilders } from "./columns/all.js";
import { entityKind } from "../entity.js";
import { Table } from "../table.js";
import { getCasingFn } from "../casing.js";

//#region src/sqlite-core/table.ts
/** @internal */
const InlineForeignKeys = Symbol.for("drizzle:SQLiteInlineForeignKeys");
var SQLiteTable = class extends Table {
	static [entityKind] = "SQLiteTable";
	/** @internal */
	static Symbol = Object.assign({}, Table.Symbol, { InlineForeignKeys });
	/** @internal */
	[Table.Symbol.Columns];
	/** @internal */
	[InlineForeignKeys] = [];
	/** @internal */
	[Table.Symbol.ExtraConfigBuilder] = void 0;
};
/** @internal */
function sqliteTableBase(name, columns, extraConfig, schema, casing, baseName = name) {
	const casingFn = getCasingFn(casing);
	const rawTable = new SQLiteTable(name, schema, baseName);
	const parsedColumns = typeof columns === "function" ? columns(getSQLiteColumnBuilders()) : columns;
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
	if (extraConfig) table[SQLiteTable.Symbol.ExtraConfigBuilder] = extraConfig;
	return table;
}
/** @internal */
function sqliteTableWithCasing(casing) {
	return (name, columns, extraConfig) => sqliteTableBase(name, columns, extraConfig, void 0, casing);
}
const sqliteTable = sqliteTableWithCasing(void 0);
function sqliteTableCreator(customizeTableName, casing) {
	return (name, columns, extraConfig) => {
		return sqliteTableBase(customizeTableName(name), columns, extraConfig, void 0, casing, name);
	};
}

//#endregion
export { InlineForeignKeys, SQLiteTable, sqliteTable, sqliteTableBase, sqliteTableCreator, sqliteTableWithCasing };
//# sourceMappingURL=table.js.map