import { SingleStoreColumn, SingleStoreColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/singlestore-core/columns/date.ts
var SingleStoreDateBuilder = class extends SingleStoreColumnBuilder {
	static [entityKind] = "SingleStoreDateBuilder";
	constructor(name) {
		super(name, "object date", "SingleStoreDate");
	}
	/** @internal */
	build(table) {
		return new SingleStoreDate(table, this.config);
	}
};
var SingleStoreDate = class extends SingleStoreColumn {
	static [entityKind] = "SingleStoreDate";
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return `date`;
	}
	mapFromDriverValue = (value) => {
		return new Date(value);
	};
};
var SingleStoreDateStringBuilder = class extends SingleStoreColumnBuilder {
	static [entityKind] = "SingleStoreDateStringBuilder";
	constructor(name) {
		super(name, "string date", "SingleStoreDateString");
	}
	/** @internal */
	build(table) {
		return new SingleStoreDateString(table, this.config);
	}
};
var SingleStoreDateString = class extends SingleStoreColumn {
	static [entityKind] = "SingleStoreDateString";
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return `date`;
	}
};
function date(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	if (config?.mode === "string") return new SingleStoreDateStringBuilder(name);
	return new SingleStoreDateBuilder(name);
}

//#endregion
export { SingleStoreDate, SingleStoreDateBuilder, SingleStoreDateString, SingleStoreDateStringBuilder, date };
//# sourceMappingURL=date.js.map