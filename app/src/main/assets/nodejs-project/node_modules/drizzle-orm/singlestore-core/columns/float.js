import { SingleStoreColumnBuilderWithAutoIncrement, SingleStoreColumnWithAutoIncrement } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/singlestore-core/columns/float.ts
var SingleStoreFloatBuilder = class extends SingleStoreColumnBuilderWithAutoIncrement {
	static [entityKind] = "SingleStoreFloatBuilder";
	constructor(name, config) {
		super(name, config?.unsigned ? "number ufloat" : "number float", "SingleStoreFloat");
		this.config.precision = config?.precision;
		this.config.scale = config?.scale;
		this.config.unsigned = config?.unsigned;
	}
	/** @internal */
	build(table) {
		return new SingleStoreFloat(table, this.config);
	}
};
var SingleStoreFloat = class extends SingleStoreColumnWithAutoIncrement {
	static [entityKind] = "SingleStoreFloat";
	precision = this.config.precision;
	scale = this.config.scale;
	unsigned = this.config.unsigned;
	getSQLType() {
		let type = "";
		if (this.precision !== void 0 && this.scale !== void 0) type += `float(${this.precision},${this.scale})`;
		else if (this.precision === void 0) type += "float";
		else type += `float(${this.precision},0)`;
		return this.unsigned ? `${type} unsigned` : type;
	}
	mapFromDriverValue = (value) => {
		if (typeof value !== "number") return Number(value);
		return value;
	};
};
function float(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new SingleStoreFloatBuilder(name, config);
}

//#endregion
export { SingleStoreFloat, SingleStoreFloatBuilder, float };
//# sourceMappingURL=float.js.map