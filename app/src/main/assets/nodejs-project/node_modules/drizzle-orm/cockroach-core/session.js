import { CockroachDatabase } from "./db.js";
import { entityKind } from "../entity.js";
import { tracer } from "../tracing.js";
import { TransactionRollbackError } from "../errors.js";
import { sql } from "../sql/index.js";

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
	static [entityKind] = "CockroachPreparedQuery";
	/** @internal */
	joinsNotNullableMap;
};
var CockroachSession = class {
	static [entityKind] = "CockroachSession";
	constructor(dialect) {
		this.dialect = dialect;
	}
	/** @internal */
	execute(query, token) {
		return tracer.startActiveSpan("drizzle.operation", () => {
			return tracer.startActiveSpan("drizzle.prepareQuery", () => {
				return this.prepareQuery(this.dialect.sqlToQuery(query), void 0, void 0, void 0);
			}).setToken(token).execute(void 0, token);
		});
	}
	all(query) {
		return this.prepareQuery(this.dialect.sqlToQuery(query), void 0, void 0, void 0).all();
	}
};
var CockroachTransaction = class extends CockroachDatabase {
	static [entityKind] = "CockroachTransaction";
	constructor(dialect, session, schema, nestedIndex = 0) {
		super(dialect, session, schema);
		this.schema = schema;
		this.nestedIndex = nestedIndex;
	}
	rollback() {
		throw new TransactionRollbackError();
	}
	/** @internal */
	getTransactionConfigSQL(config) {
		const chunks = [];
		if (config.isolationLevel) chunks.push(`isolation level ${config.isolationLevel}`);
		if (config.accessMode) chunks.push(config.accessMode);
		if (typeof config.deferrable === "boolean") chunks.push(config.deferrable ? "deferrable" : "not deferrable");
		return sql.raw(chunks.join(" "));
	}
	setTransaction(config) {
		return this.session.execute(sql`set transaction ${this.getTransactionConfigSQL(config)}`);
	}
};

//#endregion
export { CockroachPreparedQuery, CockroachSession, CockroachTransaction };
//# sourceMappingURL=session.js.map