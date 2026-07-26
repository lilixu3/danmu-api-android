import { mssqlTableWithSchema } from "./table.js";
import { mssqlViewWithSchema } from "./view.js";
import { entityKind } from "../entity.js";

//#region src/mssql-core/schema.ts
var MsSqlSchema = class {
	static [entityKind] = "MsSqlSchema";
	isExisting = false;
	constructor(schemaName, casing) {
		this.schemaName = schemaName;
		this.casing = casing;
	}
	table = (name, columns, extraConfig) => {
		return mssqlTableWithSchema(name, columns, extraConfig, this.schemaName, this.casing);
	};
	view = ((name, columns) => {
		return mssqlViewWithSchema(name, columns, this.schemaName, this.casing);
	});
	existing() {
		this.isExisting = true;
		return this;
	}
};
/** @internal */
function mssqlSchema(name, casing) {
	return new MsSqlSchema(name, casing);
}

//#endregion
export { MsSqlSchema, mssqlSchema };
//# sourceMappingURL=schema.js.map