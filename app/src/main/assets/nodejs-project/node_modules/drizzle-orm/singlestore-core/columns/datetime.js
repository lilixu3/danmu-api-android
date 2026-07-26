import { SingleStoreColumn, SingleStoreColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";

//#region src/singlestore-core/columns/datetime.ts
var SingleStoreDateTimeBuilder = class extends SingleStoreColumnBuilder {
	generatedAlwaysAs(as, config) {
		throw new Error("Method not implemented.");
	}
	static [entityKind] = "SingleStoreDateTimeBuilder";
	constructor(name) {
		super(name, "object date", "SingleStoreDateTime");
	}
	/** @internal */
	build(table) {
		return new SingleStoreDateTime(table, this.config);
	}
};
var SingleStoreDateTime = class extends SingleStoreColumn {
	static [entityKind] = "SingleStoreDateTime";
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return `datetime`;
	}
	mapToDriverValue = (value) => {
		if (typeof value === "string") return value;
		return value.toISOString().replace("T", " ").replace("Z", "");
	};
	mapFromDriverValue = (value) => {
		return /* @__PURE__ */ new Date(value.replace(" ", "T") + "Z");
	};
};
var SingleStoreDateTimeStringBuilder = class extends SingleStoreColumnBuilder {
	generatedAlwaysAs(_as, _config) {
		throw new Error("Method not implemented.");
	}
	static [entityKind] = "SingleStoreDateTimeStringBuilder";
	constructor(name) {
		super(name, "string datetime", "SingleStoreDateTimeString");
	}
	/** @internal */
	build(table) {
		return new SingleStoreDateTimeString(table, this.config);
	}
};
var SingleStoreDateTimeString = class extends SingleStoreColumn {
	static [entityKind] = "SingleStoreDateTimeString";
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return `datetime`;
	}
	mapToDriverValue = (value) => {
		if (typeof value === "string") return value;
		return value.toISOString().replace("T", " ").replace("Z", "");
	};
};
function datetime(a, b) {
	const { name, config } = getColumnNameAndConfig(a, b);
	if (config?.mode === "string") return new SingleStoreDateTimeStringBuilder(name);
	return new SingleStoreDateTimeBuilder(name);
}

//#endregion
export { SingleStoreDateTime, SingleStoreDateTimeBuilder, SingleStoreDateTimeString, SingleStoreDateTimeStringBuilder, datetime };
//# sourceMappingURL=datetime.js.map