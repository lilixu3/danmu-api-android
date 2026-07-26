import { MsSqlColumn } from "./common.js";
import { MsSqlDateColumnBaseBuilder } from "./date.common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/mssql-core/columns/datetime.ts
var MsSqlDateTimeBuilder = class extends MsSqlDateColumnBaseBuilder {
	static [entityKind] = "MsSqlDateTimeBuilder";
	constructor(name) {
		super(name, "object date", "MsSqlDateTime");
	}
	/** @internal */
	build(table) {
		return new MsSqlDateTime(table, this.config);
	}
};
var MsSqlDateTime = class extends MsSqlColumn {
	static [entityKind] = "MsSqlDateTime";
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return `datetime`;
	}
};
var MsSqlDateTimeStringBuilder = class extends MsSqlDateColumnBaseBuilder {
	static [entityKind] = "MsSqlDateTimeStringBuilder";
	constructor(name) {
		super(name, "string datetime", "MsSqlDateTimeString");
	}
	/** @internal */
	build(table) {
		return new MsSqlDateTimeString(table, this.config);
	}
};
var MsSqlDateTimeString = class extends MsSqlColumn {
	static [entityKind] = "MsSqlDateTimeString";
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return "datetime";
	}
	mapFromDriverValue = (value) => {
		return typeof value === "string" ? value : value?.toISOString() ?? null;
	};
};
function datetime(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	if (config?.mode === "string") return new MsSqlDateTimeStringBuilder(name);
	return new MsSqlDateTimeBuilder(name);
}

//#endregion
export { MsSqlDateTime, MsSqlDateTimeBuilder, MsSqlDateTimeString, MsSqlDateTimeStringBuilder, datetime };
//# sourceMappingURL=datetime.js.map