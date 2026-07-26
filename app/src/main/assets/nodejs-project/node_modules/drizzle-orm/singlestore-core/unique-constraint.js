import { entityKind } from "../entity.js";
import { TableName } from "../table.utils.js";

//#region src/singlestore-core/unique-constraint.ts
function unique(name) {
	return new UniqueOnConstraintBuilder(name);
}
function uniqueKeyName(table, columns) {
	return `${table[TableName]}_${columns.join("_")}_unique`;
}
var UniqueConstraintBuilder = class {
	static [entityKind] = "SingleStoreUniqueConstraintBuilder";
	/** @internal */
	columns;
	constructor(columns, name) {
		this.name = name;
		this.columns = columns;
	}
	/** @internal */
	build(table) {
		return new UniqueConstraint(table, this.columns, this.name);
	}
};
var UniqueOnConstraintBuilder = class {
	static [entityKind] = "SingleStoreUniqueOnConstraintBuilder";
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
	static [entityKind] = "SingleStoreUniqueConstraint";
	columns;
	name;
	nullsNotDistinct = false;
	isNameExplicit;
	constructor(table, columns, name) {
		this.table = table;
		this.columns = columns;
		this.name = name ?? uniqueKeyName(this.table, this.columns.map((column) => column.name));
		this.isNameExplicit = !!name;
	}
	getName() {
		return this.name;
	}
};

//#endregion
export { UniqueConstraint, UniqueConstraintBuilder, UniqueOnConstraintBuilder, unique, uniqueKeyName };
//# sourceMappingURL=unique-constraint.js.map