Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/pg-core/columns/enum.ts
var PgEnumObjectColumnBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgEnumObjectColumnBuilder";
	constructor(name, enumInstance) {
		super(name, "string enum", "PgEnumObjectColumn");
		this.config.enum = enumInstance;
	}
	/** @internal */
	build(table) {
		return new PgEnumObjectColumn(table, this.config);
	}
};
var PgEnumObjectColumn = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgEnumObjectColumn";
	/** @internal */
	codec = "enum";
	enum;
	enumValues;
	constructor(table, config) {
		super(table, config);
		this.enum = config.enum;
		this.enumValues = config.enum.enumValues;
	}
	getSQLType() {
		return this.enum.enumName;
	}
};
const isPgEnumSym = Symbol.for("drizzle:isPgEnum");
function isPgEnum(obj) {
	return !!obj && typeof obj === "function" && isPgEnumSym in obj && obj[isPgEnumSym] === true;
}
var PgEnumColumnBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgEnumColumnBuilder";
	constructor(name, enumInstance) {
		super(name, "string enum", "PgEnumColumn");
		this.config.enum = enumInstance;
	}
	/** @internal */
	build(table) {
		return new PgEnumColumn(table, this.config);
	}
};
var PgEnumColumn = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgEnumColumn";
	/** @internal */
	codec = "enum";
	enum;
	enumValues;
	constructor(table, config) {
		super(table, config);
		this.enum = config.enum;
		this.enumValues = config.enum.enumValues;
	}
	getSQLType() {
		return this.enum.enumName;
	}
};
function pgEnum(enumName, input) {
	return Array.isArray(input) ? pgEnumWithSchema(enumName, [...input], void 0) : pgEnumObjectWithSchema(enumName, input, void 0);
}
/** @internal */
function pgEnumWithSchema(enumName, values, schema) {
	const enumInstance = Object.assign((name) => new PgEnumColumnBuilder(name ?? "", enumInstance), {
		enumName,
		enumValues: values,
		schema,
		[isPgEnumSym]: true
	});
	return enumInstance;
}
/** @internal */
function pgEnumObjectWithSchema(enumName, values, schema) {
	const enumInstance = Object.assign((name) => new PgEnumObjectColumnBuilder(name ?? "", enumInstance), {
		enumName,
		enumValues: Object.values(values),
		schema,
		[isPgEnumSym]: true
	});
	return enumInstance;
}

//#endregion
exports.PgEnumColumn = PgEnumColumn;
exports.PgEnumColumnBuilder = PgEnumColumnBuilder;
exports.PgEnumObjectColumn = PgEnumObjectColumn;
exports.PgEnumObjectColumnBuilder = PgEnumObjectColumnBuilder;
exports.isPgEnum = isPgEnum;
exports.pgEnum = pgEnum;
exports.pgEnumObjectWithSchema = pgEnumObjectWithSchema;
exports.pgEnumWithSchema = pgEnumWithSchema;
//# sourceMappingURL=enum.cjs.map