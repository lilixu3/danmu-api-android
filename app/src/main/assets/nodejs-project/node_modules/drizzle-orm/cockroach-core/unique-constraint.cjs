Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");

//#region src/cockroach-core/unique-constraint.ts
function unique(name) {
	return new UniqueOnConstraintBuilder(name);
}
var UniqueConstraintBuilder = class {
	static [__entity_ts.entityKind] = "CockroachUniqueConstraintBuilder";
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
	static [__entity_ts.entityKind] = "CockroachUniqueOnConstraintBuilder";
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
	static [__entity_ts.entityKind] = "CockroachUniqueConstraint";
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
exports.UniqueConstraint = UniqueConstraint;
exports.UniqueConstraintBuilder = UniqueConstraintBuilder;
exports.UniqueOnConstraintBuilder = UniqueOnConstraintBuilder;
exports.unique = unique;
//# sourceMappingURL=unique-constraint.cjs.map