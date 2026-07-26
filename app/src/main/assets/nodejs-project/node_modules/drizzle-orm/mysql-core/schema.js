import { mysqlTableWithSchema } from "./table.js";
import { mysqlViewWithSchema } from "./view.js";
import { entityKind, is } from "../entity.js";

//#region src/mysql-core/schema.ts
var MySqlSchema = class {
	static [entityKind] = "MySqlSchema";
	constructor(schemaName, casing) {
		this.schemaName = schemaName;
		this.casing = casing;
	}
	table = (name, columns, extraConfig) => {
		return mysqlTableWithSchema(name, columns, extraConfig, this.schemaName, this.casing);
	};
	view = ((name, columns) => {
		return mysqlViewWithSchema(name, columns, this.schemaName, this.casing);
	});
};
/** @deprecated - use `instanceof MySqlSchema` */
function isMySqlSchema(obj) {
	return is(obj, MySqlSchema);
}
/** @internal */
function mysqlDatabase(name, casing) {
	return new MySqlSchema(name, casing);
}
/**
* @see mysqlDatabase
*/
const mysqlSchema = mysqlDatabase;

//#endregion
export { MySqlSchema, isMySqlSchema, mysqlDatabase, mysqlSchema };
//# sourceMappingURL=schema.js.map