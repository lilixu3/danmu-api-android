import { CockroachColumn } from "./common.js";
import { CockroachDateColumnBaseBuilder } from "./date.common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/cockroach-core/columns/date.ts
var CockroachDateBuilder = class extends CockroachDateColumnBaseBuilder {
	static [entityKind] = "CockroachDateBuilder";
	constructor(name) {
		super(name, "object date", "CockroachDate");
	}
	/** @internal */
	build(table) {
		return new CockroachDate(table, this.config);
	}
};
var CockroachDate = class extends CockroachColumn {
	static [entityKind] = "CockroachDate";
	getSQLType() {
		return "date";
	}
	mapFromDriverValue = (value) => {
		return new Date(value);
	};
	mapToDriverValue = (value) => {
		if (typeof value === "string") return value;
		return value.toISOString();
	};
};
var CockroachDateStringBuilder = class extends CockroachDateColumnBaseBuilder {
	static [entityKind] = "CockroachDateStringBuilder";
	constructor(name) {
		super(name, "string date", "CockroachDateString");
	}
	/** @internal */
	build(table) {
		return new CockroachDateString(table, this.config);
	}
};
var CockroachDateString = class extends CockroachColumn {
	static [entityKind] = "CockroachDateString";
	getSQLType() {
		return "date";
	}
	mapToDriverValue = (value) => {
		if (typeof value === "string") return value;
		return value.toISOString();
	};
};
function date(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	if (config?.mode === "date") return new CockroachDateBuilder(name);
	return new CockroachDateStringBuilder(name);
}

//#endregion
export { CockroachDate, CockroachDateBuilder, CockroachDateString, CockroachDateStringBuilder, date };
//# sourceMappingURL=date.js.map