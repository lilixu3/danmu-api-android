Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/pg-core/columns/line.ts
var PgLineBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgLineBuilder";
	constructor(name) {
		super(name, "array line", "PgLine");
	}
	/** @internal */
	build(table) {
		return new PgLineTuple(table, this.config);
	}
};
var PgLineTuple = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgLine";
	/** @internal */
	codec = "line:tuple";
	mode = "tuple";
	getSQLType() {
		return "line";
	}
	mapToDriverValue = (value) => {
		return `{${value[0]},${value[1]},${value[2]}}`;
	};
};
var PgLineABCBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgLineABCBuilder";
	constructor(name) {
		super(name, "object line", "PgLineABC");
	}
	/** @internal */
	build(table) {
		return new PgLineABC(table, this.config);
	}
};
var PgLineABC = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgLineABC";
	/** @internal */
	codec = "line";
	mode = "abc";
	getSQLType() {
		return "line";
	}
	mapToDriverValue = (value) => {
		return `{${value.a},${value.b},${value.c}}`;
	};
};
function line(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	if (!config?.mode || config.mode === "tuple") return new PgLineBuilder(name);
	return new PgLineABCBuilder(name);
}

//#endregion
exports.PgLineABC = PgLineABC;
exports.PgLineABCBuilder = PgLineABCBuilder;
exports.PgLineBuilder = PgLineBuilder;
exports.PgLineTuple = PgLineTuple;
exports.line = line;
//# sourceMappingURL=line.cjs.map