import { singlestoreTableWithSchema } from "./table.js";
import { entityKind, is } from "../entity.js";

//#region src/singlestore-core/schema.ts
var SingleStoreSchema = class {
	static [entityKind] = "SingleStoreSchema";
	constructor(schemaName, casing) {
		this.schemaName = schemaName;
		this.casing = casing;
	}
	table = (name, columns, extraConfig) => {
		return singlestoreTableWithSchema(name, columns, extraConfig, this.schemaName, this.casing);
	};
};
/** @deprecated - use `instanceof SingleStoreSchema` */
function isSingleStoreSchema(obj) {
	return is(obj, SingleStoreSchema);
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
export { SingleStoreSchema, isSingleStoreSchema, singlestoreDatabase, singlestoreSchema };
//# sourceMappingURL=schema.js.map