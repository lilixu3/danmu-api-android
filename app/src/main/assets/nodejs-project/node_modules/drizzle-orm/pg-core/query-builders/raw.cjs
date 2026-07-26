Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/pg-core/query-builders/raw.ts
var PgRaw = class {
	static [__entity_ts.entityKind] = "PgRaw";
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
exports.PgRaw = PgRaw;
//# sourceMappingURL=raw.cjs.map