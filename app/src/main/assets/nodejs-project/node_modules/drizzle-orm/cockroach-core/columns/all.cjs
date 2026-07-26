Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_cockroach_core_columns_bigint = require('./bigint.cjs');
const require_cockroach_core_columns_bit = require('./bit.cjs');
const require_cockroach_core_columns_bool = require('./bool.cjs');
const require_cockroach_core_columns_char = require('./char.cjs');
const require_cockroach_core_columns_custom = require('./custom.cjs');
const require_cockroach_core_columns_date = require('./date.cjs');
const require_cockroach_core_columns_decimal = require('./decimal.cjs');
const require_cockroach_core_columns_float = require('./float.cjs');
const require_cockroach_core_columns_geometry = require('./geometry.cjs');
const require_cockroach_core_columns_inet = require('./inet.cjs');
const require_cockroach_core_columns_integer = require('./integer.cjs');
const require_cockroach_core_columns_interval = require('./interval.cjs');
const require_cockroach_core_columns_jsonb = require('./jsonb.cjs');
const require_cockroach_core_columns_real = require('./real.cjs');
const require_cockroach_core_columns_smallint = require('./smallint.cjs');
const require_cockroach_core_columns_string = require('./string.cjs');
const require_cockroach_core_columns_time = require('./time.cjs');
const require_cockroach_core_columns_timestamp = require('./timestamp.cjs');
const require_cockroach_core_columns_uuid = require('./uuid.cjs');
const require_cockroach_core_columns_varbit = require('./varbit.cjs');
const require_cockroach_core_columns_varchar = require('./varchar.cjs');
const require_cockroach_core_columns_vector = require('./vector.cjs');

//#region src/cockroach-core/columns/all.ts
function getCockroachColumnBuilders() {
	return {
		bigint: require_cockroach_core_columns_bigint.bigint,
		bool: require_cockroach_core_columns_bool.bool,
		char: require_cockroach_core_columns_char.char,
		customType: require_cockroach_core_columns_custom.customType,
		date: require_cockroach_core_columns_date.date,
		doublePrecision: require_cockroach_core_columns_float.doublePrecision,
		inet: require_cockroach_core_columns_inet.inet,
		int4: require_cockroach_core_columns_integer.int4,
		int2: require_cockroach_core_columns_smallint.int2,
		int8: require_cockroach_core_columns_bigint.int8,
		interval: require_cockroach_core_columns_interval.interval,
		jsonb: require_cockroach_core_columns_jsonb.jsonb,
		numeric: require_cockroach_core_columns_decimal.numeric,
		decimal: require_cockroach_core_columns_decimal.decimal,
		geometry: require_cockroach_core_columns_geometry.geometry,
		real: require_cockroach_core_columns_real.real,
		smallint: require_cockroach_core_columns_smallint.smallint,
		text: require_cockroach_core_columns_string.text,
		time: require_cockroach_core_columns_time.time,
		timestamp: require_cockroach_core_columns_timestamp.timestamp,
		uuid: require_cockroach_core_columns_uuid.uuid,
		varchar: require_cockroach_core_columns_varchar.varchar,
		bit: require_cockroach_core_columns_bit.bit,
		vector: require_cockroach_core_columns_vector.vector,
		float: require_cockroach_core_columns_float.float,
		string: require_cockroach_core_columns_string.string,
		varbit: require_cockroach_core_columns_varbit.varbit
	};
}

//#endregion
exports.getCockroachColumnBuilders = getCockroachColumnBuilders;
//# sourceMappingURL=all.cjs.map