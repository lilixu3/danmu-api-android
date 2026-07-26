Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('../common.cjs');
let __entity_ts = require("../../../entity.cjs");
let __utils_ts = require("../../../utils.cjs");

//#region src/pg-core/columns/vector_extension/halfvec.ts
var PgHalfVectorBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgHalfVectorBuilder";
	constructor(name, config) {
		super(name, "array halfvector", "PgHalfVector");
		this.config.length = config.dimensions;
		this.config.isLengthExact = true;
	}
	/** @internal */
	build(table) {
		return new PgHalfVector(table, this.config);
	}
};
var PgHalfVector = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgHalfVector";
	/** @internal */
	codec = "halfvec";
	getSQLType() {
		return `halfvec(${this.length})`;
	}
	mapToDriverValue = (value) => {
		return JSON.stringify(value);
	};
};
function halfvec(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new PgHalfVectorBuilder(name, config);
}

//#endregion
exports.PgHalfVector = PgHalfVector;
exports.PgHalfVectorBuilder = PgHalfVectorBuilder;
exports.halfvec = halfvec;
//# sourceMappingURL=halfvec.cjs.map