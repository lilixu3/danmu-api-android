import { CockroachColumn, CockroachColumnWithArrayBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/cockroach-core/columns/char.ts
var CockroachCharBuilder = class extends CockroachColumnWithArrayBuilder {
	static [entityKind] = "CockroachCharBuilder";
	constructor(name, config) {
		super(name, config.enum?.length ? "string enum" : "string", "CockroachChar");
		this.config.enumValues = config.enum;
		this.config.length = config.length ?? 1;
		this.config.setLength = config.length !== void 0;
	}
	/** @internal */
	build(table) {
		return new CockroachChar(table, this.config);
	}
};
var CockroachChar = class extends CockroachColumn {
	static [entityKind] = "CockroachChar";
	enumValues = this.config.enumValues;
	getSQLType() {
		return this.config.setLength ? `char(${this.length})` : `char`;
	}
};
function char(a, b = {}) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new CockroachCharBuilder(name, config);
}

//#endregion
export { CockroachChar, CockroachCharBuilder, char };
//# sourceMappingURL=char.js.map