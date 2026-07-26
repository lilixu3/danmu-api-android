import { entityKind } from "./entity.js";
import { TableName } from "./table.utils.js";

//#region src/table.ts
/** @internal */
const TableSchema = Symbol.for("drizzle:Schema");
/** @internal */
const TableColumns = Symbol.for("drizzle:Columns");
/** @internal */
const ExtraConfigColumns = Symbol.for("drizzle:ExtraConfigColumns");
/** @internal */
const OriginalName = Symbol.for("drizzle:OriginalName");
/** @internal */
const BaseName = Symbol.for("drizzle:BaseName");
/** @internal */
const IsAlias = Symbol.for("drizzle:IsAlias");
/** @internal */
const ExtraConfigBuilder = Symbol.for("drizzle:ExtraConfigBuilder");
const IsDrizzleTable = Symbol.for("drizzle:IsDrizzleTable");
var Table = class {
	static [entityKind] = "Table";
	/** @internal */
	static Symbol = {
		Name: TableName,
		Schema: TableSchema,
		OriginalName,
		Columns: TableColumns,
		ExtraConfigColumns,
		BaseName,
		IsAlias,
		ExtraConfigBuilder
	};
	/**
	* @internal
	* Can be changed if the table is aliased.
	*/
	[TableName];
	/**
	* @internal
	* Used to store the original name of the table, before any aliasing.
	*/
	[OriginalName];
	/** @internal */
	[TableSchema];
	/** @internal */
	[TableColumns];
	/** @internal */
	[ExtraConfigColumns];
	/**
	*  @internal
	* Used to store the table name before the transformation via the `tableCreator` functions.
	*/
	[BaseName];
	/** @internal */
	[IsAlias] = false;
	/** @internal */
	[IsDrizzleTable] = true;
	/** @internal */
	[ExtraConfigBuilder] = void 0;
	constructor(name, schema, baseName) {
		this[TableName] = this[OriginalName] = name;
		this[TableSchema] = schema;
		this[BaseName] = baseName;
	}
};
function isTable(table) {
	return typeof table === "object" && table !== null && IsDrizzleTable in table;
}
function getTableName(table) {
	return table[TableName];
}
function getTableUniqueName(table) {
	return `${table[TableSchema] ?? "public"}.${table[TableName]}`;
}

//#endregion
export { BaseName, ExtraConfigBuilder, ExtraConfigColumns, IsAlias, OriginalName, Table, TableColumns, TableSchema, getTableName, getTableUniqueName, isTable };
//# sourceMappingURL=table.js.map