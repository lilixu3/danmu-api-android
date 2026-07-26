Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_pg_core_columns_common = require('./common.cjs');
const require_pg_core_array = require('../array.cjs');
const require_pg_core_codecs = require('../codecs.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/pg-core/columns/custom.ts
var PgCustomColumnBuilder = class extends require_pg_core_columns_common.PgColumnBuilder {
	static [__entity_ts.entityKind] = "PgCustomColumnBuilder";
	constructor(name, fieldConfig, customTypeParams) {
		super(name, "custom", "PgCustomColumn");
		this.config.fieldConfig = fieldConfig;
		this.config.customTypeParams = customTypeParams;
	}
	/** @internal */
	build(table) {
		return new PgCustomColumn(table, this.config);
	}
};
var PgCustomColumn = class extends require_pg_core_columns_common.PgColumn {
	static [__entity_ts.entityKind] = "PgCustomColumn";
	/** @internal */
	codec;
	sqlName;
	mapFromJsonValue;
	jsonSelectIdentifier;
	constructor(table, config) {
		super(table, config);
		this.sqlName = config.customTypeParams.dataType(config.fieldConfig);
		this.mapToDriverValue = config.customTypeParams.toDriver ?? this.mapToDriverValue;
		this.mapFromDriverValue = config.customTypeParams.fromDriver ?? this.mapFromDriverValue;
		this.mapFromJsonValue = config.customTypeParams.fromJson;
		this.jsonSelectIdentifier = config.customTypeParams.forJsonSelect;
		const cfgCodec = typeof config.customTypeParams.codec === "string" || typeof config.customTypeParams.codec === "undefined" ? config.customTypeParams.codec : config.customTypeParams.codec(config.fieldConfig);
		this.codec = typeof cfgCodec === "string" ? require_pg_core_codecs.resolvePgTypeAlias(cfgCodec) : void 0;
		if (this.dimensions && config.customTypeParams.fromJson) this.mapFromJsonValue = (value) => {
			if (value === null) return value;
			const arr = typeof value === "string" ? require_pg_core_array.parsePgArray(value) : value;
			return this.mapJsonArrayElements(arr, config.customTypeParams.fromJson, this.dimensions);
		};
	}
	/** @internal */
	mapJsonArrayElements(value, mapper, depth) {
		if (depth > 0 && Array.isArray(value)) return value.map((v) => v === null ? null : this.mapJsonArrayElements(v, mapper, depth - 1));
		return mapper(value);
	}
	getSQLType() {
		return this.sqlName;
	}
};
/**
* Custom pg database data type generator
*/
function customType(customTypeParams) {
	return (a, b) => {
		const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
		return new PgCustomColumnBuilder(name, config, customTypeParams);
	};
}

//#endregion
exports.PgCustomColumn = PgCustomColumn;
exports.PgCustomColumnBuilder = PgCustomColumnBuilder;
exports.customType = customType;
//# sourceMappingURL=custom.cjs.map