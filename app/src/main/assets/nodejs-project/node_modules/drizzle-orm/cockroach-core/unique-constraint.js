import { entityKind } from "../entity.js";

//#region src/cockroach-core/unique-constraint.ts
function unique(name) {
	return new UniqueOnConstraintBuilder(name);
}
var UniqueConstraintBuilder = class {
	static [entityKind] = "CockroachUniqueConstraintBuilder";
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
	static [entityKind] = "CockroachUniqueOnConstraintBuilder";
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
	static [entityKind] = "CockroachUniqueConstraint";
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
export { UniqueConstraint, UniqueConstraintBuilder, UniqueOnConstraintBuilder, unique };
//# sourceMappingURL=unique-constraint.js.map