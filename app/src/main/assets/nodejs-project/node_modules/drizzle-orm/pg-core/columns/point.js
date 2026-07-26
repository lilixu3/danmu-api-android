import { PgColumn, PgColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/pg-core/columns/point.ts
var PgPointTupleBuilder = class extends PgColumnBuilder {
	static [entityKind] = "PgPointTupleBuilder";
	constructor(name) {
		super(name, "array point", "PgPointTuple");
	}
	/** @internal */
	build(table) {
		return new PgPointTuple(table, this.config);
	}
};
var PgPointTuple = class extends PgColumn {
	static [entityKind] = "PgPointTuple";
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
var PgPointObjectBuilder = class extends PgColumnBuilder {
	static [entityKind] = "PgPointObjectBuilder";
	constructor(name) {
		super(name, "object point", "PgPointObject");
	}
	/** @internal */
	build(table) {
		return new PgPointObject(table, this.config);
	}
};
var PgPointObject = class extends PgColumn {
	static [entityKind] = "PgPointObject";
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
	const { name, config } = getColumnNameAndConfig(a, b);
	if (!config?.mode || config.mode === "tuple") return new PgPointTupleBuilder(name);
	return new PgPointObjectBuilder(name);
}

//#endregion
export { PgPointObject, PgPointObjectBuilder, PgPointTuple, PgPointTupleBuilder, point };
//# sourceMappingURL=point.js.map