import { PgColumn, PgColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/pg-core/columns/enum.ts
var PgEnumObjectColumnBuilder = class extends PgColumnBuilder {
	static [entityKind] = "PgEnumObjectColumnBuilder";
	constructor(name, enumInstance) {
		super(name, "string enum", "PgEnumObjectColumn");
		this.config.enum = enumInstance;
	}
	/** @internal */
	build(table) {
		return new PgEnumObjectColumn(table, this.config);
	}
};
var PgEnumObjectColumn = class extends PgColumn {
	static [entityKind] = "PgEnumObjectColumn";
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
var PgEnumColumnBuilder = class extends PgColumnBuilder {
	static [entityKind] = "PgEnumColumnBuilder";
	constructor(name, enumInstance) {
		super(name, "string enum", "PgEnumColumn");
		this.config.enum = enumInstance;
	}
	/** @internal */
	build(table) {
		return new PgEnumColumn(table, this.config);
	}
};
var PgEnumColumn = class extends PgColumn {
	static [entityKind] = "PgEnumColumn";
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
export { PgEnumColumn, PgEnumColumnBuilder, PgEnumObjectColumn, PgEnumObjectColumnBuilder, isPgEnum, pgEnum, pgEnumObjectWithSchema, pgEnumWithSchema };
//# sourceMappingURL=enum.js.map