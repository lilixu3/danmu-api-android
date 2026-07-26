Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_table = require('./table.cjs');
let __entity_ts = require("../entity.cjs");

//#region src/singlestore-core/schema.ts
var SingleStoreSchema = class {
	static [__entity_ts.entityKind] = "SingleStoreSchema";
	constructor(schemaName, casing) {
		this.schemaName = schemaName;
		this.casing = casing;
	}
	table = (name, columns, extraConfig) => {
		return require_singlestore_core_table.singlestoreTableWithSchema(name, columns, extraConfig, this.schemaName, this.casing);
	};
};
/** @deprecated - use `instanceof SingleStoreSchema` */
function isSingleStoreSchema(obj) {
	return (0, __entity_ts.is)(obj, SingleStoreSchema);
}
/** @internal */
function singlestoreDatabase(name, casing) {
	return new SingleStoreSchema(name, casing);
}
/**
* @see singlestoreDatabase
*/
const singlestoreSchema = singlestoreDatabase;

//#endregion
exports.SingleStoreSchema = SingleStoreSchema;
exports.isSingleStoreSchema = isSingleStoreSchema;
exports.singlestoreDatabase = singlestoreDatabase;
exports.singlestoreSchema = singlestoreSchema;
//# sourceMappingURL=schema.cjs.map