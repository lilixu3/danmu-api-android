Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../../entity.cjs");
let __logger_ts = require("../../logger.cjs");
let __cache_core_index_ts = require("../../cache/core/index.cjs");
let __pg_core_async_session_ts = require("../../pg-core/async/session.cjs");

//#region src/bun-sql/postgres/session.ts
var BunSQLSession = class BunSQLSession extends __pg_core_async_session_ts.PgAsyncSession {
	static [__entity_ts.entityKind] = "BunSQLSession";
	logger;
	cache;
	constructor(client, dialect, relations, options = {}) {
		super(dialect);
		this.client = client;
		this.relations = relations;
		this.options = options;
		this.logger = options.logger ?? new __logger_ts.NoopLogger();
		this.cache = options.cache ?? new __cache_core_index_ts.NoopCache();
	}
	prepareQuery(query, mode, _name, mapper, queryMetadata, cacheConfig) {
		const tagged = query._sql ? query._sql : null;
		const client = this.client;
		return new __pg_core_async_session_ts.PgAsyncPreparedQuery(tagged ? mode === "arrays" ? (params) => params ? client(tagged, ...params).values() : client(tagged).values() : (params) => params ? client(tagged, ...params) : client(tagged) : mode === "arrays" ? (params) => client.unsafe(query.sql, params).values() : (params) => client.unsafe(query.sql, params), query, mapper, mode, this.logger, this.cache, queryMetadata, cacheConfig);
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
var BunSQLTransaction = class BunSQLTransaction extends __pg_core_async_session_ts.PgAsyncTransaction {
	static [__entity_ts.entityKind] = "BunSQLTransaction";
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
exports.BunSQLSession = BunSQLSession;
exports.BunSQLTransaction = BunSQLTransaction;
//# sourceMappingURL=session.cjs.map