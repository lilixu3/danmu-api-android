import { CockroachColumn, CockroachColumnWithArrayBuilder } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/cockroach-core/columns/real.ts
var CockroachRealBuilder = class extends CockroachColumnWithArrayBuilder {
	static [entityKind] = "CockroachRealBuilder";
	constructor(name, length) {
		super(name, "number float", "CockroachReal");
		this.config.length = length;
	}
	/** @internal */
	build(table) {
		return new CockroachReal(table, this.config);
	}
};
var CockroachReal = class extends CockroachColumn {
	static [entityKind] = "CockroachReal";
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return "real";
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return Number.parseFloat(value);
		return value;
	};
};
function real(name) {
	return new CockroachRealBuilder(name ?? "");
}

//#endregion
export { CockroachReal, CockroachRealBuilder, real };
//# sourceMappingURL=real.js.map