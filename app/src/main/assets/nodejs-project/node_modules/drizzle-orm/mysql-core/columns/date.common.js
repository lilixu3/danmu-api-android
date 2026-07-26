import { MySqlColumn, MySqlColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";
import { sql } from "../../sql/sql.js";

//#region src/mysql-core/columns/date.common.ts
var MySqlDateColumnBaseBuilder = class extends MySqlColumnBuilder {
	static [entityKind] = "MySqlDateColumnBuilder";
	defaultNow() {
		return this.default(sql`(now())`);
	}
	onUpdateNow(config) {
		this.config.hasOnUpdateNow = true;
		this.config.onUpdateNowFsp = config?.fsp;
		this.config.hasDefault = true;
		return this;
	}
};
var MySqlDateBaseColumn = class extends MySqlColumn {
	static [entityKind] = "MySqlDateColumn";
	hasOnUpdateNow = this.config.hasOnUpdateNow;
	onUpdateNowFsp = this.config.onUpdateNowFsp;
};

//#endregion
export { MySqlDateBaseColumn, MySqlDateColumnBaseBuilder };
//# sourceMappingURL=date.common.js.map