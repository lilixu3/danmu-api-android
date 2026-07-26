import { CockroachColumn, CockroachColumnWithArrayBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { sql } from "../../sql/sql.js";

//#region src/cockroach-core/columns/uuid.ts
var CockroachUUIDBuilder = class extends CockroachColumnWithArrayBuilder {
	static [entityKind] = "CockroachUUIDBuilder";
	constructor(name) {
		super(name, "string uuid", "CockroachUUID");
	}
	/**
	* Adds `default gen_random_uuid()` to the column definition.
	*/
	defaultRandom() {
		return this.default(sql`gen_random_uuid()`);
	}
	/** @internal */
	build(table) {
		return new CockroachUUID(table, this.config);
	}
};
var CockroachUUID = class extends CockroachColumn {
	static [entityKind] = "CockroachUUID";
	getSQLType() {
		return "uuid";
	}
};
function uuid(name) {
	return new CockroachUUIDBuilder(name ?? "");
}

//#endregion
export { CockroachUUID, CockroachUUIDBuilder, uuid };
//# sourceMappingURL=uuid.js.map