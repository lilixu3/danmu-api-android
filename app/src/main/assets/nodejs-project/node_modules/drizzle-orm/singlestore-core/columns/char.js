import { SingleStoreColumn, SingleStoreColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/singlestore-core/columns/char.ts
var SingleStoreCharBuilder = class extends SingleStoreColumnBuilder {
	static [entityKind] = "SingleStoreCharBuilder";
	constructor(name, config) {
		super(name, config.enum?.length ? "string enum" : "string", "SingleStoreChar");
		this.config.length = config.length ?? 1;
		this.config.setLength = config.length !== void 0;
		this.config.enum = config.enum;
	}
	/** @internal */
	build(table) {
		return new SingleStoreChar(table, this.config);
	}
};
var SingleStoreChar = class extends SingleStoreColumn {
	static [entityKind] = "SingleStoreChar";
	enumValues = this.config.enum;
	getSQLType() {
		return this.config.setLength ? `char(${this.length})` : `char`;
	}
};
function char(a, b = {}) {
	const { name, config } = getColumnNameAndConfig(a, b);
	return new SingleStoreCharBuilder(name, config);
}

//#endregion
export { SingleStoreChar, SingleStoreCharBuilder, char };
//# sourceMappingURL=char.js.map