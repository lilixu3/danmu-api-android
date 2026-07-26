import { entityKind } from "../entity.js";

//#region src/mssql-core/checks.ts
var CheckBuilder = class {
	static [entityKind] = "MsSqlCheckBuilder";
	brand;
	constructor(name, value) {
		this.name = name;
		this.value = value;
	}
	/** @internal */
	build(table) {
		return new Check(table, this);
	}
};
var Check = class {
	static [entityKind] = "MsSqlCheck";
	name;
	value;
	constructor(table, builder) {
		this.table = table;
		this.name = builder.name;
		this.value = builder.value;
	}
};
function check(name, value) {
	return new CheckBuilder(name, value);
}

//#endregion
export { Check, CheckBuilder, check };
//# sourceMappingURL=checks.js.map