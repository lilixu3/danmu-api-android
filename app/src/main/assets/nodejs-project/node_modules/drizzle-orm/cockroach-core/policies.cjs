Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");

//#region src/cockroach-core/policies.ts
var CockroachPolicy = class {
	static [__entity_ts.entityKind] = "CockroachPolicy";
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
exports.CockroachPolicy = CockroachPolicy;
exports.cockroachPolicy = cockroachPolicy;
//# sourceMappingURL=policies.cjs.map