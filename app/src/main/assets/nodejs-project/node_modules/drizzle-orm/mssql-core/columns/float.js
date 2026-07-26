import { MsSqlColumnBuilderWithIdentity, MsSqlColumnWithIdentity } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/mssql-core/columns/float.ts
var MsSqlFloatBuilder = class extends MsSqlColumnBuilderWithIdentity {
	static [entityKind] = "MsSqlFloatBuilder";
	constructor(name, config) {
		super(name, "number double", "MsSqlFloat");
		this.config.precision = config?.precision;
	}
	/** @internal */
	build(table) {
		return new MsSqlFloat(table, this.config);
	}
};
var MsSqlFloat = class extends MsSqlColumnWithIdentity {
	static [entityKind] = "MsSqlFloat";
	precision = this.config.precision;
	getSQLType() {
		return `float${this.precision === void 0 ? "" : `(${this.precision})`}`;
	}
};
function float(a, b = {}) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new MsSqlFloatBuilder(name, config);
}

//#endregion
export { MsSqlFloat, MsSqlFloatBuilder, float };
//# sourceMappingURL=float.js.map