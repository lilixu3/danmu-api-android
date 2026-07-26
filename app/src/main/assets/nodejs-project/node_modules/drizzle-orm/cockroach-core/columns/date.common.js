import { CockroachColumnWithArrayBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { sql } from "../../sql/sql.js";

//#region src/cockroach-core/columns/date.common.ts
var CockroachDateColumnBaseBuilder = class extends CockroachColumnWithArrayBuilder {
	static [entityKind] = "CockroachDateColumnBaseBuilder";
	defaultNow() {
		return this.default(sql`now()`);
	}
};

//#endregion
export { CockroachDateColumnBaseBuilder };
//# sourceMappingURL=date.common.js.map