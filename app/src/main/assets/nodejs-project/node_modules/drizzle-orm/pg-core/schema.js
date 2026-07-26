import { EnableRLS, pgTableWithSchema } from "./table.js";
import { pgEnumObjectWithSchema, pgEnumWithSchema } from "./columns/enum.js";
import { pgSequenceWithSchema } from "./sequence.js";
import { pgMaterializedViewWithSchema, pgViewWithSchema } from "./view.js";
import { entityKind, is } from "../entity.js";
import { SQL, sql } from "../sql/sql.js";

//#region src/pg-core/schema.ts
var PgSchema = class {
	static [entityKind] = "PgSchema";
	isExisting = false;
	constructor(schemaName, casing) {
		this.schemaName = schemaName;
		this.casing = casing;
		this.table = Object.assign(this.table, { withRLS: ((name, columns, extraConfig) => {
			const table = pgTableWithSchema(name, columns, extraConfig, this.schemaName, this.casing);
			table[EnableRLS] = true;
			return table;
		}) });
	}
	table = ((name, columns, extraConfig) => {
		return pgTableWithSchema(name, columns, extraConfig, this.schemaName, this.casing);
	});
	view = ((name, columns) => {
		return pgViewWithSchema(name, columns, this.schemaName, this.casing);
	});
	materializedView = ((name, columns) => {
		return pgMaterializedViewWithSchema(name, columns, this.schemaName, this.casing);
	});
	enum(enumName, input) {
		return Array.isArray(input) ? pgEnumWithSchema(enumName, [...input], this.schemaName) : pgEnumObjectWithSchema(enumName, input, this.schemaName);
	}
	sequence = ((name, options) => {
		return pgSequenceWithSchema(name, options, this.schemaName);
	});
	getSQL() {
		return new SQL([sql.identifier(this.schemaName)]);
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
	return is(obj, PgSchema);
}
/** @internal */
function pgSchema(name, casing) {
	if (name === "public") throw new Error(`You can't specify 'public' as schema name. Postgres is using public schema by default. If you want to use 'public' schema, just use pgTable() instead of creating a schema`);
	return new PgSchema(name, casing);
}

//#endregion
export { PgSchema, isPgSchema, pgSchema };
//# sourceMappingURL=schema.js.map