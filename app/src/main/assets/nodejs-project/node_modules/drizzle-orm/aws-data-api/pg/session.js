import { getValueFromDataApi, toValueParam } from "../common/index.js";
import { entityKind } from "../../entity.js";
import { sql } from "../../sql/sql.js";
import { NoopLogger } from "../../logger.js";
import { PgAsyncPreparedQuery, PgAsyncSession, PgAsyncTransaction } from "../../pg-core/async/session.js";
import { NoopCache } from "../../cache/core/cache.js";
import { BeginTransactionCommand, CommitTransactionCommand, ExecuteStatementCommand, RollbackTransactionCommand } from "@aws-sdk/client-rds-data";

//#region src/aws-data-api/pg/session.ts
var AwsDataApiSession = class AwsDataApiSession extends PgAsyncSession {
	static [entityKind] = "AwsDataApiSession";
	/** @internal */
	rawQuery;
	cache;
	logger;
	constructor(client, dialect, relations, options, transactionId) {
		super(dialect);
		this.client = client;
		this.relations = relations;
		this.options = options;
		this.transactionId = transactionId;
		this.rawQuery = {
			secretArn: options.secretArn,
			resourceArn: options.resourceArn,
			database: options.database
		};
		this.cache = options.cache ?? new NoopCache();
		this.logger = options.logger ?? new NoopLogger();
	}
	prepareQuery(query, mode, _name, mapper, queryMetadata, cacheConfig) {
		const executor = async (params) => {
			const command = new ExecuteStatementCommand({
				sql: query.sql,
				parameters: params ? params.map((param, index) => ({
					name: `${index + 1}`,
					value: toValueParam(param)
				})) : [],
				secretArn: this.options.secretArn,
				resourceArn: this.options.resourceArn,
				database: this.options.database,
				transactionId: this.transactionId,
				includeResultMetadata: mode === "objects" || mode === "raw"
			});
			const result = await this.client.send(command);
			const rows = result.records ?? [];
			if (result.records) for (let i = 0; i < result.records.length; ++i) {
				const row = rows[i];
				for (let j = 0; j < row.length; ++j) row[j] = getValueFromDataApi(row[j]);
			}
			if (mode === "arrays") return rows;
			const { columnMetadata } = result;
			if (!columnMetadata) return Object.assign(result, { rows });
			const mappedRows = rows.map((sourceRow) => {
				const row = {};
				for (const [index, value] of sourceRow.entries()) {
					const metadata = columnMetadata[index];
					if (!metadata) throw new Error(`Unexpected state: no column metadata found for index ${index}. Please report this issue on GitHub: https://github.com/drizzle-team/drizzle-orm/issues/new/choose`);
					if (!metadata.name) throw new Error(`Unexpected state: no column name for index ${index} found in the column metadata. Please report this issue on GitHub: https://github.com/drizzle-team/drizzle-orm/issues/new/choose`);
					row[metadata.name] = value;
				}
				return row;
			});
			if (mode === "objects") return mappedRows;
			return Object.assign(result, { rows: mappedRows });
		};
		return new PgAsyncPreparedQuery(executor, query, mapper, mode, this.logger, this.cache, queryMetadata, cacheConfig);
	}
	async transaction(transaction, config) {
		const { transactionId } = await this.client.send(new BeginTransactionCommand(this.rawQuery));
		const session = new AwsDataApiSession(this.client, this.dialect, this.relations, this.options, transactionId);
		const tx = new AwsDataApiTransaction(this.dialect, session, this.relations, void 0, true);
		if (config) await tx.setTransaction(config);
		try {
			const result = await transaction(tx);
			await this.client.send(new CommitTransactionCommand({
				...this.rawQuery,
				transactionId
			}));
			return result;
		} catch (e) {
			await this.client.send(new RollbackTransactionCommand({
				...this.rawQuery,
				transactionId
			}));
			throw e;
		}
	}
};
var AwsDataApiTransaction = class AwsDataApiTransaction extends PgAsyncTransaction {
	static [entityKind] = "AwsDataApiTransaction";
	async transaction(transaction) {
		const savepointName = `sp${this.nestedIndex + 1}`;
		const tx = new AwsDataApiTransaction(this.dialect, this.session, this._.relations, this.nestedIndex + 1, true);
		await this.session.execute(sql.raw(`savepoint ${savepointName}`));
		try {
			const result = await transaction(tx);
			await this.session.execute(sql.raw(`release savepoint ${savepointName}`));
			return result;
		} catch (e) {
			await this.session.execute(sql.raw(`rollback to savepoint ${savepointName}`));
			throw e;
		}
	}
};

//#endregion
export { AwsDataApiSession, AwsDataApiTransaction };
//# sourceMappingURL=session.js.map