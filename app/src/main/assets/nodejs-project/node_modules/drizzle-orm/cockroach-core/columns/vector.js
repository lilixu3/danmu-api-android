import { CockroachColumn, CockroachColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/cockroach-core/columns/vector.ts
var CockroachVectorBuilder = class extends CockroachColumnBuilder {
	static [entityKind] = "CockroachVectorBuilder";
	constructor(name, config) {
		super(name, "array vector", "CockroachVector");
		this.config.length = config.dimensions;
	}
	/** @internal */
	build(table) {
		return new CockroachVector(table, this.config);
	}
};
var CockroachVector = class extends CockroachColumn {
	static [entityKind] = "CockroachVector";
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
	const { name, config } = getColumnNameAndConfig(a, b);
	return new CockroachVectorBuilder(name, config);
}

//#endregion
export { CockroachVector, CockroachVectorBuilder, vector };
//# sourceMappingURL=vector.js.map