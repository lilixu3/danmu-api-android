import { entityKind } from "../entity.js";

//#region src/mysql-core/primary-keys.ts
function primaryKey(...config) {
	if (config[0].columns) return new PrimaryKeyBuilder(config[0].columns);
	return new PrimaryKeyBuilder(config);
}
var PrimaryKeyBuilder = class {
	static [entityKind] = "MySqlPrimaryKeyBuilder";
	/** @internal */
	columns;
	constructor(columns) {
		this.columns = columns;
	}
	/** @internal */
	build(table) {
		return new PrimaryKey(table, this.columns);
	}
};
var PrimaryKey = class {
	static [entityKind] = "MySqlPrimaryKey";
	columns;
	constructor(table, columns) {
		this.table = table;
		this.columns = columns;
	}
};

//#endregion
export { PrimaryKey, PrimaryKeyBuilder, primaryKey };
//# sourceMappingURL=primary-keys.js.map