import { PgColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { sql } from "../../sql/sql.js";

//#region src/pg-core/columns/date.common.ts
var PgDateColumnBuilder = class extends PgColumnBuilder {
	static [entityKind] = "PgDateColumnBaseBuilder";
	/**
	* Adds a `default now()` clause to the column definition.
	* Available for date/time column types.
	*/
	defaultNow() {
		return this.default(sql`now()`);
	}
};

//#endregion
export { PgDateColumnBuilder };
//# sourceMappingURL=date.common.js.map