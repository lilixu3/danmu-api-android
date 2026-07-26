Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/pg-core/columns/double-precision.ts
var PgDoublePrecisionBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgDoublePrecisionBuilder";
	constructor(name) {
		super(name, "number double", "PgDoublePrecision");
	}
	/** @internal */
	build(table) {
		return new PgDoublePrecision(table, this.config);
	}
};
var PgDoublePrecision = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgDoublePrecision";
	/** @internal */
	codec = "float8";
	getSQLType() {
		return "double precision";
	}
};
function doublePrecision(name) {
	return new PgDoublePrecisionBuilder(name ?? "");
}

//#endregion
exports.PgDoublePrecision = PgDoublePrecision;
exports.PgDoublePrecisionBuilder = PgDoublePrecisionBuilder;
exports.doublePrecision = doublePrecision;
//# sourceMappingURL=double-precision.cjs.map