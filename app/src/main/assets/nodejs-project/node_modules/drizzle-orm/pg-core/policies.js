import { entityKind } from "../entity.js";

//#region src/pg-core/policies.ts
var PgPolicy = class {
	static [entityKind] = "PgPolicy";
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
function pgPolicy(name, config) {
	return new PgPolicy(name, config);
}

//#endregion
export { PgPolicy, pgPolicy };
//# sourceMappingURL=policies.js.map