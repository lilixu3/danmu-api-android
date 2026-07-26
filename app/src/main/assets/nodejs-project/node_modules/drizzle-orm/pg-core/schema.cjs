Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_pg_core_table = require('./table.cjs');
const require_pg_core_columns_enum = require('./columns/enum.cjs');
const require_pg_core_sequence = require('./sequence.cjs');
const require_pg_core_view = require('./view.cjs');
let __entity_ts = require("../entity.cjs");
let __sql_sql_ts = require("../sql/sql.cjs");

//#region src/pg-core/schema.ts
var PgSchema = class {
	static [__entity_ts.entityKind] = "PgSchema";
	isExisting = false;
	constructor(schemaName, casing) {
		this.schemaName = schemaName;
		this.casing = casing;
		this.table = Object.assign(this.table, { withRLS: ((name, columns, extraConfig) => {
			const table = require_pg_core_table.pgTableWithSchema(name, columns, extraConfig, this.schemaName, this.casing);
			table[require_pg_core_table.EnableRLS] = true;
			return table;
		}) });
	}
	table = ((name, columns, extraConfig) => {
		return require_pg_core_table.pgTableWithSchema(name, columns, extraConfig, this.schemaName, this.casing);
	});
	view = ((name, columns) => {
		return require_pg_core_view.pgViewWithSchema(name, columns, this.schemaName, this.casing);
	});
	materializedView = ((name, columns) => {
		return require_pg_core_view.pgMaterializedViewWithSchema(name, columns, this.schemaName, this.casing);
	});
	enum(enumName, input) {
		return Array.isArray(input) ? require_pg_core_columns_enum.pgEnumWithSchema(enumName, [...input], this.schemaName) : require_pg_core_columns_enum.pgEnumObjectWithSchema(enumName, input, this.schemaName);
	}
	sequence = ((name, options) => {
		return require_pg_core_sequence.pgSequenceWithSchema(name, options, this.schemaName);
	});
	getSQL() {
		return new __sql_sql_ts.SQL([__sql_sql_ts.sql.identifier(this.schemaName)]);
	}
	shouldOmitSQLParens() {
		return true;
	}
	existing() {
		this.isExisting = true;
		return this;
	}
};
function isPgSchema(obj) {
	return (0, __entity_ts.is)(obj, PgSchema);
}
/** @internal */
function pgSchema(name, casing) {
	if (name === "public") throw new Error(`You can't specify 'public' as schema name. Postgres is using public schema by default. If you want to use 'public' schema, just use pgTable() instead of creating a schema`);
	return new PgSchema(name, casing);
}

//#endregion
exports.PgSchema = PgSchema;
exports.isPgSchema = isPgSchema;
exports.pgSchema = pgSchema;
//# sourceMappingURL=schema.cjs.map