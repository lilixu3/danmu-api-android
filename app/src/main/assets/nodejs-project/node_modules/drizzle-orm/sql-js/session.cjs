Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");
let __sql_sql_ts = require("../sql/sql.cjs");
let __logger_ts = require("../logger.cjs");
let __sqlite_core_async_session_ts = require("../sqlite-core/async/session.cjs");

//#region src/sql-js/session.ts
var SQLJsSession = class extends __sqlite_core_async_session_ts.SQLiteAsyncSession {
	static [__entity_ts.entityKind] = "SQLJsSession";
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
				const stmt = this.client.prepare(query.sql);
				stmt.bind(params);
				const rows = [];
				if (mode === "arrays") while (stmt.step()) rows.push(stmt.get());
				else while (stmt.step()) rows.push(stmt.getAsObject());
				stmt.free();
				return rows;
			},
			get: (params) => {
				const stmt = this.client.prepare(query.sql);
				stmt.bind(params);
				let row;
				if (stmt.step()) row = mode === "arrays" ? stmt.get() : stmt.getAsObject();
				stmt.free();
				return row;
			},
			run: (params) => {
				const stmt = this.client.prepare(query.sql);
				const res = stmt.run(params);
				stmt.free();
				return res;
			},
			values: (params) => {
				const stmt = this.client.prepare(query.sql);
				stmt.bind(params);
				const rows = [];
				while (stmt.step()) rows.push(stmt.get());
				stmt.free();
				return rows;
			}
		}, query, mapper, mode, this.logger, void 0, queryMetadata, void 0);
	}
	transaction(transaction, config = {}) {
		const tx = new SQLJsTransaction("sync", this.dialect, this, this.relations);
		this.run(__sql_sql_ts.sql.raw(`begin${config.behavior ? ` ${config.behavior}` : ""}`));
		try {
			const result = transaction(tx);
			this.run(__sql_sql_ts.sql`commit`);
			return result;
		} catch (err) {
			this.run(__sql_sql_ts.sql`rollback`);
			throw err;
		}
	}
};
var SQLJsTransaction = class SQLJsTransaction extends __sqlite_core_async_session_ts.SQLiteAsyncTransaction {
	static [__entity_ts.entityKind] = "SQLJsTransaction";
	transaction(transaction) {
		const savepointName = `sp${this.nestedIndex + 1}`;
		const tx = new SQLJsTransaction("sync", this.dialect, this.session, this._.relations, this.nestedIndex + 1);
		tx.run(__sql_sql_ts.sql.raw(`savepoint ${savepointName}`));
		try {
			const result = transaction(tx);
			tx.run(__sql_sql_ts.sql.raw(`release savepoint ${savepointName}`));
			return result;
		} catch (err) {
			tx.run(__sql_sql_ts.sql.raw(`rollback to savepoint ${savepointName}`));
			throw err;
		}
	}
};

//#endregion
exports.SQLJsSession = SQLJsSession;
exports.SQLJsTransaction = SQLJsTransaction;
//# sourceMappingURL=session.cjs.map