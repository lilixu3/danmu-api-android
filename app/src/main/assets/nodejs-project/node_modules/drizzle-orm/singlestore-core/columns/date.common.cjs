Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_singlestore_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __sql_sql_ts = require("../../sql/sql.cjs");

//#region src/singlestore-core/columns/date.common.ts
var SingleStoreDateColumnBaseBuilder = class extends require_singlestore_core_columns_common.SingleStoreColumnBuilder {
	static [__entity_ts.entityKind] = "SingleStoreDateColumnBuilder";
	defaultNow() {
		return this.default(__sql_sql_ts.sql`now()`);
	}
	onUpdateNow() {
		this.config.hasOnUpdateNow = true;
		this.config.hasDefault = true;
		return this;
	}
};
var SingleStoreDateBaseColumn = class extends require_singlestore_core_columns_common.SingleStoreColumn {
	static [__entity_ts.entityKind] = "SingleStoreDateColumn";
	hasOnUpdateNow = this.config.hasOnUpdateNow;
};

//#endregion
exports.SingleStoreDateBaseColumn = SingleStoreDateBaseColumn;
exports.SingleStoreDateColumnBaseBuilder = SingleStoreDateColumnBaseBuilder;
//# sourceMappingURL=date.common.cjs.map