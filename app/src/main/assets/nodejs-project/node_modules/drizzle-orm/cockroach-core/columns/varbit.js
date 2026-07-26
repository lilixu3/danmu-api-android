import { CockroachColumn, CockroachColumnWithArrayBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/cockroach-core/columns/varbit.ts
var CockroachVarbitBuilder = class extends CockroachColumnWithArrayBuilder {
	static [entityKind] = "CockroachVarbitBuilder";
	constructor(name, config) {
		super(name, "string binary", "CockroachVarbit");
		this.config.length = config.length;
	}
	/** @internal */
	build(table) {
		return new CockroachVarbit(table, this.config);
	}
};
var CockroachVarbit = class extends CockroachColumn {
	static [entityKind] = "CockroachVarbit";
	getSQLType() {
		return this.length ? `varbit(${this.length})` : "varbit";
	}
};
function varbit(a, b = {}) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new CockroachVarbitBuilder(name, config);
}

//#endregion
export { CockroachVarbit, CockroachVarbitBuilder, varbit };
//# sourceMappingURL=varbit.js.map