Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");

//#region src/cockroach-core/columns/jsonb.ts
var CockroachJsonbBuilder = class extends require_cockroach_core_columns_common.CockroachColumnBuilder {
	static [__entity_ts.entityKind] = "CockroachJsonbBuilder";
	constructor(name) {
		super(name, "object json", "CockroachJsonb");
	}
	/** @internal */
	build(table) {
		return new CockroachJsonb(table, this.config);
	}
};
var CockroachJsonb = class extends require_cockroach_core_columns_common.CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachJsonb";
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return "jsonb";
	}
	mapToDriverValue = (value) => {
		return JSON.stringify(value);
	};
	mapFromDriverValue = (value) => {
		if (typeof value === "string") try {
			return JSON.parse(value);
		} catch {
			return value;
		}
		return value;
	};
};
function jsonb(name) {
	return new CockroachJsonbBuilder(name ?? "");
}

//#endregion
exports.CockroachJsonb = CockroachJsonb;
exports.CockroachJsonbBuilder = CockroachJsonbBuilder;
exports.jsonb = jsonb;
//# sourceMappingURL=jsonb.cjs.map