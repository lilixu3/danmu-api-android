import { PgColumn, PgColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/pg-core/columns/macaddr.ts
var PgMacaddrBuilder = class extends PgColumnBuilder {
	static [entityKind] = "PgMacaddrBuilder";
	constructor(name) {
		super(name, "string macaddr", "PgMacaddr");
	}
	/** @internal */
	build(table) {
		return new PgMacaddr(table, this.config);
	}
};
var PgMacaddr = class extends PgColumn {
	static [entityKind] = "PgMacaddr";
	/** @internal */
	codec = "macaddr";
	getSQLType() {
		return "macaddr";
	}
};
function macaddr(name) {
	return new PgMacaddrBuilder(name ?? "");
}

//#endregion
export { PgMacaddr, PgMacaddrBuilder, macaddr };
//# sourceMappingURL=macaddr.js.map