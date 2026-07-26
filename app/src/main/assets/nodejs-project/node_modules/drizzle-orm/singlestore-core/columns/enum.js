import { SingleStoreColumn, SingleStoreColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/singlestore-core/columns/enum.ts
var SingleStoreEnumColumnBuilder = class extends SingleStoreColumnBuilder {
	generatedAlwaysAs(as, config) {
		throw new Error("Method not implemented.");
	}
	static [entityKind] = "SingleStoreEnumColumnBuilder";
	constructor(name, values) {
		super(name, "string enum", "SingleStoreEnumColumn");
		this.config.enumValues = values;
	}
	/** @internal */
	build(table) {
		return new SingleStoreEnumColumn(table, this.config);
	}
};
var SingleStoreEnumColumn = class extends SingleStoreColumn {
	static [entityKind] = "SingleStoreEnumColumn";
	enumValues = this.config.enumValues;
	getSQLType() {
		return `enum(${this.enumValues.map((value) => `'${value}'`).join(",")})`;
	}
};
function singlestoreEnum(a, b) {
	const { name, config: values } = getColumnNameAndConfig(a, b);
	if (values.length === 0) throw new Error(`You have an empty array for "${name}" enum values`);
	return new SingleStoreEnumColumnBuilder(name, values);
}

//#endregion
export { SingleStoreEnumColumn, SingleStoreEnumColumnBuilder, singlestoreEnum };
//# sourceMappingURL=enum.js.map