Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_table = require('./table.cjs');
let __entity_ts = require("../entity.cjs");

//#region src/singlestore-core/primary-keys.ts
function primaryKey(...config) {
	if (config[0].columns) return new PrimaryKeyBuilder(config[0].columns, config[0].name);
	return new PrimaryKeyBuilder(config);
}
var PrimaryKeyBuilder = class {
	static [__entity_ts.entityKind] = "SingleStorePrimaryKeyBuilder";
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
	static [__entity_ts.entityKind] = "SingleStorePrimaryKey";
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
		return this.name ?? `${this.table[require_singlestore_core_table.SingleStoreTable.Symbol.Name]}_${this.columns.map((column) => column.name).join("_")}_pk`;
	}
};

//#endregion
exports.PrimaryKey = PrimaryKey;
exports.PrimaryKeyBuilder = PrimaryKeyBuilder;
exports.primaryKey = primaryKey;
//# sourceMappingURL=primary-keys.cjs.map