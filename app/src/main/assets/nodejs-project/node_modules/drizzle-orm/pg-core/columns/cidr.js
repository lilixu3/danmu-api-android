import { PgColumn, PgColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/pg-core/columns/cidr.ts
var PgCidrBuilder = class extends PgColumnBuilder {
	static [entityKind] = "PgCidrBuilder";
	constructor(name) {
		super(name, "string cidr", "PgCidr");
	}
	/** @internal */
	build(table) {
		return new PgCidr(table, this.config);
	}
};
var PgCidr = class extends PgColumn {
	static [entityKind] = "PgCidr";
	/** @internal */
	codec = "cidr";
	getSQLType() {
		return "cidr";
	}
};
function cidr(name) {
	return new PgCidrBuilder(name ?? "");
}

//#endregion
export { PgCidr, PgCidrBuilder, cidr };
//# sourceMappingURL=cidr.js.map