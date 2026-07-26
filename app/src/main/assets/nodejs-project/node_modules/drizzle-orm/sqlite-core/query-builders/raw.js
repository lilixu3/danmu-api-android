import { entityKind } from "../../entity.js";

//#region src/sqlite-core/query-builders/raw.ts
var SQLiteRaw = class {
	static [entityKind] = "SQLiteRaw";
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
export { SQLiteRaw };
//# sourceMappingURL=raw.js.map