Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_table = require('./table.cjs');
const require_mssql_core_view = require('./view.cjs');
let __entity_ts = require("../entity.cjs");

//#region src/mssql-core/schema.ts
var MsSqlSchema = class {
	static [__entity_ts.entityKind] = "MsSqlSchema";
	isExisting = false;
	constructor(schemaName, casing) {
		this.schemaName = schemaName;
		this.casing = casing;
	}
	table = (name, columns, extraConfig) => {
		return require_mssql_core_table.mssqlTableWithSchema(name, columns, extraConfig, this.schemaName, this.casing);
	};
	view = ((name, columns) => {
		return require_mssql_core_view.mssqlViewWithSchema(name, columns, this.schemaName, this.casing);
	});
	existing() {
		this.isExisting = true;
		return this;
	}
};
/** @internal */
function mssqlSchema(name, casing) {
	return new MsSqlSchema(name, casing);
}

//#endregion
exports.MsSqlSchema = MsSqlSchema;
exports.mssqlSchema = mssqlSchema;
//# sourceMappingURL=schema.cjs.map