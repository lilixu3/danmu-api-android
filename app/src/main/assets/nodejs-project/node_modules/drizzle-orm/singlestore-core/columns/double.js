import { SingleStoreColumnBuilderWithAutoIncrement, SingleStoreColumnWithAutoIncrement } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/singlestore-core/columns/double.ts
var SingleStoreDoubleBuilder = class extends SingleStoreColumnBuilderWithAutoIncrement {
	static [entityKind] = "SingleStoreDoubleBuilder";
	constructor(name, config) {
		super(name, config?.unsigned ? "number udouble" : "number double", "SingleStoreDouble");
		this.config.precision = config?.precision;
		this.config.scale = config?.scale;
		this.config.unsigned = config?.unsigned;
	}
	/** @internal */
	build(table) {
		return new SingleStoreDouble(table, this.config);
	}
};
var SingleStoreDouble = class extends SingleStoreColumnWithAutoIncrement {
	static [entityKind] = "SingleStoreDouble";
	precision = this.config.precision;
	scale = this.config.scale;
	unsigned = this.config.unsigned;
	getSQLType() {
		let type = "";
		if (this.precision !== void 0 && this.scale !== void 0) type += `double(${this.precision},${this.scale})`;
		else if (this.precision === void 0) type += "double";
		else type += `double(${this.precision})`;
		return this.unsigned ? `${type} unsigned` : type;
	}
};
function double(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new SingleStoreDoubleBuilder(name, config);
}

//#endregion
export { SingleStoreDouble, SingleStoreDoubleBuilder, double };
//# sourceMappingURL=double.js.map