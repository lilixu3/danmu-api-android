import { CockroachColumn, CockroachColumnWithArrayBuilder } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/cockroach-core/columns/float.ts
var CockroachFloatBuilder = class extends CockroachColumnWithArrayBuilder {
	static [entityKind] = "CockroachFloatBuilder";
	constructor(name) {
		super(name, "number double", "CockroachFloat");
	}
	/** @internal */
	build(table) {
		return new CockroachFloat(table, this.config);
	}
};
var CockroachFloat = class extends CockroachColumn {
	static [entityKind] = "CockroachFloat";
	getSQLType() {
		return "float";
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return Number.parseFloat(value);
		return value;
	};
};
function float(name) {
	return new CockroachFloatBuilder(name ?? "");
}
const doublePrecision = float;

//#endregion
export { CockroachFloat, CockroachFloatBuilder, doublePrecision, float };
//# sourceMappingURL=float.js.map