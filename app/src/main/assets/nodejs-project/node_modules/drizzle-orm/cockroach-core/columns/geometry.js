import { CockroachColumn, CockroachColumnWithArrayBuilder } from "./common.js";
import { parseEWKB } from "./utils.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/cockroach-core/columns/geometry.ts
var CockroachGeometryBuilder = class extends CockroachColumnWithArrayBuilder {
	static [entityKind] = "CockroachGeometryBuilder";
	constructor(name, srid) {
		super(name, "array geometry", "CockroachGeometry");
		this.config.srid = srid;
	}
	/** @internal */
	build(table) {
		return new CockroachGeometry(table, this.config);
	}
};
var CockroachGeometry = class extends CockroachColumn {
	static [entityKind] = "CockroachGeometry";
	srid = this.config.srid;
	mode = "tuple";
	getSQLType() {
		return `geometry(point${this.srid === void 0 ? "" : `,${this.srid}`})`;
	}
	mapFromDriverValue = (value) => {
		if (typeof value !== "string") return value;
		return parseEWKB(value).point;
	};
	mapToDriverValue = (value) => {
		return `point(${value[0]} ${value[1]})`;
	};
};
var CockroachGeometryObjectBuilder = class extends CockroachColumnWithArrayBuilder {
	static [entityKind] = "CockroachGeometryObjectBuilder";
	constructor(name, srid) {
		super(name, "object geometry", "CockroachGeometryObject");
		this.config.srid = srid;
	}
	/** @internal */
	build(table) {
		return new CockroachGeometryObject(table, this.config);
	}
};
var CockroachGeometryObject = class extends CockroachColumn {
	static [entityKind] = "CockroachGeometryObject";
	srid = this.config.srid;
	mode = "object";
	getSQLType() {
		return `geometry(point${this.srid === void 0 ? "" : `,${this.srid}`})`;
	}
	mapFromDriverValue = (value) => {
		const parsed = parseEWKB(value);
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
	const { name, config } = getColumnNameAndConfig(a, b);
	if (!config?.mode || config.mode === "tuple") return new CockroachGeometryBuilder(name, config?.srid);
	return new CockroachGeometryObjectBuilder(name, config?.srid);
}

//#endregion
export { CockroachGeometry, CockroachGeometryBuilder, CockroachGeometryObject, CockroachGeometryObjectBuilder, geometry };
//# sourceMappingURL=geometry.js.map