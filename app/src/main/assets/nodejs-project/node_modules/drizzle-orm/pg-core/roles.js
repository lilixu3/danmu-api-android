import { entityKind } from "../entity.js";

//#region src/pg-core/roles.ts
var PgRole = class {
	static [entityKind] = "PgRole";
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
export { PgRole, pgRole };
//# sourceMappingURL=roles.js.map