import { SingleStoreDateBaseColumn, SingleStoreDateColumnBaseBuilder } from "./date.common.js";
import { entityKind } from "../../entity.js";
import { getColumnNameAndConfig } from "../../utils.js";
import { sql } from "../../sql/sql.js";

//#region src/singlestore-core/columns/timestamp.ts
var SingleStoreTimestampBuilder = class extends SingleStoreDateColumnBaseBuilder {
	static [entityKind] = "SingleStoreTimestampBuilder";
	constructor(name) {
		super(name, "object date", "SingleStoreTimestamp");
	}
	/** @internal */
	build(table) {
		return new SingleStoreTimestamp(table, this.config);
	}
	defaultNow() {
		return this.default(sql`CURRENT_TIMESTAMP`);
	}
};
var SingleStoreTimestamp = class extends SingleStoreDateBaseColumn {
	static [entityKind] = "SingleStoreTimestamp";
	getSQLType() {
		return `timestamp`;
	}
	mapFromDriverValue = (value) => {
		return /* @__PURE__ */ new Date(value + "+0000");
	};
	mapToDriverValue = (value) => {
		if (typeof value === "string") return value;
		return value.toISOString().slice(0, -1).replace("T", " ");
	};
};
var SingleStoreTimestampStringBuilder = class extends SingleStoreDateColumnBaseBuilder {
	static [entityKind] = "SingleStoreTimestampStringBuilder";
	constructor(name) {
		super(name, "string timestamp", "SingleStoreTimestampString");
	}
	/** @internal */
	build(table) {
		return new SingleStoreTimestampString(table, this.config);
	}
	defaultNow() {
		return this.default(sql`CURRENT_TIMESTAMP`);
	}
};
var SingleStoreTimestampString = class extends SingleStoreDateBaseColumn {
	static [entityKind] = "SingleStoreTimestampString";
	getSQLType() {
		return `timestamp`;
	}
	mapToDriverValue = (value) => {
		if (typeof value === "string") return value;
		return value.toISOString().slice(0, -1).replace("T", " ");
	};
};
function timestamp(a, b = {}) {
	const { name, config } = getColumnNameAndConfig(a, b);
	if (config?.mode === "string") return new SingleStoreTimestampStringBuilder(name);
	return new SingleStoreTimestampBuilder(name);
}

//#endregion
export { SingleStoreTimestamp, SingleStoreTimestampBuilder, SingleStoreTimestampString, SingleStoreTimestampStringBuilder, timestamp };
//# sourceMappingURL=timestamp.js.map