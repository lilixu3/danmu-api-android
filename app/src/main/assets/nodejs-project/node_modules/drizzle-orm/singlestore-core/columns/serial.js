import { SingleStoreColumnBuilderWithAutoIncrement, SingleStoreColumnWithAutoIncrement } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/singlestore-core/columns/serial.ts
var SingleStoreSerialBuilder = class extends SingleStoreColumnBuilderWithAutoIncrement {
	static [entityKind] = "SingleStoreSerialBuilder";
	constructor(name) {
		super(name, "number uint53", "SingleStoreSerial");
		this.config.hasDefault = true;
		this.config.autoIncrement = true;
	}
	/** @internal */
	build(table) {
		return new SingleStoreSerial(table, this.config);
	}
};
var SingleStoreSerial = class extends SingleStoreColumnWithAutoIncrement {
	static [entityKind] = "SingleStoreSerial";
	getSQLType() {
		return "serial";
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return Number(value);
		return value;
	};
};
function serial(name) {
	return new SingleStoreSerialBuilder(name ?? "");
}

//#endregion
export { SingleStoreSerial, SingleStoreSerialBuilder, serial };
//# sourceMappingURL=serial.js.map