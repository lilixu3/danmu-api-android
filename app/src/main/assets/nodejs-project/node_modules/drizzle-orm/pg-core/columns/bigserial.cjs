Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/pg-core/columns/bigserial.ts
var PgBigSerial53Builder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgBigSerial53Builder";
	constructor(name) {
		super(name, "number int53", "PgBigSerial53");
		this.config.hasDefault = true;
		this.config.notNull = true;
	}
	/** @internal */
	build(table) {
		return new PgBigSerial53(table, this.config);
	}
};
var PgBigSerial53 = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgBigSerial53";
	/** @internal */
	codec = "bigserial:number";
	getSQLType() {
		return "bigserial";
	}
};
var PgBigSerial64Builder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgBigSerial64Builder";
	constructor(name) {
		super(name, "bigint int64", "PgBigSerial64");
		this.config.hasDefault = true;
		this.config.notNull = true;
	}
	/** @internal */
	build(table) {
		return new PgBigSerial64(table, this.config);
	}
};
var PgBigSerial64 = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgBigSerial64";
	/** @internal */
	codec = "bigserial";
	getSQLType() {
		return "bigserial";
	}
};
function bigserial(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	if (config.mode === "number") return new PgBigSerial53Builder(name);
	return new PgBigSerial64Builder(name);
}

//#endregion
exports.PgBigSerial53 = PgBigSerial53;
exports.PgBigSerial53Builder = PgBigSerial53Builder;
exports.PgBigSerial64 = PgBigSerial64;
exports.PgBigSerial64Builder = PgBigSerial64Builder;
exports.bigserial = bigserial;
//# sourceMappingURL=bigserial.cjs.map