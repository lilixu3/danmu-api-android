import { PgColumn, PgColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/pg-core/columns/double-precision.ts
var PgDoublePrecisionBuilder = class extends PgColumnBuilder {
	static [entityKind] = "PgDoublePrecisionBuilder";
	constructor(name) {
		super(name, "number double", "PgDoublePrecision");
	}
	/** @internal */
	build(table) {
		return new PgDoublePrecision(table, this.config);
	}
};
var PgDoublePrecision = class extends PgColumn {
	static [entityKind] = "PgDoublePrecision";
	/** @internal */
	codec = "float8";
	getSQLType() {
		return "double precision";
	}
};
function doublePrecision(name) {
	return new PgDoublePrecisionBuilder(name ?? "");
}

//#endregion
export { PgDoublePrecision, PgDoublePrecisionBuilder, doublePrecision };
//# sourceMappingURL=double-precision.js.map