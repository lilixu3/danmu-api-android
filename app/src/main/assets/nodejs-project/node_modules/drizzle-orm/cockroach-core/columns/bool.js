import { CockroachColumn, CockroachColumnWithArrayBuilder } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/cockroach-core/columns/bool.ts
var CockroachBooleanBuilder = class extends CockroachColumnWithArrayBuilder {
	static [entityKind] = "CockroachBooleanBuilder";
	constructor(name) {
		super(name, "boolean", "CockroachBoolean");
	}
	/** @internal */
	build(table) {
		return new CockroachBoolean(table, this.config);
	}
};
var CockroachBoolean = class extends CockroachColumn {
	static [entityKind] = "CockroachBoolean";
	getSQLType() {
		return "bool";
	}
};
function bool(name) {
	return new CockroachBooleanBuilder(name ?? "");
}
const boolean = bool;

//#endregion
export { CockroachBoolean, CockroachBooleanBuilder, bool, boolean };
//# sourceMappingURL=bool.js.map