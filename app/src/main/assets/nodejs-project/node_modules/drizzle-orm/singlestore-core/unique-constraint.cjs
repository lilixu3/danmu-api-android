Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");
let __table_utils_ts = require("../table.utils.cjs");

//#region src/singlestore-core/unique-constraint.ts
function unique(name) {
	return new UniqueOnConstraintBuilder(name);
}
function uniqueKeyName(table, columns) {
	return `${table[__table_utils_ts.TableName]}_${columns.join("_")}_unique`;
}
var UniqueConstraintBuilder = class {
	static [__entity_ts.entityKind] = "SingleStoreUniqueConstraintBuilder";
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
	static [__entity_ts.entityKind] = "SingleStoreUniqueOnConstraintBuilder";
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
	static [__entity_ts.entityKind] = "SingleStoreUniqueConstraint";
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
exports.UniqueConstraint = UniqueConstraint;
exports.UniqueConstraintBuilder = UniqueConstraintBuilder;
exports.UniqueOnConstraintBuilder = UniqueOnConstraintBuilder;
exports.unique = unique;
exports.uniqueKeyName = uniqueKeyName;
//# sourceMappingURL=unique-constraint.cjs.map