Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('../common.cjs');
let __entity_ts = require("../../../entity.cjs");
let __utils_ts = require("../../../utils.cjs");

//#region src/pg-core/columns/postgis_extension/geometry.ts
var PgGeometryBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgGeometryBuilder";
	constructor(name, srid) {
		super(name, "array geometry", "PgGeometry");
		this.config.srid = srid;
	}
	/** @internal */
	build(table) {
		return new PgGeometry(table, this.config);
	}
};
var PgGeometry = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgGeometry";
	/** @internal */
	codec = "geometry(point):tuple";
	srid = this.config.srid;
	mode = "tuple";
	getSQLType() {
		return `geometry(point${this.srid === void 0 ? "" : `,${this.srid}`})`;
	}
	mapToDriverValue = (value) => {
		return `point(${value[0]} ${value[1]})`;
	};
};
var PgGeometryObjectBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgGeometryObjectBuilder";
	constructor(name, srid) {
		super(name, "object geometry", "PgGeometryObject");
		this.config.srid = srid;
	}
	/** @internal */
	build(table) {
		return new PgGeometryObject(table, this.config);
	}
};
var PgGeometryObject = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgGeometryObject";
	/** @internal */
	codec = "geometry(point)";
	srid = this.config.srid;
	mode = "object";
	getSQLType() {
		return `geometry(point${this.srid === void 0 ? "" : `,${this.srid}`})`;
	}
	mapToDriverValue = (value) => {
		return `point(${value.x} ${value.y})`;
	};
};
function geometry(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	if (!config?.mode || config.mode === "tuple") return new PgGeometryBuilder(name, config?.srid);
	return new PgGeometryObjectBuilder(name, config?.srid);
}

//#endregion
exports.PgGeometry = PgGeometry;
exports.PgGeometryBuilder = PgGeometryBuilder;
exports.PgGeometryObject = PgGeometryObject;
exports.PgGeometryObjectBuilder = PgGeometryObjectBuilder;
exports.geometry = geometry;
//# sourceMappingURL=geometry.cjs.map