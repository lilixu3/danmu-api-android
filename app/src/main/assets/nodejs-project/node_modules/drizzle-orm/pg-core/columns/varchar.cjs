Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/pg-core/columns/varchar.ts
var PgVarcharBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgVarcharBuilder";
	constructor(name, config) {
		super(name, config.enum?.length ? "string enum" : "string", "PgVarchar");
		this.config.length = config.length;
		this.config.enumValues = config.enum;
	}
	/** @internal */
	build(table) {
		return new PgVarchar(table, this.config);
	}
};
var PgVarchar = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgVarchar";
	/** @internal */
	codec = "varchar";
	enumValues;
	constructor(table, config) {
		super(table, config);
		this.enumValues = config.enumValues;
	}
	getSQLType() {
		return this.length === void 0 ? `varchar` : `varchar(${this.length})`;
	}
};
function varchar(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new PgVarcharBuilder(name, config);
}

//#endregion
exports.PgVarchar = PgVarchar;
exports.PgVarcharBuilder = PgVarcharBuilder;
exports.varchar = varchar;
//# sourceMappingURL=varchar.cjs.map