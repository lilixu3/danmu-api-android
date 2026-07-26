Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./common.cjs');
const require_cockroach_core_columns_utils = require('./utils.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/cockroach-core/columns/geometry.ts
var CockroachGeometryBuilder = class extends require_cockroach_core_columns_common.CockroachColumnWithArrayBuilder {
	static [__entity_ts.entityKind] = "CockroachGeometryBuilder";
	constructor(name, srid) {
		super(name, "array geometry", "CockroachGeometry");
		this.config.srid = srid;
	}
	/** @internal */
	build(table) {
		return new CockroachGeometry(table, this.config);
	}
};
var CockroachGeometry = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachGeometry";
	srid = this.config.srid;
	mode = "tuple";
	getSQLType() {
		return `geometry(point${this.srid === void 0 ? "" : `,${this.srid}`})`;
	}
	mapFromDriverValue = (value) => {
		if (typeof value !== "string") return value;
		return require_cockroach_core_columns_utils.parseEWKB(value).point;
	};
	mapToDriverValue = (value) => {
		return `point(${value[0]} ${value[1]})`;
	};
};
var CockroachGeometryObjectBuilder = class extends require_cockroach_core_columns_common.CockroachColumnWithArrayBuilder {
	static [__entity_ts.entityKind] = "CockroachGeometryObjectBuilder";
	constructor(name, srid) {
		super(name, "object geometry", "CockroachGeometryObject");
		this.config.srid = srid;
	}
	/** @internal */
	build(table) {
		return new CockroachGeometryObject(table, this.config);
	}
};
var CockroachGeometryObject = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachGeometryObject";
	srid = this.config.srid;
	mode = "object";
	getSQLType() {
		return `geometry(point${this.srid === void 0 ? "" : `,${this.srid}`})`;
	}
	mapFromDriverValue = (value) => {
		const parsed = require_cockroach_core_columns_utils.parseEWKB(value);
		return {
			x: parsed.point[0],
			y: parsed.point[1]
		};
	};
	mapToDriverValue = (value) => {
		return `point(${value.x} ${value.y})`;
	};
};
function geometry(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	if (!config?.mode || config.mode === "tuple") return new CockroachGeometryBuilder(name, config?.srid);
	return new CockroachGeometryObjectBuilder(name, config?.srid);
}

//#endregion
exports.CockroachGeometry = CockroachGeometry;
exports.CockroachGeometryBuilder = CockroachGeometryBuilder;
exports.CockroachGeometryObject = CockroachGeometryObject;
exports.CockroachGeometryObjectBuilder = CockroachGeometryObjectBuilder;
exports.geometry = geometry;
//# sourceMappingURL=geometry.cjs.map