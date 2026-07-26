import { entityKind } from "../../entity.js";
import { NoopLogger } from "../../logger.js";
import { NoopCache } from "../../cache/core/index.js";
import { SQLiteAsyncPreparedQuery, SQLiteAsyncSession, SQLiteAsyncTransaction } from "../../sqlite-core/async/session.js";

//#region src/bun-sql/sqlite/session.ts
var BunSQLiteSession = class BunSQLiteSession extends SQLiteAsyncSession {
	static [entityKind] = "BunSQLiteSession";
	logger;
	cache;
	constructor(client, dialect, relations, options) {
		super(dialect, "async");
		this.client = client;
		this.relations = relations;
		this.options = options;
		this.logger = options.logger ?? new NoopLogger();
		this.cache = options.cache ?? new NoopCache();
	}
	prepareQuery(query, mode, _prepare, executeMethod, mapper, queryMetadata, cacheConfig) {
		return new SQLiteAsyncPreparedQuery("async", executeMethod, {
			all: (params) => {
				const q = this.client.unsafe(query.sql, params);
				if (mode === "arrays") return q.values();
				return q;
			},
			get: (params) => {
				const q = this.client.unsafe(query.sql, params);
				if (mode === "arrays") return q.values().then((rows) => rows[0]);
				return q.then((rows) => rows[0]);
			},
			run: (params) => {
				return this.client.unsafe(query.sql, params);
			},
			values: (params) => {
				return this.client.unsafe(query.sql, params).values();
			}
		}, query, mapper, mode, this.logger, this.cache, queryMetadata, cacheConfig);
	}
	async transaction(transaction, config) {
		return this.client.begin(config?.behavior ?? "", async (client) => {
			const session = new BunSQLiteSession(client, this.dialect, this.relations, this.options);
			return await transaction(new BunSQLiteTransaction("async", this.dialect, session, this.relations));
		});
	}
};
var BunSQLiteTransaction = class BunSQLiteTransaction extends SQLiteAsyncTransaction {
	static [entityKind] = "BunSQLiteTransaction";
	async transaction(transaction) {
		return this.session.client.savepoint(async (client) => {
			const session = new BunSQLiteSession(client, this.session.dialect, this._.relations, this.session.options);
			return await transaction(new BunSQLiteTransaction("async", this.dialect, session, this._.relations, this.nestedIndex + 1));
		});
	}
};

//#endregion
export { BunSQLiteSession, BunSQLiteTransaction };
//# sourceMappingURL=session.js.map