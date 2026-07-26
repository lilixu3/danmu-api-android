import { entityKind } from "../entity.js";

//#region src/mysql-core/checks.ts
var CheckBuilder = class {
	static [entityKind] = "MySqlCheckBuilder";
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
	static [entityKind] = "MySqlCheck";
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