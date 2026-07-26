import { getSingleStoreColumnBuilders } from "./columns/all.js";
import { entityKind } from "../entity.js";
import { Table } from "../table.js";
import { getCasingFn } from "../casing.js";

//#region src/singlestore-core/table.ts
var SingleStoreTable = class extends Table {
	static [entityKind] = "SingleStoreTable";
	/** @internal */
	static Symbol = Object.assign({}, Table.Symbol, {});
	/** @internal */
	[Table.Symbol.Columns];
	/** @internal */
	[Table.Symbol.ExtraConfigBuilder] = void 0;
};
function singlestoreTableWithSchema(name, columns, extraConfig, schema, casing, baseName = name) {
	const casingFn = getCasingFn(casing);
	const rawTable = new SingleStoreTable(name, schema, baseName);
	const parsedColumns = typeof columns === "function" ? columns(getSingleStoreColumnBuilders()) : columns;
	const builtColumns = Object.fromEntries(Object.entries(parsedColumns).map(([name, colBuilderBase]) => {
		const colBuilder = colBuilderBase;
		colBuilder.setName(name, casingFn);
		return [name, colBuilder.build(rawTable).postBuild()];
	}));
	const table = Object.assign(rawTable, builtColumns);
	table[Table.Symbol.Columns] = builtColumns;
	table[Table.Symbol.ExtraConfigColumns] = builtColumns;
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
export { SingleStoreTable, singlestoreTable, singlestoreTableCreator, singlestoreTableWithCasing, singlestoreTableWithSchema };
//# sourceMappingURL=table.js.map