import { entityKind } from "../entity.js";
import { NoopLogger } from "../logger.js";
import { NoopCache } from "../cache/core/index.js";
import { PgAsyncPreparedQuery, PgAsyncSession } from "../pg-core/async/session.js";

//#region src/xata-http/session.ts
var XataHttpSession = class extends PgAsyncSession {
	static [entityKind] = "XataHttpSession";
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
			if (mode === "raw") return this.client.sql({
				statement: query.sql,
				params
			});
			if (mode === "objects") return this.client.sql({
				statement: query.sql,
				params,
				responseType: "json"
			}).then(({ warning, records }) => {
				if (warning) console.warn(warning);
				return records;
			});
			return this.client.sql({
				statement: query.sql,
				params,
				responseType: "array"
			}).then(({ warning, rows }) => {
				if (warning) console.warn(warning);
				return rows;
			});
		};
		return new PgAsyncPreparedQuery(executor, query, mapper, mode, this.logger, this.cache, queryMetadata, cacheConfig);
	}
	async transaction(_transaction, _config = {}) {
		throw new Error("No transactions support in Xata Http driver");
	}
};

//#endregion
export { XataHttpSession };
//# sourceMappingURL=session.js.map