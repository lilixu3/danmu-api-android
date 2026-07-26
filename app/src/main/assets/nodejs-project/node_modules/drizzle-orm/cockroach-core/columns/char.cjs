Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/cockroach-core/columns/char.ts
var CockroachCharBuilder = class extends require_cockroach_core_columns_common.CockroachColumnWithArrayBuilder {
	static [__entity_ts.entityKind] = "CockroachCharBuilder";
	constructor(name, config) {
		super(name, config.enum?.length ? "string enum" : "string", "CockroachChar");
		this.config.enumValues = config.enum;
		this.config.length = config.length ?? 1;
		this.config.setLength = config.length !== void 0;
	}
	/** @internal */
	build(table) {
		return new CockroachChar(table, this.config);
	}
};
var CockroachChar = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachChar";
	enumValues = this.config.enumValues;
	getSQLType() {
		return this.config.setLength ? `char(${this.length})` : `char`;
	}
};
function char(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new CockroachCharBuilder(name, config);
}

//#endregion
exports.CockroachChar = CockroachChar;
exports.CockroachCharBuilder = CockroachCharBuilder;
exports.char = char;
//# sourceMappingURL=char.cjs.map