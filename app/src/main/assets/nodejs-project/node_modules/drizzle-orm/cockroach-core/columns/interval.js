import { CockroachColumn, CockroachColumnWithArrayBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/cockroach-core/columns/interval.ts
var CockroachIntervalBuilder = class extends CockroachColumnWithArrayBuilder {
	static [entityKind] = "CockroachIntervalBuilder";
	constructor(name, intervalConfig) {
		super(name, "string interval", "CockroachInterval");
		this.config.intervalConfig = intervalConfig;
	}
	/** @internal */
	build(table) {
		return new CockroachInterval(table, this.config);
	}
};
var CockroachInterval = class extends CockroachColumn {
	static [entityKind] = "CockroachInterval";
	fields = this.config.intervalConfig.fields;
	precision = this.config.intervalConfig.precision;
	getSQLType() {
		return `interval${this.fields ? ` ${this.fields}` : ""}${this.precision ? `(${this.precision})` : ""}`;
	}
};
function interval(a, b = {}) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new CockroachIntervalBuilder(name, config);
}

//#endregion
export { CockroachInterval, CockroachIntervalBuilder, interval };
//# sourceMappingURL=interval.js.map