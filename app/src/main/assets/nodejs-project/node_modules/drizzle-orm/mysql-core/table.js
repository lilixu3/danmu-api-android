import { getMySqlColumnBuilders } from "./columns/all.js";
import { entityKind } from "../entity.js";
import { Table } from "../table.js";
import { getCasingFn } from "../casing.js";

//#region src/mysql-core/table.ts
/** @internal */
const InlineForeignKeys = Symbol.for("drizzle:MySqlInlineForeignKeys");
var MySqlTable = class extends Table {
	static [entityKind] = "MySqlTable";
	/** @internal */
	static Symbol = Object.assign({}, Table.Symbol, { InlineForeignKeys });
	/** @internal */
	[Table.Symbol.Columns];
	/** @internal */
	[InlineForeignKeys] = [];
	/** @internal */
	[Table.Symbol.ExtraConfigBuilder] = void 0;
};
function mysqlTableWithSchema(name, columns, extraConfig, schema, casing, baseName = name) {
	const casingFn = getCasingFn(casing);
	const rawTable = new MySqlTable(name, schema, baseName);
	const parsedColumns = typeof columns === "function" ? columns(getMySqlColumnBuilders()) : columns;
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
export { InlineForeignKeys, MySqlTable, mysqlTable, mysqlTableCreator, mysqlTableWithCasing, mysqlTableWithSchema };
//# sourceMappingURL=table.js.map