import { MsSqlColumn } from "./common.js";
import { MsSqlDateColumnBaseBuilder } from "./date.common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/mssql-core/columns/date.ts
var MsSqlDateBuilder = class extends MsSqlDateColumnBaseBuilder {
	static [entityKind] = "MsSqlDateBuilder";
	constructor(name) {
		super(name, "object date", "MsSqlDate");
	}
	/** @internal */
	build(table) {
		return new MsSqlDate(table, this.config);
	}
};
var MsSqlDate = class extends MsSqlColumn {
	static [entityKind] = "MsSqlDate";
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return `date`;
	}
	mapFromDriverValue = (value) => {
		return new Date(value);
	};
};
var MsSqlDateStringBuilder = class extends MsSqlDateColumnBaseBuilder {
	static [entityKind] = "MsSqlDateStringBuilder";
	constructor(name) {
		super(name, "string date", "MsSqlDateString");
	}
	/** @internal */
	build(table) {
		return new MsSqlDateString(table, this.config);
	}
};
var MsSqlDateString = class extends MsSqlColumn {
	static [entityKind] = "MsSqlDateString";
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return `date`;
	}
	mapFromDriverValue = (value) => {
		return typeof value === "string" ? value : value?.toISOString().split("T")[0] ?? null;
	};
};
function date(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	if (config?.mode === "string") return new MsSqlDateStringBuilder(name);
	return new MsSqlDateBuilder(name);
}

//#endregion
export { MsSqlDate, MsSqlDateBuilder, MsSqlDateString, MsSqlDateStringBuilder, date };
//# sourceMappingURL=date.js.map