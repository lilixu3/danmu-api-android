import { entityKind } from "../entity.js";
import { sql } from "../sql/sql.js";
import { NoopLogger } from "../logger.js";
import { NoopCache } from "../cache/core/index.js";
import { SQLiteAsyncPreparedQuery, SQLiteAsyncSession, SQLiteAsyncTransaction } from "../sqlite-core/async/session.js";

//#region src/op-sqlite/session.ts
var OPSQLiteSession = class extends SQLiteAsyncSession {
	static [entityKind] = "OPSQLiteSession";
	logger;
	cache;
	constructor(client, dialect, relations, options = {}) {
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
				if (mode === "arrays") return this.client.executeRawAsync(query.sql, params);
				return this.client.executeAsync(query.sql, params).then(({ rows }) => rows?._array || []);
			},
			get: (params) => {
				if (mode === "arrays") return this.client.executeRawAsync(query.sql, params).then((rows) => rows[0]);
				return this.client.executeAsync(query.sql, params).then(({ rows }) => rows?._array?.[0]);
			},
			run: (params) => {
				return this.client.executeAsync(query.sql, params);
			},
			values: (params) => {
				return this.client.executeRawAsync(query.sql, params);
			}
		}, query, mapper, mode, this.logger, this.cache, queryMetadata, cacheConfig);
	}
	transaction(transaction, config = {}) {
		const tx = new OPSQLiteTransaction("async", this.dialect, this, this.relations);
		this.run(sql.raw(`begin${config?.behavior ? " " + config.behavior : ""}`));
		try {
			const result = transaction(tx);
			this.run(sql`commit`);
			return result;
		} catch (err) {
			this.run(sql`rollback`);
			throw err;
		}
	}
};
var OPSQLiteTransaction = class OPSQLiteTransaction extends SQLiteAsyncTransaction {
	static [entityKind] = "OPSQLiteTransaction";
	transaction(transaction) {
		const savepointName = `sp${this.nestedIndex}`;
		const tx = new OPSQLiteTransaction("async", this.dialect, this.session, this._.relations, this.nestedIndex + 1);
		this.session.run(sql.raw(`savepoint ${savepointName}`));
		try {
			const result = transaction(tx);
			this.session.run(sql.raw(`release savepoint ${savepointName}`));
			return result;
		} catch (err) {
			this.session.run(sql.raw(`rollback to savepoint ${savepointName}`));
			throw err;
		}
	}
};

//#endregion
export { OPSQLiteSession, OPSQLiteTransaction };
//# sourceMappingURL=session.js.map