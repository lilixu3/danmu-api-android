import { MsSqlColumn } from "./common.js";
import { MsSqlDateColumnBaseBuilder } from "./date.common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/mssql-core/columns/datetimeoffset.ts
var MsSqlDateTimeOffsetBuilder = class extends MsSqlDateColumnBaseBuilder {
	static [entityKind] = "MsSqlDateTimeOffsetBuilder";
	constructor(name, config) {
		super(name, "object date", "MsSqlDateTimeOffset");
		this.config.precision = config?.precision;
	}
	/** @internal */
	build(table) {
		return new MsSqlDateTimeOffset(table, this.config);
	}
};
var MsSqlDateTimeOffset = class extends MsSqlColumn {
	static [entityKind] = "MsSqlDateTimeOffset";
	precision;
	constructor(table, config) {
		super(table, config);
		this.precision = config.precision;
	}
	getSQLType() {
		return `datetimeoffset${this.precision === void 0 ? "" : `(${this.precision})`}`;
	}
};
var MsSqlDateTimeOffsetStringBuilder = class extends MsSqlDateColumnBaseBuilder {
	static [entityKind] = "MsSqlDateTimeOffsetStringBuilder";
	constructor(name, config) {
		super(name, "string datetime", "MsSqlDateTimeOffsetString");
		this.config.precision = config?.precision;
	}
	/** @internal */
	build(table) {
		return new MsSqlDateTimeOffsetString(table, this.config);
	}
};
var MsSqlDateTimeOffsetString = class extends MsSqlColumn {
	static [entityKind] = "MsSqlDateTimeOffsetString";
	precision;
	constructor(table, config) {
		super(table, config);
		this.precision = config.precision;
	}
	getSQLType() {
		return `datetimeoffset${this.precision === void 0 ? "" : `(${this.precision})`}`;
	}
	mapFromDriverValue = (value) => {
		return typeof value === "string" ? value : value?.toISOString() ?? null;
	};
};
function datetimeoffset(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	if (config?.mode === "string") return new MsSqlDateTimeOffsetStringBuilder(name, config);
	return new MsSqlDateTimeOffsetBuilder(name, config);
}

//#endregion
export { MsSqlDateTimeOffset, MsSqlDateTimeOffsetBuilder, MsSqlDateTimeOffsetString, MsSqlDateTimeOffsetStringBuilder, datetimeoffset };
//# sourceMappingURL=datetimeoffset.js.map