Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __table_ts = require("../table.cjs");
let __utils_ts = require("../utils.cjs");
let __column_ts = require("../column.cjs");
let __column_builder_ts = require("../column-builder.cjs");
let arktype = require("arktype");

//#region src/arktype/column.ts
const literalSchema = arktype.type.string.or(arktype.type.number).or(arktype.type.boolean).or(arktype.type.null);
const jsonSchema = literalSchema.or(arktype.type.unknown.as().array()).or(arktype.type.object.as());
const bufferSchema = arktype.type.unknown.narrow((value) => value instanceof Buffer).as().describe("a Buffer instance");
function columnToSchema(column) {
	let schema;
	const dimensions = column.dimensions;
	if (typeof dimensions === "number" && dimensions > 0) return pgArrayColumnToSchema(column, dimensions);
	const { type: columnType, constraint } = (0, __column_builder_ts.extractExtendedColumnType)(column);
	switch (columnType) {
		case "array":
			schema = arrayColumnToSchema(column, constraint);
			break;
		case "object":
			schema = objectColumnToSchema(column, constraint);
			break;
		case "number":
			schema = numberColumnToSchema(column, constraint);
			break;
		case "bigint":
			schema = bigintColumnToSchema(column, constraint);
			break;
		case "boolean":
			schema = arktype.type.boolean;
			break;
		case "string":
			schema = stringColumnToSchema(column, constraint);
			break;
		case "custom":
			schema = arktype.type.unknown;
			break;
		default: schema = arktype.type.unknown;
	}
	return schema;
}
function numberColumnToSchema(column, constraint) {
	let min;
	let max;
	let integer = false;
	switch (constraint) {
		case "int8":
			min = __utils_ts.CONSTANTS.INT8_MIN;
			max = __utils_ts.CONSTANTS.INT8_MAX;
			integer = true;
			break;
		case "uint8":
			min = 0;
			max = __utils_ts.CONSTANTS.INT8_UNSIGNED_MAX;
			integer = true;
			break;
		case "int16":
			min = __utils_ts.CONSTANTS.INT16_MIN;
			max = __utils_ts.CONSTANTS.INT16_MAX;
			integer = true;
			break;
		case "uint16":
			min = 0;
			max = __utils_ts.CONSTANTS.INT16_UNSIGNED_MAX;
			integer = true;
			break;
		case "int24":
			min = __utils_ts.CONSTANTS.INT24_MIN;
			max = __utils_ts.CONSTANTS.INT24_MAX;
			integer = true;
			break;
		case "uint24":
			min = 0;
			max = __utils_ts.CONSTANTS.INT24_UNSIGNED_MAX;
			integer = true;
			break;
		case "int32":
			min = __utils_ts.CONSTANTS.INT32_MIN;
			max = __utils_ts.CONSTANTS.INT32_MAX;
			integer = true;
			break;
		case "uint32":
			min = 0;
			max = __utils_ts.CONSTANTS.INT32_UNSIGNED_MAX;
			integer = true;
			break;
		case "int53":
			min = Number.MIN_SAFE_INTEGER;
			max = Number.MAX_SAFE_INTEGER;
			integer = true;
			break;
		case "uint53":
			min = 0;
			max = Number.MAX_SAFE_INTEGER;
			integer = true;
			break;
		case "float":
			min = __utils_ts.CONSTANTS.INT24_MIN;
			max = __utils_ts.CONSTANTS.INT24_MAX;
			break;
		case "ufloat":
			min = 0;
			max = __utils_ts.CONSTANTS.INT24_UNSIGNED_MAX;
			break;
		case "double":
			min = __utils_ts.CONSTANTS.INT48_MIN;
			max = __utils_ts.CONSTANTS.INT48_MAX;
			break;
		case "udouble":
			min = 0;
			max = __utils_ts.CONSTANTS.INT48_UNSIGNED_MAX;
			break;
		case "year":
			min = 1901;
			max = 2155;
			integer = true;
			break;
		case "unsigned":
			min = 0;
			max = Number.MAX_SAFE_INTEGER;
			break;
		default:
			min = Number.MIN_SAFE_INTEGER;
			max = Number.MAX_SAFE_INTEGER;
			break;
	}
	return (integer ? arktype.type.keywords.number.integer : arktype.type.number).atLeast(min).atMost(max);
}
function pgArrayColumnToSchema(column, dimensions) {
	const [baseType, baseConstraint] = column.dataType.split(" ");
	let baseSchema;
	switch (baseType) {
		case "number":
			baseSchema = numberColumnToSchema(column, baseConstraint);
			break;
		case "bigint":
			baseSchema = bigintColumnToSchema(column, baseConstraint);
			break;
		case "boolean":
			baseSchema = arktype.type.boolean;
			break;
		case "string":
			baseSchema = stringColumnToSchema(column, baseConstraint);
			break;
		case "object":
			baseSchema = objectColumnToSchema(column, baseConstraint);
			break;
		case "array":
			baseSchema = arrayColumnToSchema(column, baseConstraint);
			break;
		default: baseSchema = arktype.type.unknown;
	}
	let schema = baseSchema.array();
	for (let i = 1; i < dimensions; i++) schema = schema.array();
	return schema;
}
function arrayColumnToSchema(column, constraint) {
	switch (constraint) {
		case "geometry":
		case "point": return (0, arktype.type)([arktype.type.number, arktype.type.number]);
		case "line": return (0, arktype.type)([
			arktype.type.number,
			arktype.type.number,
			arktype.type.number
		]);
		case "vector":
		case "halfvector": {
			const length = column.length;
			return length ? arktype.type.number.array().exactlyLength(length) : arktype.type.number.array();
		}
		case "int64vector": {
			const length = column.length;
			return length ? arktype.type.bigint.array().exactlyLength(length) : arktype.type.bigint.array();
		}
		case "basecolumn": {
			const baseColumn = column.baseColumn;
			if (baseColumn) {
				const length = column.length;
				const schema = columnToSchema(baseColumn).array();
				if (length) return schema.exactlyLength(length);
				return schema;
			}
			return arktype.type.unknown.array();
		}
		default: return arktype.type.unknown.array();
	}
}
function objectColumnToSchema(column, constraint) {
	switch (constraint) {
		case "buffer": return bufferSchema;
		case "date": return arktype.type.Date;
		case "geometry":
		case "point": return (0, arktype.type)({
			x: arktype.type.number,
			y: arktype.type.number
		});
		case "json": return jsonSchema;
		case "line": return (0, arktype.type)({
			a: arktype.type.number,
			b: arktype.type.number,
			c: arktype.type.number
		});
		default: return (0, arktype.type)({});
	}
}
const unsignedBigintNarrow = (v, ctx) => v < 0n ? ctx.mustBe("greater than") : v > __utils_ts.CONSTANTS.INT64_UNSIGNED_MAX ? ctx.mustBe("less than") : true;
const bigintNarrow = (v, ctx) => v < __utils_ts.CONSTANTS.INT64_MIN ? ctx.mustBe("greater than") : v > __utils_ts.CONSTANTS.INT64_MAX ? ctx.mustBe("less than") : true;
const bigintStringModeSchema = arktype.type.string.narrow((v, ctx) => {
	if (typeof v !== "string") return ctx.mustBe("a string");
	if (!/^-?\d+$/.test(v)) return ctx.mustBe("a string representing a number");
	const bigint = BigInt(v);
	if (bigint < __utils_ts.CONSTANTS.INT64_MIN) return ctx.mustBe("greater than");
	if (bigint > __utils_ts.CONSTANTS.INT64_MAX) return ctx.mustBe("less than");
	return true;
});
const unsignedBigintStringModeSchema = arktype.type.string.narrow((v, ctx) => {
	if (typeof v !== "string") return ctx.mustBe("a string");
	if (!/^\d+$/.test(v)) return ctx.mustBe("a string representing a number");
	const bigint = BigInt(v);
	if (bigint < 0) return ctx.mustBe("greater than");
	if (bigint > __utils_ts.CONSTANTS.INT64_MAX) return ctx.mustBe("less than");
	return true;
});
function bigintColumnToSchema(column, constraint) {
	switch (constraint) {
		case "int64": return arktype.type.bigint.narrow(bigintNarrow);
		case "uint64": return arktype.type.bigint.narrow(unsignedBigintNarrow);
	}
	return arktype.type.bigint;
}
function stringColumnToSchema(column, constraint) {
	const { name: columnName, length, isLengthExact } = column;
	if (constraint === "binary") return (0, arktype.type)(`/^[01]${length ? `{${isLengthExact ? length : `0,${length}`}}` : "*"}$/`).describe(`a string containing ones or zeros${length ? ` while being ${isLengthExact ? "" : "up to "}${length} characters long` : ""}`);
	if (constraint === "uuid") return (0, arktype.type)(/^[\da-f]{8}(?:-[\da-f]{4}){3}-[\da-f]{12}$/iu).describe("a RFC-4122-compliant UUID");
	if (constraint === "enum") {
		const enumValues = column.enumValues;
		if (!enumValues) throw new Error(`Column "${(0, __table_ts.getTableName)((0, __column_ts.getColumnTable)(column))}"."${columnName}" is of 'enum' type, but lacks enum values`);
		return arktype.type.enumerated(...enumValues);
	}
	if (constraint === "int64") return bigintStringModeSchema;
	if (constraint === "uint64") return unsignedBigintStringModeSchema;
	return length && isLengthExact ? arktype.type.string.exactlyLength(length) : length ? arktype.type.string.atMostLength(length) : arktype.type.string;
}

//#endregion
exports.bigintNarrow = bigintNarrow;
exports.bigintStringModeSchema = bigintStringModeSchema;
exports.bufferSchema = bufferSchema;
exports.columnToSchema = columnToSchema;
exports.jsonSchema = jsonSchema;
exports.literalSchema = literalSchema;
exports.unsignedBigintNarrow = unsignedBigintNarrow;
exports.unsignedBigintStringModeSchema = unsignedBigintStringModeSchema;
//# sourceMappingURL=column.cjs.map