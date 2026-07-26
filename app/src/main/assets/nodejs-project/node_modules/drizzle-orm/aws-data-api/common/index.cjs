Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });

//#region src/aws-data-api/common/index.ts
function getValueFromDataApi(field) {
	if (field.stringValue !== void 0) return field.stringValue;
	if (field.booleanValue !== void 0) return field.booleanValue;
	if (field.doubleValue !== void 0) return field.doubleValue;
	if (field.isNull !== void 0) return null;
	if (field.longValue !== void 0) return field.longValue;
	if (field.blobValue !== void 0) return field.blobValue;
	if (field.arrayValue !== void 0) {
		if (field.arrayValue.stringValues !== void 0) return field.arrayValue.stringValues;
		if (field.arrayValue.longValues !== void 0) return field.arrayValue.longValues;
		if (field.arrayValue.doubleValues !== void 0) return field.arrayValue.doubleValues;
		if (field.arrayValue.booleanValues !== void 0) return field.arrayValue.booleanValues;
		if (field.arrayValue.arrayValues !== void 0) return field.arrayValue.arrayValues;
		throw new Error("Unknown array type");
	}
	throw new Error("Unknown type");
}
function toValueParam(value) {
	if (value === null) return { isNull: true };
	const valueType = typeof value;
	if (valueType === "string") return { stringValue: value };
	if (valueType === "number") return Number.isInteger(value) ? { longValue: value } : { doubleValue: value };
	if (valueType === "boolean") return { booleanValue: value };
	if (valueType === "bigint") return { stringValue: value.toString() };
	if (value instanceof Date) return { stringValue: value.toISOString().replace("T", " ").replace("Z", "") };
	if (typeof Buffer !== "undefined" && Buffer.isBuffer(value) || value instanceof Uint8Array) return { blobValue: value };
	throw new Error(`Unknown type for ${value}`);
}

//#endregion
exports.getValueFromDataApi = getValueFromDataApi;
exports.toValueParam = toValueParam;
//# sourceMappingURL=index.cjs.map