import { CockroachColumn, CockroachColumnWithArrayBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/cockroach-core/columns/bit.ts
var CockroachBitBuilder = class extends CockroachColumnWithArrayBuilder {
	static [entityKind] = "CockroachBitBuilder";
	constructor(name, config) {
		super(name, "string binary", "CockroachBit");
		this.config.length = config.length ?? 1;
		this.config.setLength = config.length !== void 0;
		this.config.isLengthExact = true;
	}
	/** @internal */
	build(table) {
		return new CockroachBit(table, this.config);
	}
};
var CockroachBit = class extends CockroachColumn {
	static [entityKind] = "CockroachBit";
	getSQLType() {
		return this.config.setLength ? `bit(${this.length})` : "bit";
	}
};
function bit(a, b = {}) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new CockroachBitBuilder(name, config);
}

//#endregion
export { CockroachBit, CockroachBitBuilder, bit };
//# sourceMappingURL=bit.js.map