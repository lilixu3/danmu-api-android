import { entityKind } from "../entity.js";

//#region src/cockroach-core/roles.ts
var CockroachRole = class {
	static [entityKind] = "CockroachRole";
	/** @internal */
	_existing;
	/** @internal */
	createDb;
	/** @internal */
	createRole;
	constructor(name, config) {
		this.name = name;
		if (config) {
			this.createDb = config.createDb;
			this.createRole = config.createRole;
		}
	}
	existing() {
		this._existing = true;
		return this;
	}
};
function cockroachRole(name, config) {
	return new CockroachRole(name, config);
}

//#endregion
export { CockroachRole, cockroachRole };
//# sourceMappingURL=roles.js.map