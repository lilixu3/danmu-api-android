Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/sqlite-core/query-builders/raw.ts
var SQLiteRaw = class {
	static [__entity_ts.entityKind] = "SQLiteRaw";
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
exports.SQLiteRaw = SQLiteRaw;
//# sourceMappingURL=raw.cjs.map