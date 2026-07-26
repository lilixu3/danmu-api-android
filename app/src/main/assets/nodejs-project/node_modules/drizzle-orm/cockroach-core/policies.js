import { entityKind } from "../entity.js";

//#region src/cockroach-core/policies.ts
var CockroachPolicy = class {
	static [entityKind] = "CockroachPolicy";
	as;
	for;
	to;
	using;
	withCheck;
	/** @internal */
	_linkedTable;
	constructor(name, config) {
		this.name = name;
		if (config) {
			this.as = config.as;
			this.for = config.for;
			this.to = config.to;
			this.using = config.using;
			this.withCheck = config.withCheck;
		}
	}
	link(table) {
		this._linkedTable = table;
		return this;
	}
};
function cockroachPolicy(name, config) {
	return new CockroachPolicy(name, config);
}

//#endregion
export { CockroachPolicy, cockroachPolicy };
//# sourceMappingURL=policies.js.map