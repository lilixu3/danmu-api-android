import { MsSqlColumn } from "./common.js";
import { MsSqlDateColumnBaseBuilder } from "./date.common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/mssql-core/columns/datetime2.ts
var MsSqlDateTime2Builder = class extends MsSqlDateColumnBaseBuilder {
	static [entityKind] = "MsSqlDateTime2Builder";
	constructor(name, config) {
		super(name, "object date", "MsSqlDateTime2");
		this.config.precision = config?.precision;
	}
	/** @internal */
	build(table) {
		return new MsSqlDateTime2(table, this.config);
	}
};
var MsSqlDateTime2 = class extends MsSqlColumn {
	static [entityKind] = "MsSqlDateTime2";
	precision;
	constructor(table, config) {
		super(table, config);
		this.precision = config.precision;
	}
	getSQLType() {
		return `datetime2${this.precision === void 0 ? "" : `(${this.precision})`}`;
	}
};
var MsSqlDateTime2StringBuilder = class extends MsSqlDateColumnBaseBuilder {
	static [entityKind] = "MsSqlDateTime2StringBuilder";
	constructor(name, config) {
		super(name, "string datetime", "MsSqlDateTime2String");
		this.config.precision = config?.precision;
	}
	/** @internal */
	build(table) {
		return new MsSqlDateTime2String(table, this.config);
	}
};
var MsSqlDateTime2String = class extends MsSqlColumn {
	static [entityKind] = "MsSqlDateTime2String";
	precision;
	constructor(table, config) {
		super(table, config);
		this.precision = config.precision;
	}
	getSQLType() {
		return `datetime2${this.precision === void 0 ? "" : `(${this.precision})`}`;
	}
	mapFromDriverValue = (value) => {
		return typeof value === "string" ? value : value?.toISOString() ?? null;
	};
};
function datetime2(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	if (config?.mode === "string") return new MsSqlDateTime2StringBuilder(name, config);
	return new MsSqlDateTime2Builder(name, config);
}

//#endregion
export { MsSqlDateTime2, MsSqlDateTime2Builder, MsSqlDateTime2String, MsSqlDateTime2StringBuilder, datetime2 };
//# sourceMappingURL=datetime2.js.map