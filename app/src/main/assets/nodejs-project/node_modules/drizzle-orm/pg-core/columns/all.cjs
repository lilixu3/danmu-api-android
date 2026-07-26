Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_pg_core_columns_bigint = require('./bigint.cjs');
const require_pg_core_columns_bigserial = require('./bigserial.cjs');
const require_pg_core_columns_boolean = require('./boolean.cjs');
const require_pg_core_columns_char = require('./char.cjs');
const require_pg_core_columns_cidr = require('./cidr.cjs');
const require_pg_core_columns_custom = require('./custom.cjs');
const require_pg_core_columns_date = require('./date.cjs');
const require_pg_core_columns_double_precision = require('./double-precision.cjs');
const require_pg_core_columns_inet = require('./inet.cjs');
const require_pg_core_columns_integer = require('./integer.cjs');
const require_pg_core_columns_interval = require('./interval.cjs');
const require_pg_core_columns_json = require('./json.cjs');
const require_pg_core_columns_jsonb = require('./jsonb.cjs');
const require_pg_core_columns_line = require('./line.cjs');
const require_pg_core_columns_macaddr = require('./macaddr.cjs');
const require_pg_core_columns_macaddr8 = require('./macaddr8.cjs');
const require_pg_core_columns_numeric = require('./numeric.cjs');
const require_pg_core_columns_point = require('./point.cjs');
const require_pg_core_columns_postgis_extension_geometry = require('./postgis_extension/geometry.cjs');
const require_pg_core_columns_real = require('./real.cjs');
const require_pg_core_columns_serial = require('./serial.cjs');
const require_pg_core_columns_smallint = require('./smallint.cjs');
const require_pg_core_columns_smallserial = require('./smallserial.cjs');
const require_pg_core_columns_text = require('./text.cjs');
const require_pg_core_columns_time = require('./time.cjs');
const require_pg_core_columns_timestamp = require('./timestamp.cjs');
const require_pg_core_columns_uuid = require('./uuid.cjs');
const require_pg_core_columns_varchar = require('./varchar.cjs');
const require_pg_core_columns_vector_extension_bit = require('./vector_extension/bit.cjs');
const require_pg_core_columns_vector_extension_halfvec = require('./vector_extension/halfvec.cjs');
const require_pg_core_columns_vector_extension_sparsevec = require('./vector_extension/sparsevec.cjs');
const require_pg_core_columns_vector_extension_vector = require('./vector_extension/vector.cjs');

//#region src/pg-core/columns/all.ts
function getPgColumnBuilders() {
	return {
		bigint: require_pg_core_columns_bigint.bigint,
		bigserial: require_pg_core_columns_bigserial.bigserial,
		boolean: require_pg_core_columns_boolean.boolean,
		char: require_pg_core_columns_char.char,
		cidr: require_pg_core_columns_cidr.cidr,
		customType: require_pg_core_columns_custom.customType,
		date: require_pg_core_columns_date.date,
		doublePrecision: require_pg_core_columns_double_precision.doublePrecision,
		inet: require_pg_core_columns_inet.inet,
		integer: require_pg_core_columns_integer.integer,
		interval: require_pg_core_columns_interval.interval,
		json: require_pg_core_columns_json.json,
		jsonb: require_pg_core_columns_jsonb.jsonb,
		line: require_pg_core_columns_line.line,
		macaddr: require_pg_core_columns_macaddr.macaddr,
		macaddr8: require_pg_core_columns_macaddr8.macaddr8,
		numeric: require_pg_core_columns_numeric.numeric,
		point: require_pg_core_columns_point.point,
		geometry: require_pg_core_columns_postgis_extension_geometry.geometry,
		real: require_pg_core_columns_real.real,
		serial: require_pg_core_columns_serial.serial,
		smallint: require_pg_core_columns_smallint.smallint,
		smallserial: require_pg_core_columns_smallserial.smallserial,
		text: require_pg_core_columns_text.text,
		time: require_pg_core_columns_time.time,
		timestamp: require_pg_core_columns_timestamp.timestamp,
		uuid: require_pg_core_columns_uuid.uuid,
		varchar: require_pg_core_columns_varchar.varchar,
		bit: require_pg_core_columns_vector_extension_bit.bit,
		halfvec: require_pg_core_columns_vector_extension_halfvec.halfvec,
		sparsevec: require_pg_core_columns_vector_extension_sparsevec.sparsevec,
		vector: require_pg_core_columns_vector_extension_vector.vector
	};
}

//#endregion
exports.getPgColumnBuilders = getPgColumnBuilders;
//# sourceMappingURL=all.cjs.map