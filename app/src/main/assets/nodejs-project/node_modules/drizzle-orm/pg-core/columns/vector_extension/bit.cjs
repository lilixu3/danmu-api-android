Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('../common.cjs');
let __entity_ts = require("../../../entity.cjs");
let __utils_ts = require("../../../utils.cjs");

//#region src/pg-core/columns/vector_extension/bit.ts
var PgBinaryVectorBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgBinaryVectorBuilder";
	constructor(name, config) {
		super(name, "string binary", "PgBinaryVector");
		this.config.length = config.dimensions;
		this.config.isLengthExact = true;
	}
	/** @internal */
	build(table) {
		return new PgBinaryVector(table, this.config);
	}
};
var PgBinaryVector = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgBinaryVector";
	/** @internal */
	codec = "bit";
	getSQLType() {
		return `bit(${this.length})`;
	}
};
function bit(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new PgBinaryVectorBuilder(name, config);
}

//#endregion
exports.PgBinaryVector = PgBinaryVector;
exports.PgBinaryVectorBuilder = PgBinaryVectorBuilder;
exports.bit = bit;
//# sourceMappingURL=bit.cjs.map