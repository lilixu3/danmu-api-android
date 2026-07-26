Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_db = require('./db.cjs');
let __entity_ts = require("../entity.cjs");
let __tracing_ts = require("../tracing.cjs");
let __errors_ts = require("../errors.cjs");
let __sql_index_ts = require("../sql/index.cjs");

//#region src/cockroach-core/session.ts
var CockroachPreparedQuery = class {
	constructor(query) {
		this.query = query;
	}
	authToken;
	getQuery() {
		return this.query;
	}
	mapResult(response, _isFromBatch) {
		return response;
	}
	/** @internal */
	setToken(token) {
		this.authToken = token;
		return this;
	}
	static [__entity_ts.entityKind] = "CockroachPreparedQuery";
	/** @internal */
	joinsNotNullableMap;
};
var CockroachSession = class {
	static [__entity_ts.entityKind] = "CockroachSession";
	constructor(dialect) {
		this.dialect = dialect;
	}
	/** @internal */
	execute(query, token) {
		return __tracing_ts.tracer.startActiveSpan("drizzle.operation", () => {
			return __tracing_ts.tracer.startActiveSpan("drizzle.prepareQuery", () => {
				return this.prepareQuery(this.dialect.sqlToQuery(query), void 0, void 0, void 0);
			}).setToken(token).execute(void 0, token);
		});
	}
	all(query) {
		return this.prepareQuery(this.dialect.sqlToQuery(query), void 0, void 0, void 0).all();
	}
};
var CockroachTransaction = class extends require_cockroach_core_db.CockroachDatabase {
	static [__entity_ts.entityKind] = "CockroachTransaction";
	constructor(dialect, session, schema, nestedIndex = 0) {
		super(dialect, session, schema);
		this.schema = schema;
		this.nestedIndex = nestedIndex;
	}
	rollback() {
		throw new __errors_ts.TransactionRollbackError();
	}
	/** @internal */
	getTransactionConfigSQL(config) {
		const chunks = [];
		if (config.isolationLevel) chunks.push(`isolation level ${config.isolationLevel}`);
		if (config.accessMode) chunks.push(config.accessMode);
		if (typeof config.deferrable === "boolean") chunks.push(config.deferrable ? "deferrable" : "not deferrable");
		return __sql_index_ts.sql.raw(chunks.join(" "));
	}
	setTransaction(config) {
		return this.session.execute(__sql_index_ts.sql`set transaction ${this.getTransactionConfigSQL(config)}`);
	}
};

//#endregion
exports.CockroachPreparedQuery = CockroachPreparedQuery;
exports.CockroachSession = CockroachSession;
exports.CockroachTransaction = CockroachTransaction;
//# sourceMappingURL=session.cjs.map