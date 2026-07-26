import { entityKind } from "../entity.js";
import { NoopLogger } from "../logger.js";
import { PgAsyncPreparedQuery, PgAsyncSession } from "../pg-core/async/session.js";
import { NoopCache } from "../cache/core/cache.js";

//#region src/pg-proxy/session.ts
var PgRemoteSession = class extends PgAsyncSession {
	static [entityKind] = "PgRemoteSession";
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
			if (mode === "arrays") return this.client(query.sql, params, "all").then((r) => r.rows);
			return this.client(query.sql, params, "execute").then((r) => r.rows);
		};
		return new PgAsyncPreparedQuery(executor, query, mapper, mode, this.logger, this.cache, queryMetadata, cacheConfig);
	}
	async transaction(_transaction, _config) {
		throw new Error("Transactions are not supported by the Postgres Proxy driver");
	}
};

//#endregion
export { PgRemoteSession };
//# sourceMappingURL=session.js.map