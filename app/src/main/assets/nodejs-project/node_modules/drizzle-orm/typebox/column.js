import { CONSTANTS } from "../utils.js";
import { getTableName } from "../table.js";
import { getColumnTable } from "../column.js";
import { extractExtendedColumnType } from "../column-builder.js";
import Type from "typebox";

//#region src/typebox/column.ts
var TBuffer = class TBuffer extends Type.Base {
	Check(value) {
		return value instanceof Buffer;
	}
	Errors(value) {
		return !this.Check(value) ? [{ message: "not a Buffer" }] : [];
	}
	Clone() {
		return new TBuffer();
	}
};
var TDate = class TDate extends Type.Base {
	Check(value) {
		return value instanceof Date;
	}
	Errors(value) {
		return !this.Check(value) ? [{ message: "not a Date" }] : [];
	}
	Clone() {
		return new TDate();
	}
};
const literalSchema = Type.Union([
	Type.String(),
	Type.Number(),
	Type.Boolean(),
	Type.Null()
]);
const jsonSchema = Type.Union([
	literalSchema,
	Type.Array(Type.Any()),
	Type.Record(Type.String(), Type.Any())
]);
function mapEnumValues(values) {
	return Object.fromEntries(values.map((value) => [value, value]));
}
function columnToSchema(column, t) {
	let schema;
	const dimensions = column.dimensions;
	if (typeof dimensions === "number" && dimensions > 0) return pgArrayColumnToSchema(column, dimensions, t);
	const { type, constraint } = extractExtendedColumnType(column);
	switch (type) {
		case "array":
			schema = arrayColumnToSchema(column, constraint, t);
			break;
		case "object":
			schema = objectColumnToSchema(column, constraint, t);
			break;
		case "number":
			schema = numberColumnToSchema(column, constraint, t);
			break;
		case "bigint":
			schema = bigintColumnToSchema(column, constraint, t);
			break;
		case "boolean":
			schema = t.Boolean();
			break;
		case "string":
			schema = stringColumnToSchema(column, constraint, t);
			break;
		case "custom":
			schema = t.Any();
			break;
		default: schema = t.Any();
	}
	return schema;
}
function numberColumnToSchema(column, constraint, t) {
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
	return t[integer ? "Integer" : "Number"]({
		minimum: min,
		maximum: max
	});
}
var TBigIntString = class TBigIntString extends Type.Base {
	Check(value) {
		if (typeof value !== "string" || !/^-?\d+$/.test(value)) return false;
		const bigint = BigInt(value);
		if (bigint < CONSTANTS.INT64_MIN || bigint > CONSTANTS.INT64_MAX) return false;
		return true;
	}
	Errors(value) {
		return !this.Check(value) ? [{ message: "not a bigint string" }] : [];
	}
	Clone() {
		return new TBigIntString();
	}
};
var TUnsignedBigIntString = class TUnsignedBigIntString extends Type.Base {
	Check(value) {
		if (typeof value !== "string" || !/^\d+$/.test(value)) return false;
		const bigint = BigInt(value);
		if (bigint < 0 || bigint > CONSTANTS.INT64_MAX) return false;
		return true;
	}
	Errors(value) {
		return !this.Check(value) ? [{ message: "not an unsigned bigint string" }] : [];
	}
	Clone() {
		return new TUnsignedBigIntString();
	}
};
function pgArrayColumnToSchema(column, dimensions, t) {
	const [baseType, baseConstraint] = column.dataType.split(" ");
	let baseSchema;
	switch (baseType) {
		case "number":
			baseSchema = numberColumnToSchema(column, baseConstraint, t);
			break;
		case "bigint":
			baseSchema = bigintColumnToSchema(column, baseConstraint, t);
			break;
		case "boolean":
			baseSchema = t.Boolean();
			break;
		case "string":
			baseSchema = stringColumnToSchema(column, baseConstraint, t);
			break;
		case "object":
			baseSchema = objectColumnToSchema(column, baseConstraint, t);
			break;
		case "array":
			baseSchema = arrayColumnToSchema(column, baseConstraint, t);
			break;
		default: baseSchema = t.Any();
	}
	let schema = t.Array(baseSchema);
	for (let i = 1; i < dimensions; i++) schema = t.Array(schema);
	return schema;
}
function arrayColumnToSchema(column, constraint, t) {
	switch (constraint) {
		case "geometry":
		case "point": return t.Tuple([t.Number(), t.Number()]);
		case "line": return t.Tuple([
			t.Number(),
			t.Number(),
			t.Number()
		]);
		case "vector":
		case "halfvector": {
			const length = column.length;
			const sizeParam = length ? {
				minItems: length,
				maxItems: length
			} : void 0;
			return t.Array(t.Number(), sizeParam);
		}
		case "int64vector": {
			const length = column.length;
			const sizeParam = length ? {
				minItems: length,
				maxItems: length
			} : void 0;
			return t.Array(t.BigInt({
				minimum: CONSTANTS.INT64_MIN,
				maximum: CONSTANTS.INT64_MAX
			}), sizeParam);
		}
		case "basecolumn": {
			const baseColumn = column.baseColumn;
			if (baseColumn) {
				const size = column.length;
				const sizeParam = size ? {
					minItems: size,
					maxItems: size
				} : void 0;
				return t.Array(columnToSchema(baseColumn, t), sizeParam);
			}
			return t.Array(t.Any());
		}
		default: return t.Array(t.Any());
	}
}
function objectColumnToSchema(column, constraint, t) {
	switch (constraint) {
		case "buffer": return new TBuffer();
		case "date": return new TDate();
		case "geometry":
		case "point": return t.Object({
			x: t.Number(),
			y: t.Number()
		});
		case "json": return jsonSchema;
		case "line": return t.Object({
			a: t.Number(),
			b: t.Number(),
			c: t.Number()
		});
		default: return t.Object({});
	}
}
function bigintColumnToSchema(column, constraint, t) {
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
	const options = {};
	if (min !== void 0) options.minimum = min;
	if (max !== void 0) options.maximum = max;
	return t.BigInt(Object.keys(options).length > 0 ? options : void 0);
}
function stringColumnToSchema(column, constraint, t) {
	const { name: columnName, length, isLengthExact } = column;
	let regex;
	if (constraint === "binary") regex = /^[01]*$/;
	if (constraint === "uuid") return t.String({ format: "uuid" });
	if (constraint === "enum") {
		const enumValues = column.enumValues;
		if (!enumValues) throw new Error(`Column "${getTableName(getColumnTable(column))}"."${columnName}" is of 'enum' type, but lacks enum values`);
		return t.Enum(mapEnumValues(enumValues));
	}
	if (constraint === "int64") return new TBigIntString();
	if (constraint === "uint64") return new TUnsignedBigIntString();
	const options = {};
	if (length !== void 0 && isLengthExact) {
		options.minLength = length;
		options.maxLength = length;
	} else if (length !== void 0) options.maxLength = length;
	if (regex) options.pattern = regex;
	return t.String(Object.keys(options).length > 0 ? options : void 0);
}

//#endregion
export { TBigIntString, TBuffer, TDate, TUnsignedBigIntString, columnToSchema, jsonSchema, literalSchema, mapEnumValues };
//# sourceMappingURL=column.js.map