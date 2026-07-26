import { CockroachColumn } from "./common.js";
import { CockroachDateColumnBaseBuilder } from "./date.common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/cockroach-core/columns/timestamp.ts
var CockroachTimestampBuilder = class extends CockroachDateColumnBaseBuilder {
	static [entityKind] = "CockroachTimestampBuilder";
	constructor(name, withTimezone, precision) {
		super(name, "object date", "CockroachTimestamp");
		this.config.withTimezone = withTimezone;
		this.config.precision = precision;
	}
	/** @internal */
	build(table) {
		return new CockroachTimestamp(table, this.config);
	}
};
var CockroachTimestamp = class extends CockroachColumn {
	static [entityKind] = "CockroachTimestamp";
	withTimezone;
	precision;
	constructor(table, config) {
		super(table, config);
		this.withTimezone = config.withTimezone;
		this.precision = config.precision;
	}
	getSQLType() {
		const precision = this.precision === void 0 ? "" : `(${this.precision})`;
		return `timestamp${this.withTimezone ? "tz" : ""}${precision}`;
	}
	mapFromDriverValue = (value) => {
		return new Date(this.withTimezone ? value : value + "+0000");
	};
	mapToDriverValue = (value) => {
		if (typeof value === "string") return value;
		return value.toISOString();
	};
};
var CockroachTimestampStringBuilder = class extends CockroachDateColumnBaseBuilder {
	static [entityKind] = "CockroachTimestampStringBuilder";
	constructor(name, withTimezone, precision) {
		super(name, "string timestamp", "CockroachTimestampString");
		this.config.withTimezone = withTimezone;
		this.config.precision = precision;
	}
	/** @internal */
	build(table) {
		return new CockroachTimestampString(table, this.config);
	}
};
var CockroachTimestampString = class extends CockroachColumn {
	static [entityKind] = "CockroachTimestampString";
	withTimezone;
	precision;
	constructor(table, config) {
		super(table, config);
		this.withTimezone = config.withTimezone;
		this.precision = config.precision;
	}
	getSQLType() {
		const precision = this.precision === void 0 ? "" : `(${this.precision})`;
		return `timestamp${this.withTimezone ? "tz" : ""}${precision}`;
	}
	mapToDriverValue = (value) => {
		if (typeof value === "string") return value;
		return value.toISOString();
	};
};
function timestamp(a, b = {}) {
	const { name, config } = getColumnNameAndConfig(a, b);
	if (config?.mode === "string") return new CockroachTimestampStringBuilder(name, config.withTimezone ?? false, config.precision);
	return new CockroachTimestampBuilder(name, config?.withTimezone ?? false, config?.precision);
}

//#endregion
export { CockroachTimestamp, CockroachTimestampBuilder, CockroachTimestampString, CockroachTimestampStringBuilder, timestamp };
//# sourceMappingURL=timestamp.js.map