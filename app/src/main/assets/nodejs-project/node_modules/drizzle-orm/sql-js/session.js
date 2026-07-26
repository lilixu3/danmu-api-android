import { entityKind } from "../entity.js";
import { sql } from "../sql/sql.js";
import { NoopLogger } from "../logger.js";
import { SQLiteAsyncPreparedQuery, SQLiteAsyncSession, SQLiteAsyncTransaction } from "../sqlite-core/async/session.js";

//#region src/sql-js/session.ts
var SQLJsSession = class extends SQLiteAsyncSession {
	static [entityKind] = "SQLJsSession";
	logger;
	constructor(client, dialect, relations, options = {}) {
		super(dialect, "sync");
		this.client = client;
		this.relations = relations;
		this.options = options;
		this.logger = options.logger ?? new NoopLogger();
	}
	prepareQuery(query, mode, _prepare, executeMethod, mapper, queryMetadata) {
		return new SQLiteAsyncPreparedQuery("sync", executeMethod, {
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
		this.run(sql.raw(`begin${config.behavior ? ` ${config.behavior}` : ""}`));
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
var SQLJsTransaction = class SQLJsTransaction extends SQLiteAsyncTransaction {
	static [entityKind] = "SQLJsTransaction";
	transaction(transaction) {
		const savepointName = `sp${this.nestedIndex + 1}`;
		const tx = new SQLJsTransaction("sync", this.dialect, this.session, this._.relations, this.nestedIndex + 1);
		tx.run(sql.raw(`savepoint ${savepointName}`));
		try {
			const result = transaction(tx);
			tx.run(sql.raw(`release savepoint ${savepointName}`));
			return result;
		} catch (err) {
			tx.run(sql.raw(`rollback to savepoint ${savepointName}`));
			throw err;
		}
	}
};

//#endregion
export { SQLJsSession, SQLJsTransaction };
//# sourceMappingURL=session.js.map