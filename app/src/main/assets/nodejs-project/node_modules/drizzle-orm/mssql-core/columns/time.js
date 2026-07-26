import { MsSqlColumn, MsSqlColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/mssql-core/columns/time.ts
var MsSqlTimeStringBuilder = class extends MsSqlColumnBuilder {
	static [entityKind] = "MsSqlTimeBuilder";
	constructor(name, config) {
		super(name, "string time", "MsSqlTime");
		this.config.precision = config?.precision;
	}
	/** @internal */
	build(table) {
		return new MsSqlTimeString(table, this.config);
	}
};
var MsSqlTimeString = class extends MsSqlColumn {
	static [entityKind] = "MsSqlTime";
	fsp = this.config.precision;
	getSQLType() {
		return `time${this.fsp === void 0 ? "" : `(${this.fsp})`}`;
	}
	mapFromDriverValue = (value) => {
		return typeof value === "string" ? value : value?.toISOString().split("T")[1]?.split("Z")[0] ?? null;
	};
};
var MsSqlTimeBuilder = class extends MsSqlColumnBuilder {
	static [entityKind] = "MsSqlTimeBuilder";
	constructor(name, config) {
		super(name, "object date", "MsSqlTime");
		this.config.precision = config?.precision;
	}
	/** @internal */
	build(table) {
		return new MsSqlTime(table, this.config);
	}
};
var MsSqlTime = class extends MsSqlColumn {
	static [entityKind] = "MsSqlTime";
	fsp = this.config.precision;
	getSQLType() {
		return `time${this.fsp === void 0 ? "" : `(${this.fsp})`}`;
	}
};
function time(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	if (config?.mode === "string") return new MsSqlTimeStringBuilder(name, config);
	return new MsSqlTimeBuilder(name, config);
}

//#endregion
export { MsSqlTime, MsSqlTimeBuilder, MsSqlTimeString, MsSqlTimeStringBuilder, time };
//# sourceMappingURL=time.js.map