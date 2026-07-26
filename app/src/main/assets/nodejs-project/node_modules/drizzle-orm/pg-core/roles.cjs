Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");

//#region src/pg-core/roles.ts
var PgRole = class {
	static [__entity_ts.entityKind] = "PgRole";
	/** @internal */
	_existing;
	/** @internal */
	createDb;
	/** @internal */
	createRole;
	/** @internal */
	inherit;
	constructor(name, config) {
		this.name = name;
		if (config) {
			this.createDb = config.createDb;
			this.createRole = config.createRole;
			this.inherit = config.inherit;
		}
	}
	existing() {
		this._existing = true;
		return this;
	}
};
function pgRole(name, config) {
	return new PgRole(name, config);
}

//#endregion
exports.PgRole = PgRole;
exports.pgRole = pgRole;
//# sourceMappingURL=roles.cjs.map