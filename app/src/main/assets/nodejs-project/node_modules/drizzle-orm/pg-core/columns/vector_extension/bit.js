import { PgColumn, PgColumnBuilder } from "../common.js";
import { entityKind } from "../../../entity.js";
import { getColumnNameAndConfig } from "../../../utils.js";

//#region src/pg-core/columns/vector_extension/bit.ts
var PgBinaryVectorBuilder = class extends PgColumnBuilder {
	static [entityKind] = "PgBinaryVectorBuilder";
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
var PgBinaryVector = class extends PgColumn {
	static [entityKind] = "PgBinaryVector";
	/** @internal */
	codec = "bit";
	getSQLType() {
		return `bit(${this.length})`;
	}
};
function bit(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new PgBinaryVectorBuilder(name, config);
}

//#endregion
export { PgBinaryVector, PgBinaryVectorBuilder, bit };
//# sourceMappingURL=bit.js.map