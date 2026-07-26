import { entityKind } from "../entity.js";
import { sql } from "../sql/sql.js";
import { NoopLogger } from "../logger.js";
import { PgAsyncPreparedQuery, PgAsyncSession, PgAsyncTransaction } from "../pg-core/async/session.js";
import { NoopCache } from "../cache/core/cache.js";
import { types } from "@electric-sql/pglite";

//#region src/pglite/session.ts
const parsers = {
	[types.TIMESTAMP]: (value) => value,
	[types.TIMESTAMPTZ]: (value) => value,
	[types.INTERVAL]: (value) => value,
	[types.DATE]: (value) => value,
	[1231]: (value) => value,
	[1115]: (value) => value,
	[1185]: (value) => value,
	[1187]: (value) => value,
	[1182]: (value) => value
};
var PgliteSession = class PgliteSession extends PgAsyncSession {
	static [entityKind] = "PgliteSession";
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
		const executor = async (params) => {
			return this.client.query(query.sql, params, {
				rowMode: mode === "arrays" ? "array" : "object",
				parsers
			}).then((r) => mode === "raw" ? r : r.rows);
		};
		return new PgAsyncPreparedQuery(executor, query, mapper, mode, this.logger, this.cache, queryMetadata, cacheConfig);
	}
	async transaction(transaction, config) {
		return this.client.transaction(async (client) => {
			const session = new PgliteSession(client, this.dialect, this.relations, this.options);
			const tx = new PgliteTransaction(this.dialect, session, this.relations, void 0, false);
			if (config) await tx.setTransaction(config);
			return transaction(tx);
		});
	}
};
var PgliteTransaction = class PgliteTransaction extends PgAsyncTransaction {
	static [entityKind] = "PgliteTransaction";
	async transaction(transaction) {
		const savepointName = `sp${this.nestedIndex + 1}`;
		const tx = new PgliteTransaction(this.dialect, this.session, this._.relations, this.nestedIndex + 1, false);
		await tx.execute(sql.raw(`savepoint ${savepointName}`));
		try {
			const result = await transaction(tx);
			await tx.execute(sql.raw(`release savepoint ${savepointName}`));
			return result;
		} catch (err) {
			await tx.execute(sql.raw(`rollback to savepoint ${savepointName}`));
			throw err;
		}
	}
};

//#endregion
export { PgliteSession, PgliteTransaction };
//# sourceMappingURL=session.js.map