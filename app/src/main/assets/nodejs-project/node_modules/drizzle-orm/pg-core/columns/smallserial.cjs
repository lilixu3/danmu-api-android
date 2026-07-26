Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/pg-core/columns/smallserial.ts
var PgSmallSerialBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgSmallSerialBuilder";
	constructor(name) {
		super(name, "number int16", "PgSmallSerial");
		this.config.hasDefault = true;
		this.config.notNull = true;
	}
	/** @internal */
	build(table) {
		return new PgSmallSerial(table, this.config);
	}
};
var PgSmallSerial = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgSmallSerial";
	/** @internal */
	codec = "smallserial";
	getSQLType() {
		return "smallserial";
	}
};
function smallserial(name) {
	return new PgSmallSerialBuilder(name ?? "");
}

//#endregion
exports.PgSmallSerial = PgSmallSerial;
exports.PgSmallSerialBuilder = PgSmallSerialBuilder;
exports.smallserial = smallserial;
//# sourceMappingURL=smallserial.cjs.map