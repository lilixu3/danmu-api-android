Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/cockroach-core/columns/string.ts
var CockroachStringBuilder = class extends require_cockroach_core_columns_common.CockroachColumnWithArrayBuilder {
	static [__entity_ts.entityKind] = "CockroachStringBuilder";
	constructor(name, config) {
		super(name, config.enum?.length ? "string enum" : "string", "CockroachString");
		this.config.enumValues = config.enum;
		this.config.length = config.length;
	}
	/** @internal */
	build(table) {
		return new CockroachString(table, this.config);
	}
};
var CockroachString = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachString";
	enumValues = this.config.enumValues;
	getSQLType() {
		return this.length === void 0 ? `string` : `string(${this.length})`;
	}
};
function string(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new CockroachStringBuilder(name, config);
}
function text(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new CockroachStringBuilder(name, config);
}

//#endregion
exports.CockroachString = CockroachString;
exports.CockroachStringBuilder = CockroachStringBuilder;
exports.string = string;
exports.text = text;
//# sourceMappingURL=string.cjs.map