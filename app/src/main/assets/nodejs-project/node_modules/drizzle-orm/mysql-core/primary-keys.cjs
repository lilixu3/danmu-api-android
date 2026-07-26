Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");

//#region src/mysql-core/primary-keys.ts
function primaryKey(...config) {
	if (config[0].columns) return new PrimaryKeyBuilder(config[0].columns);
	return new PrimaryKeyBuilder(config);
}
var PrimaryKeyBuilder = class {
	static [__entity_ts.entityKind] = "MySqlPrimaryKeyBuilder";
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
	static [__entity_ts.entityKind] = "MySqlPrimaryKey";
	columns;
	constructor(table, columns) {
		this.table = table;
		this.columns = columns;
	}
};

//#endregion
exports.PrimaryKey = PrimaryKey;
exports.PrimaryKeyBuilder = PrimaryKeyBuilder;
exports.primaryKey = primaryKey;
//# sourceMappingURL=primary-keys.cjs.map