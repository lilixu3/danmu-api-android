import { SingleStoreColumn, SingleStoreColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/singlestore-core/columns/vector.ts
var SingleStoreVectorBuilder = class extends SingleStoreColumnBuilder {
	static [entityKind] = "SingleStoreVectorBuilder";
	constructor(name, config) {
		super(name, "array vector", "SingleStoreVector");
		this.config.length = config.dimensions;
		this.config.elementType = config.elementType;
		this.config.isLengthExact = true;
	}
	/** @internal */
	build(table) {
		return new SingleStoreVector(table, this.config);
	}
	/** @internal */
	generatedAlwaysAs(as, config) {
		throw new Error("Method not implemented.");
	}
};
var SingleStoreVector = class extends SingleStoreColumn {
	static [entityKind] = "SingleStoreVector";
	elementType = this.config.elementType;
	getSQLType() {
		return `vector(${this.config.length}, ${this.elementType || "F32"})`;
	}
	mapToDriverValue = (value) => {
		return `[${value.map((e) => e.toString()).join(",")}]`;
	};
	mapFromDriverValue = (value) => {
		if (typeof value === "string") {
			if (value.startsWith("[")) return value.slice(1, -1).split(",").map(Number);
			value = Buffer.from(value, "hex");
		}
		if (Buffer.isBuffer(value)) {
			const type = this.elementType || "F32";
			const bytearr = new Uint8Array(value);
			switch (type) {
				case "I8": return Array.from(new Int8Array(bytearr.buffer, 0, bytearr.length / 1));
				case "I16": return Array.from(new Int16Array(bytearr.buffer, 0, bytearr.length / 2));
				case "I32": return Array.from(new Int32Array(bytearr.buffer, 0, bytearr.length / 4));
				case "F32": return Array.from(new Float32Array(bytearr.buffer, 0, bytearr.length / 4));
				case "F64": return Array.from(new Float64Array(bytearr.buffer, 0, bytearr.length / 8));
			}
		}
		return value;
	};
};
var SingleStoreBigIntVectorBuilder = class extends SingleStoreColumnBuilder {
	static [entityKind] = "SingleStoreBigIntVectorBuilder";
	constructor(name, config) {
		super(name, "array int64vector", "SingleStoreBigIntVector");
		this.config.length = config.dimensions;
		this.config.isLengthExact = true;
	}
	/** @internal */
	build(table) {
		return new SingleStoreBigIntVector(table, this.config);
	}
	/** @internal */
	generatedAlwaysAs(as, config) {
		throw new Error("Method not implemented.");
	}
};
var SingleStoreBigIntVector = class extends SingleStoreColumn {
	static [entityKind] = "SingleStoreBigIntVector";
	elementType = "I64";
	getSQLType() {
		return `vector(${this.config.length}, ${this.elementType})`;
	}
	mapToDriverValue = (value) => {
		return `[${value.map((e) => e.toString()).join(",")}]`;
	};
	mapFromDriverValue = (value) => {
		if (typeof value === "string") {
			if (value.startsWith("[")) return value.slice(1, -1).split(",").map(BigInt);
			value = Buffer.from(value, "hex");
		}
		if (Buffer.isBuffer(value)) {
			const bytearr = new Uint8Array(value);
			return Array.from(new BigInt64Array(bytearr.buffer, 0, bytearr.length / 8));
		}
		return value;
	};
};
function vector(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return config.elementType === "I64" ? new SingleStoreBigIntVectorBuilder(name, config) : new SingleStoreVectorBuilder(name, config);
}

//#endregion
export { SingleStoreBigIntVector, SingleStoreBigIntVectorBuilder, SingleStoreVector, SingleStoreVectorBuilder, vector };
//# sourceMappingURL=vector.js.map