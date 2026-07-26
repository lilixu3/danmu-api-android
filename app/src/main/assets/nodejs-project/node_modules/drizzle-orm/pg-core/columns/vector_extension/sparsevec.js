import { PgColumn, PgColumnBuilder } from "../common.js";
import { entityKind } from "../../../entity.js";
import { getColumnNameAndConfig } from "../../../utils.js";

//#region src/pg-core/columns/vector_extension/sparsevec.ts
var PgSparseVectorBuilder = class extends PgColumnBuilder {
	static [entityKind] = "PgSparseVectorBuilder";
	constructor(name, config) {
		super(name, "string sparsevec", "PgSparseVector");
		this.config.vectorDimensions = config.dimensions;
	}
	/** @internal */
	build(table) {
		return new PgSparseVector(table, this.config);
	}
};
var PgSparseVector = class extends PgColumn {
	static [entityKind] = "PgSparseVector";
	/** @internal */
	codec = "sparsevec";
	vectorDimensions = this.config.vectorDimensions;
	getSQLType() {
		return `sparsevec(${this.vectorDimensions})`;
	}
};
function sparsevec(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new PgSparseVectorBuilder(name, config);
}

//#endregion
export { PgSparseVector, PgSparseVectorBuilder, sparsevec };
//# sourceMappingURL=sparsevec.js.map