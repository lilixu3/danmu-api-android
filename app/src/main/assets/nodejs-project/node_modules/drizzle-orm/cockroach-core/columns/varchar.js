import { CockroachColumn, CockroachColumnWithArrayBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/cockroach-core/columns/varchar.ts
var CockroachVarcharBuilder = class extends CockroachColumnWithArrayBuilder {
	static [entityKind] = "CockroachVarcharBuilder";
	constructor(name, config) {
		super(name, config.enum?.length ? "string enum" : "string", "CockroachVarchar");
		this.config.length = config.length;
		this.config.enumValues = config.enum;
	}
	/** @internal */
	build(table) {
		return new CockroachVarchar(table, this.config);
	}
};
var CockroachVarchar = class extends CockroachColumn {
	static [entityKind] = "CockroachVarchar";
	enumValues = this.config.enumValues;
	getSQLType() {
		return this.length === void 0 ? `varchar` : `varchar(${this.length})`;
	}
};
function varchar(a, b = {}) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new CockroachVarcharBuilder(name, config);
}

//#endregion
export { CockroachVarchar, CockroachVarcharBuilder, varchar };
//# sourceMappingURL=varchar.js.map