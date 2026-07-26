import { CockroachColumn, CockroachColumnWithArrayBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/cockroach-core/columns/time.ts
var CockroachTimeBuilder = class extends CockroachColumnWithArrayBuilder {
	static [entityKind] = "CockroachTimeBuilder";
	constructor(name, withTimezone, precision) {
		super(name, "string time", "CockroachTime");
		this.withTimezone = withTimezone;
		this.precision = precision;
		this.config.withTimezone = withTimezone;
		this.config.precision = precision;
	}
	/** @internal */
	build(table) {
		return new CockroachTime(table, this.config);
	}
};
var CockroachTime = class extends CockroachColumn {
	static [entityKind] = "CockroachTime";
	withTimezone;
	precision;
	constructor(table, config) {
		super(table, config);
		this.withTimezone = config.withTimezone;
		this.precision = config.precision;
	}
	getSQLType() {
		const precision = this.precision === void 0 ? "" : `(${this.precision})`;
		return `time${this.withTimezone ? "tz" : ""}${precision}`;
	}
};
function time(a, b = {}) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new CockroachTimeBuilder(name, config.withTimezone ?? false, config.precision);
}

//#endregion
export { CockroachTime, CockroachTimeBuilder, time };
//# sourceMappingURL=time.js.map