Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/pg-core/columns/point.ts
var PgPointTupleBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgPointTupleBuilder";
	constructor(name) {
		super(name, "array point", "PgPointTuple");
	}
	/** @internal */
	build(table) {
		return new PgPointTuple(table, this.config);
	}
};
var PgPointTuple = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgPointTuple";
	/** @internal */
	codec = "point:tuple";
	mode = "tuple";
	getSQLType() {
		return "point";
	}
	mapToDriverValue = (value) => {
		return `(${value[0]},${value[1]})`;
	};
};
var PgPointObjectBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgPointObjectBuilder";
	constructor(name) {
		super(name, "object point", "PgPointObject");
	}
	/** @internal */
	build(table) {
		return new PgPointObject(table, this.config);
	}
};
var PgPointObject = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgPointObject";
	/** @internal */
	codec = "point";
	mode = "xy";
	getSQLType() {
		return "point";
	}
	mapToDriverValue = (value) => {
		return `(${value.x},${value.y})`;
	};
};
function point(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	if (!config?.mode || config.mode === "tuple") return new PgPointTupleBuilder(name);
	return new PgPointObjectBuilder(name);
}

//#endregion
exports.PgPointObject = PgPointObject;
exports.PgPointObjectBuilder = PgPointObjectBuilder;
exports.PgPointTuple = PgPointTuple;
exports.PgPointTupleBuilder = PgPointTupleBuilder;
exports.point = point;
//# sourceMappingURL=point.cjs.map