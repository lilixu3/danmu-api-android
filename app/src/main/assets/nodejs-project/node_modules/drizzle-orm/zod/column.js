import { CONSTANTS } from "../utils.js";
import { getTableName } from "../table.js";
import { getColumnTable } from "../column.js";
import { extractExtendedColumnType } from "../column-builder.js";
import { z } from "zod/v4";

//#region src/zod/column.ts
const literalSchema = z.union([
	z.string(),
	z.number(),
	z.boolean(),
	z.null()
]);
const jsonSchema = z.union([
	literalSchema,
	z.record(z.string(), z.any()),
	z.array(z.any())
]);
const bufferSchema = z.custom((v) => v instanceof Buffer);
function columnToSchema(column, factory) {
	const z$1 = factory?.zodInstance ?? z;
	const coerce = factory?.coerce ?? {};
	let schema;
	const dimensions = column.dimensions;
	if (typeof dimensions === "number" && dimensions > 0) return pgArrayColumnToSchema(column, dimensions, z$1, coerce);
	const { type, constraint } = extractExtendedColumnType(column);
	switch (type) {
		case "array":
			schema = arrayColumnToSchema(column, constraint, z$1, coerce);
			break;
		case "object":
			schema = objectColumnToSchema(column, constraint, z$1, coerce);
			break;
		case "number":
			schema = numberColumnToSchema(column, constraint, z$1, coerce);
			break;
		case "bigint":
			schema = bigintColumnToSchema(column, constraint, z$1, coerce);
			break;
		case "boolean":
			schema = coerce === true || coerce.boolean ? z$1.coerce.boolean() : z$1.boolean();
			break;
		case "string":
			schema = stringColumnToSchema(column, constraint, z$1, coerce);
			break;
		case "custom":
			schema = z$1.any();
			break;
		default: schema = z$1.any();
	}
	return schema;
}
function numberColumnToSchema(column, constraint, z, coerce) {
	let min;
	let max;
	let integer = false;
	switch (constraint) {
		case "int8":
			min = CONSTANTS.INT8_MIN;
			max = CONSTANTS.INT8_MAX;
			integer = true;
			break;
		case "uint8":
			min = 0;
			max = CONSTANTS.INT8_UNSIGNED_MAX;
			integer = true;
			break;
		case "int16":
			min = CONSTANTS.INT16_MIN;
			max = CONSTANTS.INT16_MAX;
			integer = true;
			break;
		case "uint16":
			min = 0;
			max = CONSTANTS.INT16_UNSIGNED_MAX;
			integer = true;
			break;
		case "int24":
			min = CONSTANTS.INT24_MIN;
			max = CONSTANTS.INT24_MAX;
			integer = true;
			break;
		case "uint24":
			min = 0;
			max = CONSTANTS.INT24_UNSIGNED_MAX;
			integer = true;
			break;
		case "int32":
			min = CONSTANTS.INT32_MIN;
			max = CONSTANTS.INT32_MAX;
			integer = true;
			break;
		case "uint32":
			min = 0;
			max = CONSTANTS.INT32_UNSIGNED_MAX;
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
			min = CONSTANTS.INT24_MIN;
			max = CONSTANTS.INT24_MAX;
			break;
		case "ufloat":
			min = 0;
			max = CONSTANTS.INT24_UNSIGNED_MAX;
			break;
		case "double":
			min = CONSTANTS.INT48_MIN;
			max = CONSTANTS.INT48_MAX;
			break;
		case "udouble":
			min = 0;
			max = CONSTANTS.INT48_UNSIGNED_MAX;
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
const bigintStringModeSchema = z.string().regex(/^-?\d+$/).transform(BigInt).pipe(z.bigint().gte(CONSTANTS.INT64_MIN).lte(CONSTANTS.INT64_MAX)).transform(String);
const unsignedBigintStringModeSchema = z.string().regex(/^\d+$/).transform(BigInt).pipe(z.bigint().gte(0n).lte(CONSTANTS.INT64_MAX)).transform(String);
function bigintColumnToSchema(column, constraint, z, coerce) {
	let min;
	let max;
	switch (constraint) {
		case "int64":
			min = CONSTANTS.INT64_MIN;
			max = CONSTANTS.INT64_MAX;
			break;
		case "uint64":
			min = 0n;
			max = CONSTANTS.INT64_UNSIGNED_MAX;
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
			return length ? z.array(z.bigint().min(CONSTANTS.INT64_MIN).max(CONSTANTS.INT64_MAX)).length(length) : z.array(z.bigint().min(CONSTANTS.INT64_MIN).max(CONSTANTS.INT64_MAX));
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
		if (!enumValues) throw new Error(`Column "${getTableName(getColumnTable(column))}"."${columnName}" is of 'enum' type, but lacks enum values`);
		return z.enum(enumValues);
	}
	if (constraint === "int64") return bigintStringModeSchema;
	if (constraint === "uint64") return unsignedBigintStringModeSchema;
	let schema = coerce === true || coerce?.string ? z.coerce.string() : z.string();
	schema = regex ? schema.regex(regex) : schema;
	return length && isLengthExact ? schema.length(length) : length ? schema.max(length) : schema;
}

//#endregion
export { bigintStringModeSchema, bufferSchema, columnToSchema, jsonSchema, literalSchema, unsignedBigintStringModeSchema };
//# sourceMappingURL=column.js.map