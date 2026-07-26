Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mysql_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __sql_sql_ts = require("../../sql/sql.cjs");

//#region src/mysql-core/columns/date.common.ts
var MySqlDateColumnBaseBuilder = class extends require_mysql_core_columns_common.MySqlColumnBuilder {
	static [__entity_ts.entityKind] = "MySqlDateColumnBuilder";
	defaultNow() {
		return this.default(__sql_sql_ts.sql`(now())`);
	}
	onUpdateNow(config) {
		this.config.hasOnUpdateNow = true;
		this.config.onUpdateNowFsp = config?.fsp;
		this.config.hasDefault = true;
		return this;
	}
};
var MySqlDateBaseColumn = class extends require_mysql_core_columns_common.MySqlColumn {
	static [__entity_ts.entityKind] = "MySqlDateColumn";
	hasOnUpdateNow = this.config.hasOnUpdateNow;
	onUpdateNowFsp = this.config.onUpdateNowFsp;
};

//#endregion
exports.MySqlDateBaseColumn = MySqlDateBaseColumn;
exports.MySqlDateColumnBaseBuilder = MySqlDateColumnBaseBuilder;
//# sourceMappingURL=date.common.cjs.map