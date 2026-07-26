import { PgColumn } from "./common.js";
import { PgIntColumnBuilder } from "./int.common.js";
import { entityKind } from "../../entity.js";

//#region src/pg-core/columns/smallint.ts
var PgSmallIntBuilder = class extends PgIntColumnBuilder {
	static [entityKind] = "PgSmallIntBuilder";
	constructor(name) {
		super(name, "number int16", "PgSmallInt");
	}
	/** @internal */
	build(table) {
		return new PgSmallInt(table, this.config);
	}
};
var PgSmallInt = class extends PgColumn {
	static [entityKind] = "PgSmallInt";
	/** @internal */
	codec = "smallint";
	getSQLType() {
		return "smallint";
	}
};
function smallint(name) {
	return new PgSmallIntBuilder(name ?? "");
}

//#endregion
export { PgSmallInt, PgSmallIntBuilder, smallint };
//# sourceMappingURL=smallint.js.map