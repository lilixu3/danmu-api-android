Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");

//#region src/pg-core/policies.ts
var PgPolicy = class {
	static [__entity_ts.entityKind] = "PgPolicy";
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
exports.PgPolicy = PgPolicy;
exports.pgPolicy = pgPolicy;
//# sourceMappingURL=policies.cjs.map