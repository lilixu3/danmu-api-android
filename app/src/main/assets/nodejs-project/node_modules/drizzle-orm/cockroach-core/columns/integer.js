import { CockroachColumn } from "./common.js";
import { CockroachIntColumnBaseBuilder } from "./int.common.js";
import { entityKind } from "../../entity.js";

//#region src/cockroach-core/columns/integer.ts
var CockroachIntegerBuilder = class extends CockroachIntColumnBaseBuilder {
	static [entityKind] = "CockroachIntegerBuilder";
	constructor(name) {
		super(name, "number int32", "CockroachInteger");
	}
	/** @internal */
	build(table) {
		return new CockroachInteger(table, this.config);
	}
};
var CockroachInteger = class extends CockroachColumn {
	static [entityKind] = "CockroachInteger";
	getSQLType() {
		return "int4";
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return Number.parseInt(value);
		return value;
	};
};
function int4(name) {
	return new CockroachIntegerBuilder(name ?? "");
}

//#endregion
export { CockroachInteger, CockroachIntegerBuilder, int4 };
//# sourceMappingURL=integer.js.map