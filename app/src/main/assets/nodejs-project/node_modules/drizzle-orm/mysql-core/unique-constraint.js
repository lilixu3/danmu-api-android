import { entityKind } from "../entity.js";
import { TableName } from "../table.utils.js";

//#region src/mysql-core/unique-constraint.ts
function unique(name) {
	return new UniqueOnConstraintBuilder(name);
}
function uniqueKeyName(table, columns) {
	return `${table[TableName]}_${columns.join("_")}_unique`;
}
var UniqueConstraintBuilder = class {
	static [entityKind] = "MySqlUniqueConstraintBuilder";
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
		return new UniqueConstraint(table, this.columns, this.name);
	}
};
var UniqueOnConstraintBuilder = class {
	static [entityKind] = "MySqlUniqueOnConstraintBuilder";
	/** @internal */
	name;
	constructor(name) {
		this.name = name;
	}
	on(...columns) {
		return new UniqueConstraintBuilder(columns, this.name);
	}
};
var UniqueConstraint = class {
	static [entityKind] = "MySqlUniqueConstraint";
	columns;
	name;
	isNameExplicit;
	nullsNotDistinct = false;
	constructor(table, columns, name) {
		this.table = table;
		this.columns = columns;
		this.isNameExplicit = !!name;
		this.name = name ?? uniqueKeyName(this.table, this.columns.map((column) => column.name));
	}
	getName() {
		return this.name;
	}
};

//#endregion
export { UniqueConstraint, UniqueConstraintBuilder, UniqueOnConstraintBuilder, unique, uniqueKeyName };
//# sourceMappingURL=unique-constraint.js.map