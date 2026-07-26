import { entityKind } from "../entity.js";

//#region src/cockroach-core/checks.ts
var CheckBuilder = class {
	static [entityKind] = "CockroachCheckBuilder";
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
	static [entityKind] = "CockroachCheck";
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