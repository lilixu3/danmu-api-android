import { PgColumn, PgColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/pg-core/columns/inet.ts
var PgInetBuilder = class extends PgColumnBuilder {
	static [entityKind] = "PgInetBuilder";
	constructor(name) {
		super(name, "string inet", "PgInet");
	}
	/** @internal */
	build(table) {
		return new PgInet(table, this.config);
	}
};
var PgInet = class extends PgColumn {
	static [entityKind] = "PgInet";
	/** @internal */
	codec = "inet";
	getSQLType() {
		return "inet";
	}
};
function inet(name) {
	return new PgInetBuilder(name ?? "");
}

//#endregion
export { PgInet, PgInetBuilder, inet };
//# sourceMappingURL=inet.js.map