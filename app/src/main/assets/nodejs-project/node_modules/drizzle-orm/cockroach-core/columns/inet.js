import { CockroachColumn, CockroachColumnWithArrayBuilder } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/cockroach-core/columns/inet.ts
var CockroachInetBuilder = class extends CockroachColumnWithArrayBuilder {
	static [entityKind] = "CockroachInetBuilder";
	constructor(name) {
		super(name, "string inet", "CockroachInet");
	}
	/** @internal */
	build(table) {
		return new CockroachInet(table, this.config);
	}
};
var CockroachInet = class extends CockroachColumn {
	static [entityKind] = "CockroachInet";
	getSQLType() {
		return "inet";
	}
};
function inet(name) {
	return new CockroachInetBuilder(name ?? "");
}

//#endregion
export { CockroachInet, CockroachInetBuilder, inet };
//# sourceMappingURL=inet.js.map