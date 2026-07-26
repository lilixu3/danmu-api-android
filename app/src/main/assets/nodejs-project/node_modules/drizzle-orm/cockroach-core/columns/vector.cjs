Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/cockroach-core/columns/vector.ts
var CockroachVectorBuilder = class extends require_cockroach_core_columns_common.CockroachColumnBuilder {
	static [__entity_ts.entityKind] = "CockroachVectorBuilder";
	constructor(name, config) {
		super(name, "array vector", "CockroachVector");
		this.config.length = config.dimensions;
	}
	/** @internal */
	build(table) {
		return new CockroachVector(table, this.config);
	}
};
var CockroachVector = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachVector";
	getSQLType() {
		return `vector(${this.config.length})`;
	}
	mapToDriverValue = (value) => {
		return JSON.stringify(value);
	};
	mapFromDriverValue = (value) => {
		return value.slice(1, -1).split(",").map((v) => Number.parseFloat(v));
	};
};
function vector(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	return new CockroachVectorBuilder(name, config);
}

//#endregion
exports.CockroachVector = CockroachVector;
exports.CockroachVectorBuilder = CockroachVectorBuilder;
exports.vector = vector;
//# sourceMappingURL=vector.cjs.map