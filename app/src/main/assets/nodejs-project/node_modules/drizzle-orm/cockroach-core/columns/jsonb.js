import { CockroachColumn, CockroachColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/cockroach-core/columns/jsonb.ts
var CockroachJsonbBuilder = class extends CockroachColumnBuilder {
	static [entityKind] = "CockroachJsonbBuilder";
	constructor(name) {
		super(name, "object json", "CockroachJsonb");
	}
	/** @internal */
	build(table) {
		return new CockroachJsonb(table, this.config);
	}
};
var CockroachJsonb = class extends CockroachColumn {
	static [entityKind] = "CockroachJsonb";
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return "jsonb";
	}
	mapToDriverValue = (value) => {
		return JSON.stringify(value);
	};
	mapFromDriverValue = (value) => {
		if (typeof value === "string") try {
			return JSON.parse(value);
		} catch {
			return value;
		}
		return value;
	};
};
function jsonb(name) {
	return new CockroachJsonbBuilder(name ?? "");
}

//#endregion
export { CockroachJsonb, CockroachJsonbBuilder, jsonb };
//# sourceMappingURL=jsonb.js.map