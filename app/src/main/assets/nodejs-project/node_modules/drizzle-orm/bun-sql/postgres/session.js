import { entityKind } from "../../entity.js";
import { NoopLogger } from "../../logger.js";
import { NoopCache } from "../../cache/core/index.js";
import { PgAsyncPreparedQuery, PgAsyncSession, PgAsyncTransaction } from "../../pg-core/async/session.js";

//#region src/bun-sql/postgres/session.ts
var BunSQLSession = class BunSQLSession extends PgAsyncSession {
	static [entityKind] = "BunSQLSession";
	logger;
	cache;
	constructor(client, dialect, relations, options = {}) {
		super(dialect);
		this.client = client;
		this.relations = relations;
		this.options = options;
		this.logger = options.logger ?? new NoopLogger();
		this.cache = options.cache ?? new NoopCache();
	}
	prepareQuery(query, mode, _name, mapper, queryMetadata, cacheConfig) {
		const tagged = query._sql ? query._sql : null;
		const client = this.client;
		return new PgAsyncPreparedQuery(tagged ? mode === "arrays" ? (params) => params ? client(tagged, ...params).values() : client(tagged).values() : (params) => params ? client(tagged, ...params) : client(tagged) : mode === "arrays" ? (params) => client.unsafe(query.sql, params).values() : (params) => client.unsafe(query.sql, params), query, mapper, mode, this.logger, this.cache, queryMetadata, cacheConfig);
	}
	transaction(transaction, config) {
		return this.client.begin(async (client) => {
			const session = new BunSQLSession(client, this.dialect, this.relations, this.options);
			const tx = new BunSQLTransaction(this.dialect, session, this.relations);
			if (config) await tx.setTransaction(config);
			return transaction(tx);
		});
	}
};
var BunSQLTransaction = class BunSQLTransaction extends PgAsyncTransaction {
	static [entityKind] = "BunSQLTransaction";
	constructor(dialect, session, relations, nestedIndex = 0) {
		super(dialect, session, relations, nestedIndex, false);
		this.session = session;
	}
	transaction(transaction) {
		return this.session.client.savepoint((client) => {
			const session = new BunSQLSession(client, this.dialect, this._.relations, this.session.options);
			return transaction(new BunSQLTransaction(this.dialect, session, this._.relations));
		});
	}
};

//#endregion
export { BunSQLSession, BunSQLTransaction };
//# sourceMappingURL=session.js.map