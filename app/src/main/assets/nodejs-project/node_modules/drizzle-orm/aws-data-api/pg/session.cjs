Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_aws_data_api_common_index = require('../common/index.cjs');
let __entity_ts = require("../../entity.cjs");
let __sql_sql_ts = require("../../sql/sql.cjs");
let __logger_ts = require("../../logger.cjs");
let __pg_core_async_session_ts = require("../../pg-core/async/session.cjs");
let __cache_core_cache_ts = require("../../cache/core/cache.cjs");
let _aws_sdk_client_rds_data = require("@aws-sdk/client-rds-data");

//#region src/aws-data-api/pg/session.ts
var AwsDataApiSession = class AwsDataApiSession extends __pg_core_async_session_ts.PgAsyncSession {
	static [__entity_ts.entityKind] = "AwsDataApiSession";
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
		this.cache = options.cache ?? new __cache_core_cache_ts.NoopCache();
		this.logger = options.logger ?? new __logger_ts.NoopLogger();
	}
	prepareQuery(query, mode, _name, mapper, queryMetadata, cacheConfig) {
		const executor = async (params) => {
			const command = new _aws_sdk_client_rds_data.ExecuteStatementCommand({
				sql: query.sql,
				parameters: params ? params.map((param, index) => ({
					name: `${index + 1}`,
					value: require_aws_data_api_common_index.toValueParam(param)
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
				for (let j = 0; j < row.length; ++j) row[j] = require_aws_data_api_common_index.getValueFromDataApi(row[j]);
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
		return new __pg_core_async_session_ts.PgAsyncPreparedQuery(executor, query, mapper, mode, this.logger, this.cache, queryMetadata, cacheConfig);
	}
	async transaction(transaction, config) {
		const { transactionId } = await this.client.send(new _aws_sdk_client_rds_data.BeginTransactionCommand(this.rawQuery));
		const session = new AwsDataApiSession(this.client, this.dialect, this.relations, this.options, transactionId);
		const tx = new AwsDataApiTransaction(this.dialect, session, this.relations, void 0, true);
		if (config) await tx.setTransaction(config);
		try {
			const result = await transaction(tx);
			await this.client.send(new _aws_sdk_client_rds_data.CommitTransactionCommand({
				...this.rawQuery,
				transactionId
			}));
			return result;
		} catch (e) {
			await this.client.send(new _aws_sdk_client_rds_data.RollbackTransactionCommand({
				...this.rawQuery,
				transactionId
			}));
			throw e;
		}
	}
};
var AwsDataApiTransaction = class AwsDataApiTransaction extends __pg_core_async_session_ts.PgAsyncTransaction {
	static [__entity_ts.entityKind] = "AwsDataApiTransaction";
	async transaction(transaction) {
		const savepointName = `sp${this.nestedIndex + 1}`;
		const tx = new AwsDataApiTransaction(this.dialect, this.session, this._.relations, this.nestedIndex + 1, true);
		await this.session.execute(__sql_sql_ts.sql.raw(`savepoint ${savepointName}`));
		try {
			const result = await transaction(tx);
			await this.session.execute(__sql_sql_ts.sql.raw(`release savepoint ${savepointName}`));
			return result;
		} catch (e) {
			await this.session.execute(__sql_sql_ts.sql.raw(`rollback to savepoint ${savepointName}`));
			throw e;
		}
	}
};

//#endregion
exports.AwsDataApiSession = AwsDataApiSession;
exports.AwsDataApiTransaction = AwsDataApiTransaction;
//# sourceMappingURL=session.cjs.map