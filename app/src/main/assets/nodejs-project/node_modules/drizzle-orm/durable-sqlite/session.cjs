Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");
let __logger_ts = require("../logger.cjs");
let __sqlite_core_async_session_ts = require("../sqlite-core/async/session.cjs");

//#region src/durable-sqlite/session.ts
var SQLiteDOSession = class extends __sqlite_core_async_session_ts.SQLiteAsyncSession {
	static [__entity_ts.entityKind] = "SQLiteDOSession";
	logger;
	constructor(client, dialect, relations, options = {}) {
		super(dialect, "sync");
		this.client = client;
		this.relations = relations;
		this.options = options;
		this.logger = options.logger ?? new __logger_ts.NoopLogger();
	}
	prepareQuery(query, mode, _prepare, executeMethod, mapper, queryMetadata) {
		return new __sqlite_core_async_session_ts.SQLiteAsyncPreparedQuery("sync", executeMethod, {
			all: (params) => {
				const res = params.length > 0 ? this.client.sql.exec(query.sql, ...params) : this.client.sql.exec(query.sql);
				if (mode === "objects") return res.toArray();
				return res.raw().toArray();
			},
			get: (params) => {
				const res = params.length > 0 ? this.client.sql.exec(query.sql, ...params) : this.client.sql.exec(query.sql);
				if (mode === "objects") return res.one();
				return res.raw().next().value;
			},
			run: (params) => {
				return params.length > 0 ? this.client.sql.exec(query.sql, ...params) : this.client.sql.exec(query.sql);
			},
			values: (params) => {
				return (params.length > 0 ? this.client.sql.exec(query.sql, ...params) : this.client.sql.exec(query.sql)).raw().toArray();
			}
		}, query, mapper, mode, this.logger, void 0, queryMetadata, void 0);
	}
	transaction(transaction, _config) {
		const tx = new SQLiteDOTransaction("sync", this.dialect, this, this.relations, void 0, true);
		return this.client.transactionSync(() => transaction(tx));
	}
};
var SQLiteDOTransaction = class SQLiteDOTransaction extends __sqlite_core_async_session_ts.SQLiteAsyncTransaction {
	static [__entity_ts.entityKind] = "SQLiteDOTransaction";
	transaction(transaction) {
		const tx = new SQLiteDOTransaction("sync", this.dialect, this.session, this._.relations, this.nestedIndex + 1, true);
		return this.session.transaction(() => transaction(tx));
	}
};

//#endregion
exports.SQLiteDOSession = SQLiteDOSession;
exports.SQLiteDOTransaction = SQLiteDOTransaction;
//# sourceMappingURL=session.cjs.map