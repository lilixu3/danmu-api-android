Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/cockroach-core/columns/enum.ts
var CockroachEnumObjectColumnBuilder = class extends require_cockroach_core_columns_common.CockroachColumnWithArrayBuilder {
	static [__entity_ts.entityKind] = "CockroachEnumObjectColumnBuilder";
	constructor(name, enumInstance) {
		super(name, "string enum", "CockroachEnumObjectColumn");
		this.config.enum = enumInstance;
	}
	/** @internal */
	build(table) {
		return new CockroachEnumObjectColumn(table, this.config);
	}
};
var CockroachEnumObjectColumn = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachEnumObjectColumn";
	enum;
	enumValues = this.config.enum.enumValues;
	constructor(table, config) {
		super(table, config);
		this.enum = config.enum;
	}
	getSQLType() {
		return this.enum.enumName;
	}
};
const isCockroachEnumSym = Symbol.for("drizzle:isCockroachEnum");
function isCockroachEnum(obj) {
	return !!obj && typeof obj === "function" && isCockroachEnumSym in obj && obj[isCockroachEnumSym] === true;
}
var CockroachEnumColumnBuilder = class extends require_cockroach_core_columns_common.CockroachColumnWithArrayBuilder {
	static [__entity_ts.entityKind] = "CockroachEnumColumnBuilder";
	constructor(name, enumInstance) {
		super(name, "string enum", "CockroachEnumColumn");
		this.config.enum = enumInstance;
	}
	/** @internal */
	build(table) {
		return new CockroachEnumColumn(table, this.config);
	}
};
var CockroachEnumColumn = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachEnumColumn";
	enum = this.config.enum;
	enumValues = this.config.enum.enumValues;
	constructor(table, config) {
		super(table, config);
		this.enum = config.enum;
	}
	getSQLType() {
		return this.enum.enumName;
	}
};
function cockroachEnum(enumName, input) {
	return Array.isArray(input) ? cockroachEnumWithSchema(enumName, [...input], void 0) : cockroachEnumObjectWithSchema(enumName, input, void 0);
}
/** @internal */
function cockroachEnumWithSchema(enumName, values, schema) {
	const enumInstance = Object.assign((name) => new CockroachEnumColumnBuilder(name ?? "", enumInstance), {
		enumName,
		enumValues: values,
		schema,
		[isCockroachEnumSym]: true
	});
	return enumInstance;
}
/** @internal */
function cockroachEnumObjectWithSchema(enumName, values, schema) {
	const enumInstance = Object.assign((name) => new CockroachEnumObjectColumnBuilder(name ?? "", enumInstance), {
		enumName,
		enumValues: Object.values(values),
		schema,
		[isCockroachEnumSym]: true
	});
	return enumInstance;
}

//#endregion
exports.CockroachEnumColumn = CockroachEnumColumn;
exports.CockroachEnumColumnBuilder = CockroachEnumColumnBuilder;
exports.CockroachEnumObjectColumn = CockroachEnumObjectColumn;
exports.CockroachEnumObjectColumnBuilder = CockroachEnumObjectColumnBuilder;
exports.cockroachEnum = cockroachEnum;
exports.cockroachEnumObjectWithSchema = cockroachEnumObjectWithSchema;
exports.cockroachEnumWithSchema = cockroachEnumWithSchema;
exports.isCockroachEnum = isCockroachEnum;
//# sourceMappingURL=enum.cjs.map