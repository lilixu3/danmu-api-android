Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");
let __table_utils_ts = require("../table.utils.cjs");

//#region src/pg-core/unique-constraint.ts
function unique(name) {
	return new UniqueOnConstraintBuilder(name);
}
function uniqueKeyName(table, columns) {
	return `${table[__table_utils_ts.TableName]}_${columns.join("_")}_unique`;
}
var UniqueConstraintBuilder = class {
	static [__entity_ts.entityKind] = "PgUniqueConstraintBuilder";
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
	static [__entity_ts.entityKind] = "PgUniqueOnConstraintBuilder";
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
	static [__entity_ts.entityKind] = "PgUniqueConstraint";
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
exports.UniqueConstraint = UniqueConstraint;
exports.UniqueConstraintBuilder = UniqueConstraintBuilder;
exports.UniqueOnConstraintBuilder = UniqueOnConstraintBuilder;
exports.unique = unique;
exports.uniqueKeyName = uniqueKeyName;
//# sourceMappingURL=unique-constraint.cjs.map