Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_pg_core_primary_keys = require('./primary-keys.cjs');
const require_pg_core_checks = require('./checks.cjs');
const require_pg_core_foreign_keys = require('./foreign-keys.cjs');
const require_pg_core_indexes = require('./indexes.cjs');
const require_pg_core_policies = require('./policies.cjs');
const require_pg_core_unique_constraint = require('./unique-constraint.cjs');
const require_pg_core_view_common = require('./view-common.cjs');
let __entity_ts = require("../entity.cjs");
let __subquery_ts = require("../subquery.cjs");
let __view_common_ts = require("../view-common.cjs");
let __table_ts = require("../table.cjs");
let __sql_sql_ts = require("../sql/sql.cjs");
let __pg_core_table_ts = require("./table.cjs");

//#region src/pg-core/utils.ts
function getTableConfig(table) {
	const columns = Object.values(table[__table_ts.Table.Symbol.Columns]);
	const indexes = [];
	const checks = [];
	const primaryKeys = [];
	const foreignKeys = Object.values(table[__pg_core_table_ts.PgTable.Symbol.InlineForeignKeys]);
	const uniqueConstraints = [];
	const name = table[__table_ts.Table.Symbol.Name];
	const schema = table[__table_ts.Table.Symbol.Schema];
	const policies = [];
	const enableRLS = table[__pg_core_table_ts.PgTable.Symbol.EnableRLS];
	const extraConfigBuilder = table[__pg_core_table_ts.PgTable.Symbol.ExtraConfigBuilder];
	if (extraConfigBuilder !== void 0) {
		const extraConfig = extraConfigBuilder(table[__table_ts.Table.Symbol.ExtraConfigColumns]);
		const extraValues = Array.isArray(extraConfig) ? extraConfig.flat(1) : Object.values(extraConfig);
		for (const builder of extraValues) if ((0, __entity_ts.is)(builder, require_pg_core_indexes.IndexBuilder)) indexes.push(builder.build(table));
		else if ((0, __entity_ts.is)(builder, require_pg_core_checks.CheckBuilder)) checks.push(builder.build(table));
		else if ((0, __entity_ts.is)(builder, require_pg_core_unique_constraint.UniqueConstraintBuilder)) uniqueConstraints.push(builder.build(table));
		else if ((0, __entity_ts.is)(builder, require_pg_core_primary_keys.PrimaryKeyBuilder)) primaryKeys.push(builder.build(table));
		else if ((0, __entity_ts.is)(builder, require_pg_core_foreign_keys.ForeignKeyBuilder)) foreignKeys.push(builder.build(table));
		else if ((0, __entity_ts.is)(builder, require_pg_core_policies.PgPolicy)) policies.push(builder);
	}
	return {
		columns,
		indexes,
		foreignKeys,
		checks,
		primaryKeys,
		uniqueConstraints,
		name,
		schema,
		policies,
		enableRLS
	};
}
function extractUsedTable(table) {
	if ((0, __entity_ts.is)(table, __pg_core_table_ts.PgTable)) return [table[__table_ts.TableSchema] ? `${table[__table_ts.TableSchema]}.${table[__table_ts.Table.Symbol.BaseName]}` : table[__table_ts.Table.Symbol.BaseName]];
	if ((0, __entity_ts.is)(table, __subquery_ts.Subquery)) return table._.usedTables ?? [];
	if ((0, __entity_ts.is)(table, __sql_sql_ts.SQL)) return table.usedTables ?? [];
	return [];
}
function getViewConfig(view) {
	return {
		...view[__view_common_ts.ViewBaseConfig],
		...view[require_pg_core_view_common.PgViewConfig]
	};
}
function getMaterializedViewConfig(view) {
	return {
		...view[__view_common_ts.ViewBaseConfig],
		...view[require_pg_core_view_common.PgMaterializedViewConfig]
	};
}

//#endregion
exports.extractUsedTable = extractUsedTable;
exports.getMaterializedViewConfig = getMaterializedViewConfig;
exports.getTableConfig = getTableConfig;
exports.getViewConfig = getViewConfig;
//# sourceMappingURL=utils.cjs.map