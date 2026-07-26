import { CockroachColumn } from "./common.js";
import { CockroachIntColumnBaseBuilder } from "./int.common.js";
import { entityKind } from "../../entity.js";

//#region src/cockroach-core/columns/smallint.ts
var CockroachSmallIntBuilder = class extends CockroachIntColumnBaseBuilder {
	static [entityKind] = "CockroachSmallIntBuilder";
	constructor(name) {
		super(name, "number int16", "CockroachSmallInt");
	}
	/** @internal */
	build(table) {
		return new CockroachSmallInt(table, this.config);
	}
};
var CockroachSmallInt = class extends CockroachColumn {
	static [entityKind] = "CockroachSmallInt";
	getSQLType() {
		return "int2";
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return Number(value);
		return value;
	};
};
function smallint(name) {
	return new CockroachSmallIntBuilder(name ?? "");
}
function int2(name) {
	return new CockroachSmallIntBuilder(name ?? "");
}

//#endregion
export { CockroachSmallInt, CockroachSmallIntBuilder, int2, smallint };
//# sourceMappingURL=smallint.js.map