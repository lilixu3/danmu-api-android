import { PgColumn, PgColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/pg-core/columns/jsonb.ts
var PgJsonbBuilder = class extends PgColumnBuilder {
	static [entityKind] = "PgJsonbBuilder";
	constructor(name) {
		super(name, "object json", "PgJsonb");
	}
	/** @internal */
	build(table) {
		return new PgJsonb(table, this.config);
	}
};
var PgJsonb = class extends PgColumn {
	static [entityKind] = "PgJsonb";
	/** @internal */
	codec = "jsonb";
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return "jsonb";
	}
};
function jsonb(name) {
	return new PgJsonbBuilder(name ?? "");
}

//#endregion
export { PgJsonb, PgJsonbBuilder, jsonb };
//# sourceMappingURL=jsonb.js.map