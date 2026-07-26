import { entityKind } from "../entity.js";
import { TableName } from "../table.utils.js";

//#region src/pg-core/unique-constraint.ts
function unique(name) {
	return new UniqueOnConstraintBuilder(name);
}
function uniqueKeyName(table, columns) {
	return `${table[TableName]}_${columns.join("_")}_unique`;
}
var UniqueConstraintBuilder = class {
	static [entityKind] = "PgUniqueConstraintBuilder";
	/** @internal */
	columns;
	/** @internal */
	nullsNotDistinctConfig = false;
	constructor(columns, name) {
		this.name = name;
		this.columns = columns;
	}
	nullsNotDistinct() {
		this.nullsNotDistinctConfig = true;
		return this;
	}
	/** @internal */
	build(table) {
		return new UniqueConstraint(table, this.columns, this.nullsNotDistinctConfig, this.name);
	}
};
var UniqueOnConstraintBuilder = class {
	static [entityKind] = "PgUniqueOnConstraintBuilder";
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
	static [entityKind] = "PgUniqueConstraint";
	columns;
	name;
	isNameExplicit;
	nullsNotDistinct = false;
	constructor(table, columns, nullsNotDistinct, name) {
		this.table = table;
		this.columns = columns;
		this.name = name ?? uniqueKeyName(this.table, this.columns.map((column) => column.name));
		this.isNameExplicit = !!name;
		this.nullsNotDistinct = nullsNotDistinct;
	}
	getName() {
		return this.name;
	}
};

//#endregion
export { UniqueConstraint, UniqueConstraintBuilder, UniqueOnConstraintBuilder, unique, uniqueKeyName };
//# sourceMappingURL=unique-constraint.js.map