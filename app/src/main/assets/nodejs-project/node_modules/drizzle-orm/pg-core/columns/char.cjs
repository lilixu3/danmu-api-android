Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/pg-core/columns/char.ts
var PgCharBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgCharBuilder";
	constructor(name, config) {
		super(name, config.enum?.length ? "string enum" : "string", "PgChar");
		this.config.length = config.length ?? 1;
		this.config.setLength = config.length !== void 0;
		this.config.enumValues = config.enum;
	}
	/** @internal */
	build(table) {
		return new PgChar(table, this.config);
	}
};
var PgChar = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgChar";
	/** @internal */
	codec = "char";
	enumValues;
	setLength;
	constructor(table, config) {
		super(table, config);
		this.enumValues = config.enumValues;
		this.setLength = config.setLength;
	}
	getSQLType() {
		return this.setLength ? `char(${this.length})` : `char`;
	}
};
function char(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new PgCharBuilder(name, config);
}

//#endregion
exports.PgChar = PgChar;
exports.PgCharBuilder = PgCharBuilder;
exports.char = char;
//# sourceMappingURL=char.cjs.map