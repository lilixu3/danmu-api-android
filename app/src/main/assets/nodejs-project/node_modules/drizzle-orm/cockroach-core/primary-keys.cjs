Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");

//#region src/cockroach-core/primary-keys.ts
function primaryKey(config) {
	return new PrimaryKeyBuilder(config.columns, config.name);
}
var PrimaryKeyBuilder = class {
	static [__entity_ts.entityKind] = "CockroachPrimaryKeyBuilder";
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
	static [__entity_ts.entityKind] = "CockroachPrimaryKey";
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
exports.PrimaryKey = PrimaryKey;
exports.PrimaryKeyBuilder = PrimaryKeyBuilder;
exports.primaryKey = primaryKey;
//# sourceMappingURL=primary-keys.cjs.map