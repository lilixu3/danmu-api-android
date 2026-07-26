import { CockroachColumn, CockroachColumnWithArrayBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/cockroach-core/columns/string.ts
var CockroachStringBuilder = class extends CockroachColumnWithArrayBuilder {
	static [entityKind] = "CockroachStringBuilder";
	constructor(name, config) {
		super(name, config.enum?.length ? "string enum" : "string", "CockroachString");
		this.config.enumValues = config.enum;
		this.config.length = config.length;
	}
	/** @internal */
	build(table) {
		return new CockroachString(table, this.config);
	}
};
var CockroachString = class extends CockroachColumn {
	static [entityKind] = "CockroachString";
	enumValues = this.config.enumValues;
	getSQLType() {
		return this.length === void 0 ? `string` : `string(${this.length})`;
	}
};
function string(a, b = {}) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new CockroachStringBuilder(name, config);
}
function text(a, b = {}) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new CockroachStringBuilder(name, config);
}

//#endregion
export { CockroachString, CockroachStringBuilder, string, text };
//# sourceMappingURL=string.js.map