Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/singlestore-core/columns/vector.ts
var SingleStoreVectorBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilder {
	static [__entity_ts.entityKind] = "SingleStoreVectorBuilder";
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
var SingleStoreVector = class extends require_singlestore_core_columns_common.SingleStoreColumn {
	static [__entity_ts.entityKind] = "SingleStoreVector";
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
var SingleStoreBigIntVectorBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilder {
	static [__entity_ts.entityKind] = "SingleStoreBigIntVectorBuilder";
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
var SingleStoreBigIntVector = class extends require_singlestore_core_columns_common.SingleStoreColumn {
	static [__entity_ts.entityKind] = "SingleStoreBigIntVector";
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
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return config.elementType === "I64" ? new SingleStoreBigIntVectorBuilder(name, config) : new SingleStoreVectorBuilder(name, config);
}

//#endregion
exports.SingleStoreBigIntVector = SingleStoreBigIntVector;
exports.SingleStoreBigIntVectorBuilder = SingleStoreBigIntVectorBuilder;
exports.SingleStoreVector = SingleStoreVector;
exports.SingleStoreVectorBuilder = SingleStoreVectorBuilder;
exports.vector = vector;
//# sourceMappingURL=vector.cjs.map