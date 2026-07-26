import { SingleStoreColumn, SingleStoreColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { sql } from "../../sql/sql.js";

//#region src/singlestore-core/columns/date.common.ts
var SingleStoreDateColumnBaseBuilder = class extends SingleStoreColumnBuilder {
	static [entityKind] = "SingleStoreDateColumnBuilder";
	defaultNow() {
		return this.default(sql`now()`);
	}
	onUpdateNow() {
		this.config.hasOnUpdateNow = true;
		this.config.hasDefault = true;
		return this;
	}
};
var SingleStoreDateBaseColumn = class extends SingleStoreColumn {
	static [entityKind] = "SingleStoreDateColumn";
	hasOnUpdateNow = this.config.hasOnUpdateNow;
};

//#endregion
export { SingleStoreDateBaseColumn, SingleStoreDateColumnBaseBuilder };
//# sourceMappingURL=date.common.js.map