Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('../common.cjs');
let __entity_ts = require("../../../entity.cjs");
let __utils_ts = require("../../../utils.cjs");

//#region src/pg-core/columns/vector_extension/sparsevec.ts
var PgSparseVectorBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgSparseVectorBuilder";
	constructor(name, config) {
		super(name, "string sparsevec", "PgSparseVector");
		this.config.vectorDimensions = config.dimensions;
	}
	/** @internal */
	build(table) {
		return new PgSparseVector(table, this.config);
	}
};
var PgSparseVector = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgSparseVector";
	/** @internal */
	codec = "sparsevec";
	vectorDimensions = this.config.vectorDimensions;
	getSQLType() {
		return `sparsevec(${this.vectorDimensions})`;
	}
};
function sparsevec(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new PgSparseVectorBuilder(name, config);
}

//#endregion
exports.PgSparseVector = PgSparseVector;
exports.PgSparseVectorBuilder = PgSparseVectorBuilder;
exports.sparsevec = sparsevec;
//# sourceMappingURL=sparsevec.cjs.map