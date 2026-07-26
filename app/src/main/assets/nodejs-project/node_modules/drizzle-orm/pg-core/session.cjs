Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");

//#region src/pg-core/session.ts
var PgBasePreparedQuery = class {
	static [__entity_ts.entityKind] = "PgBasePreparedQuery";
	constructor(query) {
		this.query = query;
	}
	getQuery() {
		return this.query;
	}
};
var PgSession = class {
	static [__entity_ts.entityKind] = "PgSession";
	constructor(dialect) {
		this.dialect = dialect;
	}
};

//#endregion
exports.PgBasePreparedQuery = PgBasePreparedQuery;
exports.PgSession = PgSession;
//# sourceMappingURL=session.cjs.map