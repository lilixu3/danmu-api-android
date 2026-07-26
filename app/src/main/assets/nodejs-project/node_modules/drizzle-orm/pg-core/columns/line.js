import { PgColumn, PgColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/pg-core/columns/line.ts
var PgLineBuilder = class extends PgColumnBuilder {
	static [entityKind] = "PgLineBuilder";
	constructor(name) {
		super(name, "array line", "PgLine");
	}
	/** @internal */
	build(table) {
		return new PgLineTuple(table, this.config);
	}
};
var PgLineTuple = class extends PgColumn {
	static [entityKind] = "PgLine";
	/** @internal */
	codec = "line:tuple";
	mode = "tuple";
	getSQLType() {
		return "line";
	}
	mapToDriverValue = (value) => {
		return `{${value[0]},${value[1]},${value[2]}}`;
	};
};
var PgLineABCBuilder = class extends PgColumnBuilder {
	static [entityKind] = "PgLineABCBuilder";
	constructor(name) {
		super(name, "object line", "PgLineABC");
	}
	/** @internal */
	build(table) {
		return new PgLineABC(table, this.config);
	}
};
var PgLineABC = class extends PgColumn {
	static [entityKind] = "PgLineABC";
	/** @internal */
	codec = "line";
	mode = "abc";
	getSQLType() {
		return "line";
	}
	mapToDriverValue = (value) => {
		return `{${value.a},${value.b},${value.c}}`;
	};
};
function line(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	if (!config?.mode || config.mode === "tuple") return new PgLineBuilder(name);
	return new PgLineABCBuilder(name);
}

//#endregion
export { PgLineABC, PgLineABCBuilder, PgLineBuilder, PgLineTuple, line };
//# sourceMappingURL=line.js.map