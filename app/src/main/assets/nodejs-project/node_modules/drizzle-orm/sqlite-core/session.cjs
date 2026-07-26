Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");

//#region src/sqlite-core/session.ts
var SQLitePreparedQuery = class {
	static [__entity_ts.entityKind] = "SQLiteBasePreparedQuery";
	/** @internal */
	mapper;
	/** @internal */
	executeMethod;
	constructor(executeMethod, query, mapper, mode) {
		this.query = query;
		this.mode = mode;
		this.mapper = mapper;
		this.executeMethod = executeMethod;
	}
	getQuery() {
		return this.query;
	}
};
var SQLiteSession = class {
	static [__entity_ts.entityKind] = "SQLiteSession";
	constructor(dialect) {
		this.dialect = dialect;
	}
};

//#endregion
exports.SQLitePreparedQuery = SQLitePreparedQuery;
exports.SQLiteSession = SQLiteSession;
//# sourceMappingURL=session.cjs.map