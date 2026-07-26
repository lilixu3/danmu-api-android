import { PrimaryKeyBuilder } from "./primary-keys.js";
import { CheckBuilder } from "./checks.js";
import { ForeignKeyBuilder } from "./foreign-keys.js";
import { IndexBuilder } from "./indexes.js";
import { PgPolicy } from "./policies.js";
import { UniqueConstraintBuilder } from "./unique-constraint.js";
import { PgMaterializedViewConfig, PgViewConfig } from "./view-common.js";
import { is } from "../entity.js";
import { Subquery } from "../subquery.js";
import { ViewBaseConfig } from "../view-common.js";
import { Table, TableSchema } from "../table.js";
import { SQL } from "../sql/sql.js";
import { PgTable } from "./table.js";

//#region src/pg-core/utils.ts
function getTableConfig(table) {
	const columns = Object.values(table[Table.Symbol.Columns]);
	const indexes = [];
	const checks = [];
	const primaryKeys = [];
	const foreignKeys = Object.values(table[PgTable.Symbol.InlineForeignKeys]);
	const uniqueConstraints = [];
	const name = table[Table.Symbol.Name];
	const schema = table[Table.Symbol.Schema];
	const policies = [];
	const enableRLS = table[PgTable.Symbol.EnableRLS];
	const extraConfigBuilder = table[PgTable.Symbol.ExtraConfigBuilder];
	if (extraConfigBuilder !== void 0) {
		const extraConfig = extraConfigBuilder(table[Table.Symbol.ExtraConfigColumns]);
		const extraValues = Array.isArray(extraConfig) ? extraConfig.flat(1) : Object.values(extraConfig);
		for (const builder of extraValues) if (is(builder, IndexBuilder)) indexes.push(builder.build(table));
		else if (is(builder, CheckBuilder)) checks.push(builder.build(table));
		else if (is(builder, UniqueConstraintBuilder)) uniqueConstraints.push(builder.build(table));
		else if (is(builder, PrimaryKeyBuilder)) primaryKeys.push(builder.build(table));
		else if (is(builder, ForeignKeyBuilder)) foreignKeys.push(builder.build(table));
		else if (is(builder, PgPolicy)) policies.push(builder);
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
	if (is(table, PgTable)) return [table[TableSchema] ? `${table[TableSchema]}.${table[Table.Symbol.BaseName]}` : table[Table.Symbol.BaseName]];
	if (is(table, Subquery)) return table._.usedTables ?? [];
	if (is(table, SQL)) return table.usedTables ?? [];
	return [];
}
function getViewConfig(view) {
	return {
		...view[ViewBaseConfig],
		...view[PgViewConfig]
	};
}
function getMaterializedViewConfig(view) {
	return {
		...view[ViewBaseConfig],
		...view[PgMaterializedViewConfig]
	};
}

//#endregion
export { extractUsedTable, getMaterializedViewConfig, getTableConfig, getViewConfig };
//# sourceMappingURL=utils.js.map