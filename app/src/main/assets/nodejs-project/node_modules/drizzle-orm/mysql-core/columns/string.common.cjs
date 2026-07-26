Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_mysql_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/mysql-core/columns/string.common.ts
var MySqlStringColumnBaseBuilder = class extends require_mysql_core_columns_common.MySqlColumnBuilder {
	static [__entity_ts.entityKind] = "MySqlStringColumnBuilder";
	charSet(charSet) {
		this.config.charSet = charSet;
		return this;
	}
	collate(collation) {
		this.config.collation = collation;
		return this;
	}
};
var MySqlStringBaseColumn = class extends require_mysql_core_columns_common.MySqlColumn {
	static [__entity_ts.entityKind] = "MySqlStringColumn";
	charSet = this.config.charSet;
	collation = this.config.collation;
};

//#endregion
exports.MySqlStringBaseColumn = MySqlStringBaseColumn;
exports.MySqlStringColumnBaseBuilder = MySqlStringColumnBaseBuilder;
//# sourceMappingURL=string.common.cjs.map