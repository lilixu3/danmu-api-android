import { CockroachColumn, CockroachColumnWithArrayBuilder } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/cockroach-core/columns/enum.ts
var CockroachEnumObjectColumnBuilder = class extends CockroachColumnWithArrayBuilder {
	static [entityKind] = "CockroachEnumObjectColumnBuilder";
	constructor(name, enumInstance) {
		super(name, "string enum", "CockroachEnumObjectColumn");
		this.config.enum = enumInstance;
	}
	/** @internal */
	build(table) {
		return new CockroachEnumObjectColumn(table, this.config);
	}
};
var CockroachEnumObjectColumn = class extends CockroachColumn {
	static [entityKind] = "CockroachEnumObjectColumn";
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
var CockroachEnumColumnBuilder = class extends CockroachColumnWithArrayBuilder {
	static [entityKind] = "CockroachEnumColumnBuilder";
	constructor(name, enumInstance) {
		super(name, "string enum", "CockroachEnumColumn");
		this.config.enum = enumInstance;
	}
	/** @internal */
	build(table) {
		return new CockroachEnumColumn(table, this.config);
	}
};
var CockroachEnumColumn = class extends CockroachColumn {
	static [entityKind] = "CockroachEnumColumn";
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
export { CockroachEnumColumn, CockroachEnumColumnBuilder, CockroachEnumObjectColumn, CockroachEnumObjectColumnBuilder, cockroachEnum, cockroachEnumObjectWithSchema, cockroachEnumWithSchema, isCockroachEnum };
//# sourceMappingURL=enum.js.map