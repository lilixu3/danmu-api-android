Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_mysql_core_table = require('./table.cjs');
const require_mysql_core_view = require('./view.cjs');
let __entity_ts = require("../entity.cjs");

//#region src/mysql-core/schema.ts
var MySqlSchema = class {
	static [__entity_ts.entityKind] = "MySqlSchema";
	constructor(schemaName, casing) {
		this.schemaName = schemaName;
		this.casing = casing;
	}
	table = (name, columns, extraConfig) => {
		return require_mysql_core_table.mysqlTableWithSchema(name, columns, extraConfig, this.schemaName, this.casing);
	};
	view = ((name, columns) => {
		return require_mysql_core_view.mysqlViewWithSchema(name, columns, this.schemaName, this.casing);
	});
};
/** @deprecated - use `instanceof MySqlSchema` */
function isMySqlSchema(obj) {
	return (0, __entity_ts.is)(obj, MySqlSchema);
}
/** @internal */
function mysqlDatabase(name, casing) {
	return new MySqlSchema(name, casing);
}
/**
* @see mysqlDatabase
*/
const mysqlSchema = mysqlDatabase;

//#endregion
exports.MySqlSchema = MySqlSchema;
exports.isMySqlSchema = isMySqlSchema;
exports.mysqlDatabase = mysqlDatabase;
exports.mysqlSchema = mysqlSchema;
//# sourceMappingURL=schema.cjs.map