Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_utils = require('../utils.cjs');
let __table_ts = require("../table.cjs");
let __column_ts = require("../column.cjs");
let __column_builder_ts = require("../column-builder.cjs");
let valibot = require("valibot");
valibot = require_runtime.__toESM(valibot);

//#region src/valibot/column.ts
const literalSchema = valibot.union([
	valibot.string(),
	valibot.number(),
	valibot.boolean(),
	valibot.null()
]);
const jsonSchema = valibot.union([
	literalSchema,
	valibot.array(valibot.any()),
	valibot.record(valibot.string(), valibot.any())
]);
const bufferSchema = valibot.custom((v) => v instanceof Buffer);
function mapEnumValues(values) {
	return Object.fromEntries(values.map((value) => [value, value]));
}
function columnToSchema(column) {
	let schema;
	const dimensions = column.dimensions;
	if (typeof dimensions === "number" && dimensions > 0) return pgArrayColumnToSchema(column, dimensions);
	const { type, constraint } = (0, __column_builder_ts.extractExtendedColumnType)(column);
	switch (type) {
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
			schema = valibot.boolean();
			break;
		case "string":
			schema = stringColumnToSchema(column, constraint);
			break;
		case "custom":
			schema = valibot.any();
			break;
		default: schema = valibot.any();
	}
	return schema;
}
function numberColumnToSchema(column, constraint) {
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
	const actions = [valibot.minValue(min), valibot.maxValue(max)];
	if (integer) actions.push(valibot.integer());
	return valibot.pipe(valibot.number(), ...actions);
}
const bigintStringModeSchema = valibot.pipe(valibot.string(), valibot.regex(/^-?\d+$/), valibot.transform((v) => BigInt(v)), valibot.minValue(require_utils.CONSTANTS.INT64_MIN), valibot.maxValue(require_utils.CONSTANTS.INT64_MAX), valibot.transform((v) => v.toString()));
const unsignedBigintStringModeSchema = valibot.pipe(valibot.string(), valibot.regex(/^\d+$/), valibot.transform((v) => BigInt(v)), valibot.minValue(0n), valibot.maxValue(require_utils.CONSTANTS.INT64_MAX), valibot.transform((v) => v.toString()));
function bigintColumnToSchema(column, constraint) {
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
	const actions = [];
	if (min !== void 0) actions.push(valibot.minValue(min));
	if (max !== void 0) actions.push(valibot.maxValue(max));
	return actions.length > 0 ? valibot.pipe(valibot.bigint(), ...actions) : valibot.bigint();
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
			baseSchema = valibot.boolean();
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
		default: baseSchema = valibot.any();
	}
	let schema = valibot.array(baseSchema);
	for (let i = 1; i < dimensions; i++) schema = valibot.array(schema);
	return schema;
}
function arrayColumnToSchema(column, constraint) {
	switch (constraint) {
		case "geometry":
		case "point": return valibot.tuple([valibot.number(), valibot.number()]);
		case "line": return valibot.tuple([
			valibot.number(),
			valibot.number(),
			valibot.number()
		]);
		case "vector":
		case "halfvector": {
			const { length } = column;
			return length ? valibot.pipe(valibot.array(valibot.number()), valibot.length(length)) : valibot.array(valibot.number());
		}
		case "int64vector": {
			const length = column.length;
			return length ? valibot.pipe(valibot.array(valibot.pipe(valibot.bigint(), valibot.minValue(require_utils.CONSTANTS.INT64_MIN), valibot.maxValue(require_utils.CONSTANTS.INT64_MAX))), valibot.length(length)) : valibot.array(valibot.pipe(valibot.bigint(), valibot.minValue(require_utils.CONSTANTS.INT64_MIN), valibot.maxValue(require_utils.CONSTANTS.INT64_MAX)));
		}
		case "basecolumn": {
			const baseColumn = column.baseColumn;
			if (baseColumn) {
				const { length } = column;
				const schema = valibot.array(columnToSchema(baseColumn));
				if (length) return valibot.pipe(schema, valibot.length(length));
				return schema;
			}
			return valibot.array(valibot.any());
		}
		default: return valibot.array(valibot.any());
	}
}
function objectColumnToSchema(column, constraint) {
	switch (constraint) {
		case "buffer": return bufferSchema;
		case "date": return valibot.date();
		case "geometry":
		case "point": return valibot.object({
			x: valibot.number(),
			y: valibot.number()
		});
		case "json": return jsonSchema;
		case "line": return valibot.object({
			a: valibot.number(),
			b: valibot.number(),
			c: valibot.number()
		});
		default: return valibot.looseObject({});
	}
}
function stringColumnToSchema(column, constraint) {
	const { name: columnName, length, isLengthExact } = column;
	let regex;
	if (constraint === "binary") regex = /^[01]*$/;
	if (constraint === "uuid") return valibot.pipe(valibot.string(), valibot.uuid());
	if (constraint === "enum") {
		const enumValues = column.enumValues;
		if (!enumValues) throw new Error(`Column "${(0, __table_ts.getTableName)((0, __column_ts.getColumnTable)(column))}"."${columnName}" is of 'enum' type, but lacks enum values`);
		return valibot.enum(mapEnumValues(enumValues));
	}
	if (constraint === "int64") return bigintStringModeSchema;
	if (constraint === "uint64") return unsignedBigintStringModeSchema;
	const actions = [];
	if (regex) actions.push(valibot.regex(regex));
	if (length && isLengthExact) actions.push(valibot.length(length));
	else if (length) actions.push(valibot.maxLength(length));
	return actions.length > 0 ? valibot.pipe(valibot.string(), ...actions) : valibot.string();
}

//#endregion
exports.bigintStringModeSchema = bigintStringModeSchema;
exports.bufferSchema = bufferSchema;
exports.columnToSchema = columnToSchema;
exports.jsonSchema = jsonSchema;
exports.literalSchema = literalSchema;
exports.mapEnumValues = mapEnumValues;
exports.unsignedBigintStringModeSchema = unsignedBigintStringModeSchema;
//# sourceMappingURL=column.cjs.map