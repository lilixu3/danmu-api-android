import { PgColumn, PgColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/pg-core/columns/boolean.ts
var PgBooleanBuilder = class extends PgColumnBuilder {
	static [entityKind] = "PgBooleanBuilder";
	constructor(name) {
		super(name, "boolean", "PgBoolean");
	}
	/** @internal */
	build(table) {
		return new PgBoolean(table, this.config);
	}
};
var PgBoolean = class extends PgColumn {
	static [entityKind] = "PgBoolean";
	/** @internal */
	codec = "bool";
	getSQLType() {
		return "boolean";
	}
};
function boolean(name) {
	return new PgBooleanBuilder(name ?? "");
}

//#endregion
export { PgBoolean, PgBooleanBuilder, boolean };
//# sourceMappingURL=boolean.js.map