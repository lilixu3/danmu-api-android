Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_utils = require('../utils.cjs');
let __table_ts = require("../table.cjs");
let __column_ts = require("../column.cjs");
let __column_builder_ts = require("../column-builder.cjs");
let zod_v4 = require("zod/v4");

//#region src/zod/column.ts
const literalSchema = zod_v4.z.union([
	zod_v4.z.string(),
	zod_v4.z.number(),
	zod_v4.z.boolean(),
	zod_v4.z.null()
]);
const jsonSchema = zod_v4.z.union([
	literalSchema,
	zod_v4.z.record(zod_v4.z.string(), zod_v4.z.any()),
	zod_v4.z.array(zod_v4.z.any())
]);
const bufferSchema = zod_v4.z.custom((v) => v instanceof Buffer);
function columnToSchema(column, factory) {
	const z = factory?.zodInstance ?? zod_v4.z;
	const coerce = factory?.coerce ?? {};
	let schema;
	const dimensions = column.dimensions;
	if (typeof dimensions === "number" && dimensions > 0) return pgArrayColumnToSchema(column, dimensions, z, coerce);
	const { type, constraint } = (0, __column_builder_ts.extractExtendedColumnType)(column);
	switch (type) {
		case "array":
			schema = arrayColumnToSchema(column, constraint, z, coerce);
			break;
		case "object":
			schema = objectColumnToSchema(column, constraint, z, coerce);
			break;
		case "number":
			schema = numberColumnToSchema(column, constraint, z, coerce);
			break;
		case "bigint":
			schema = bigintColumnToSchema(column, constraint, z, coerce);
			break;
		case "boolean":
			schema = coerce === true || coerce.boolean ? z.coerce.boolean() : z.boolean();
			break;
		case "string":
			schema = stringColumnToSchema(column, constraint, z, coerce);
			break;
		case "custom":
			schema = z.any();
			break;
		default: schema = z.any();
	}
	return schema;
}
function numberColumnToSchema(column, constraint, z, coerce) {
	let min;
	let max;
	let integer = false;
	switch (constraint) {
		case "int8":
			min = require_utils.CONSTANTS.INT8_MIN;
			max = require_utils.CONSTANTS.INT8_MAX;
			integer = true;
			break;
		case "uint8":
			min = 0;
			max = require_utils.CONSTANTS.INT8_UNSIGNED_MAX;
			integer = true;
			break;
		case "int16":
			min = require_utils.CONSTANTS.INT16_MIN;
			max = require_utils.CONSTANTS.INT16_MAX;
			integer = true;
			break;
		case "uint16":
			min = 0;
			max = require_utils.CONSTANTS.INT16_UNSIGNED_MAX;
			integer = true;
			break;
		case "int24":
			min = require_utils.CONSTANTS.INT24_MIN;
			max = require_utils.CONSTANTS.INT24_MAX;
			integer = true;
			break;
		case "uint24":
			min = 0;
			max = require_utils.CONSTANTS.INT24_UNSIGNED_MAX;
			integer = true;
			break;
		case "int32":
			min = require_utils.CONSTANTS.INT32_MIN;
			max = require_utils.CONSTANTS.INT32_MAX;
			integer = true;
			break;
		case "uint32":
			min = 0;
			max = require_utils.CONSTANTS.INT32_UNSIGNED_MAX;
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
			min = require_utils.CONSTANTS.INT24_MIN;
			max = require_utils.CONSTANTS.INT24_MAX;
			break;
		case "ufloat":
			min = 0;
			max = require_utils.CONSTANTS.INT24_UNSIGNED_MAX;
			break;
		case "double":
			min = require_utils.CONSTANTS.INT48_MIN;
			max = require_utils.CONSTANTS.INT48_MAX;
			break;
		case "udouble":
			min = 0;
			max = require_utils.CONSTANTS.INT48_UNSIGNED_MAX;
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
	let schema = coerce === true || coerce?.number ? integer ? z.coerce.number().int() : z.coerce.number() : integer ? z.int() : z.number();
	schema = schema.gte(min).lte(max);
	return schema;
}
const bigintStringModeSchema = zod_v4.z.string().regex(/^-?\d+$/).transform(BigInt).pipe(zod_v4.z.bigint().gte(require_utils.CONSTANTS.INT64_MIN).lte(require_utils.CONSTANTS.INT64_MAX)).transform(String);
const unsignedBigintStringModeSchema = zod_v4.z.string().regex(/^\d+$/).transform(BigInt).pipe(zod_v4.z.bigint().gte(0n).lte(require_utils.CONSTANTS.INT64_MAX)).transform(String);
function bigintColumnToSchema(column, constraint, z, coerce) {
	let min;
	let max;
	switch (constraint) {
		case "int64":
			min = require_utils.CONSTANTS.INT64_MIN;
			max = require_utils.CONSTANTS.INT64_MAX;
			break;
		case "uint64":
			min = 0n;
			max = require_utils.CONSTANTS.INT64_UNSIGNED_MAX;
			break;
	}
	let schema = coerce === true || coerce?.bigint ? z.coerce.bigint() : z.bigint();
	if (min !== void 0) schema = schema.min(min);
	if (max !== void 0) schema = schema.max(max);
	return schema;
}
function pgArrayColumnToSchema(column, dimensions, z, coerce) {
	const [baseType, baseConstraint] = column.dataType.split(" ");
	let baseSchema;
	switch (baseType) {
		case "number":
			baseSchema = numberColumnToSchema(column, baseConstraint, z, coerce);
			break;
		case "bigint":
			baseSchema = bigintColumnToSchema(column, baseConstraint, z, coerce);
			break;
		case "boolean":
			baseSchema = coerce === true || coerce?.boolean ? z.coerce.boolean() : z.boolean();
			break;
		case "string":
			baseSchema = stringColumnToSchema(column, baseConstraint, z, coerce);
			break;
		case "object":
			baseSchema = objectColumnToSchema(column, baseConstraint, z, coerce);
			break;
		case "array":
			baseSchema = arrayColumnToSchema(column, baseConstraint, z, coerce);
			break;
		default: baseSchema = z.any();
	}
	let schema = z.array(baseSchema);
	for (let i = 1; i < dimensions; i++) schema = z.array(schema);
	return schema;
}
function arrayColumnToSchema(column, constraint, z, coerce) {
	switch (constraint) {
		case "geometry":
		case "point": return z.tuple([z.number(), z.number()]);
		case "line": return z.tuple([
			z.number(),
			z.number(),
			z.number()
		]);
		case "vector":
		case "halfvector": {
			const length = column.length;
			return length ? z.array(z.number()).length(length) : z.array(z.number());
		}
		case "int64vector": {
			const length = column.length;
			return length ? z.array(z.bigint().min(require_utils.CONSTANTS.INT64_MIN).max(require_utils.CONSTANTS.INT64_MAX)).length(length) : z.array(z.bigint().min(require_utils.CONSTANTS.INT64_MIN).max(require_utils.CONSTANTS.INT64_MAX));
		}
		case "basecolumn": {
			const baseColumn = column.baseColumn;
			if (baseColumn) {
				const baseSchema = columnToSchema(baseColumn, {
					zodInstance: z,
					coerce
				});
				const length = column.length;
				const schema = z.array(baseSchema);
				if (length) return schema.length(length);
				return schema;
			}
			return z.array(z.any());
		}
		default: return z.array(z.any());
	}
}
function objectColumnToSchema(column, constraint, z, coerce) {
	switch (constraint) {
		case "buffer": return bufferSchema;
		case "date": return coerce === true || coerce?.date ? z.coerce.date() : z.date();
		case "geometry":
		case "point": return z.object({
			x: z.number(),
			y: z.number()
		});
		case "json": return jsonSchema;
		case "line": return z.object({
			a: z.number(),
			b: z.number(),
			c: z.number()
		});
		default: return z.looseObject({});
	}
}
function stringColumnToSchema(column, constraint, z, coerce) {
	const { name: columnName, length, isLengthExact } = column;
	let regex;
	if (constraint === "binary") regex = /^[01]*$/;
	if (constraint === "uuid") return z.uuid();
	if (constraint === "enum") {
		const enumValues = column.enumValues;
		if (!enumValues) throw new Error(`Column "${(0, __table_ts.getTableName)((0, __column_ts.getColumnTable)(column))}"."${columnName}" is of 'enum' type, but lacks enum values`);
		return z.enum(enumValues);
	}
	if (constraint === "int64") return bigintStringModeSchema;
	if (constraint === "uint64") return unsignedBigintStringModeSchema;
	let schema = coerce === true || coerce?.string ? z.coerce.string() : z.string();
	schema = regex ? schema.regex(regex) : schema;
	return length && isLengthExact ? schema.length(length) : length ? schema.max(length) : schema;
}

//#endregion
exports.bigintStringModeSchema = bigintStringModeSchema;
exports.bufferSchema = bufferSchema;
exports.columnToSchema = columnToSchema;
exports.jsonSchema = jsonSchema;
exports.literalSchema = literalSchema;
exports.unsignedBigintStringModeSchema = unsignedBigintStringModeSchema;
//# sourceMappingURL=column.cjs.map