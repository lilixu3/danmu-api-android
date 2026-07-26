import { entityKind } from "../entity.js";

//#region src/mssql-core/primary-keys.ts
function primaryKey(config) {
	return new PrimaryKeyBuilder(config.columns, config.name);
}
var PrimaryKeyBuilder = class {
	static [entityKind] = "MsSqlPrimaryKeyBuilder";
	/** @internal */
	columns;
	/** @internal */
	name;
	constructor(columns, name) {
		this.columns = columns;
		this.name = name;
	}
	/** @internal */
	build(table) {
		return new PrimaryKey(table, this.columns, this.name);
	}
};
var PrimaryKey = class {
	static [entityKind] = "MsSqlPrimaryKey";
	columns;
	name;
	isNameExplicit;
	constructor(table, columns, name) {
		this.table = table;
		this.columns = columns;
		this.name = name;
		this.isNameExplicit = !!name;
	}
	getName() {
		return this.name;
	}
};

//#endregion
export { PrimaryKey, PrimaryKeyBuilder, primaryKey };
//# sourceMappingURL=primary-keys.js.map