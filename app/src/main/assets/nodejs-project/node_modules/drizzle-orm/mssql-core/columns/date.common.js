import { MsSqlColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { sql } from "../../sql/sql.js";

//#region src/mssql-core/columns/date.common.ts
var MsSqlDateColumnBaseBuilder = class extends MsSqlColumnBuilder {
	static [entityKind] = "MsSqlDateColumnBuilder";
	defaultGetDate() {
		return this.default(sql`(getdate())`);
	}
};

//#endregion
export { MsSqlDateColumnBaseBuilder };
//# sourceMappingURL=date.common.js.map