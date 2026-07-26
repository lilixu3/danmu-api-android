import { SingleStoreColumn, SingleStoreColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/singlestore-core/columns/varchar.ts
var SingleStoreVarCharBuilder = class extends SingleStoreColumnBuilder {
	static [entityKind] = "SingleStoreVarCharBuilder";
	/** @internal */
	constructor(name, config) {
		super(name, config.enum?.length ? "string enum" : "string", "SingleStoreVarChar");
		this.config.length = config.length;
		this.config.enum = config.enum;
	}
	/** @internal */
	build(table) {
		return new SingleStoreVarChar(table, this.config);
	}
};
var SingleStoreVarChar = class extends SingleStoreColumn {
	static [entityKind] = "SingleStoreVarChar";
	enumValues = this.config.enum;
	getSQLType() {
		return this.length === void 0 ? `varchar` : `varchar(${this.length})`;
	}
};
function varchar(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new SingleStoreVarCharBuilder(name, config);
}

//#endregion
export { SingleStoreVarChar, SingleStoreVarCharBuilder, varchar };
//# sourceMappingURL=varchar.js.map