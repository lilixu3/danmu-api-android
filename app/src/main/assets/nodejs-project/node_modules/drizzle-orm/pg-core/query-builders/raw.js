import { entityKind } from "../../entity.js";

//#region src/pg-core/query-builders/raw.ts
var PgRaw = class {
	static [entityKind] = "PgRaw";
	constructor(prepared, sql, query) {
		this.prepared = prepared;
		this.sql = sql;
		this.query = query;
	}
	getSQL() {
		return this.sql;
	}
	getQuery() {
		return this.query;
	}
	_prepare() {
		return this.prepared;
	}
};

//#endregion
export { PgRaw };
//# sourceMappingURL=raw.js.map