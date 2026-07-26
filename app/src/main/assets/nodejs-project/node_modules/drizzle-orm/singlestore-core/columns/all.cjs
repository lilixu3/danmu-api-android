Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_singlestore_core_columns_bigint = require('./bigint.cjs');
const require_singlestore_core_columns_binary = require('./binary.cjs');
const require_singlestore_core_columns_boolean = require('./boolean.cjs');
const require_singlestore_core_columns_char = require('./char.cjs');
const require_singlestore_core_columns_custom = require('./custom.cjs');
const require_singlestore_core_columns_date = require('./date.cjs');
const require_singlestore_core_columns_datetime = require('./datetime.cjs');
const require_singlestore_core_columns_decimal = require('./decimal.cjs');
const require_singlestore_core_columns_double = require('./double.cjs');
const require_singlestore_core_columns_enum = require('./enum.cjs');
const require_singlestore_core_columns_float = require('./float.cjs');
const require_singlestore_core_columns_int = require('./int.cjs');
const require_singlestore_core_columns_json = require('./json.cjs');
const require_singlestore_core_columns_mediumint = require('./mediumint.cjs');
const require_singlestore_core_columns_real = require('./real.cjs');
const require_singlestore_core_columns_serial = require('./serial.cjs');
const require_singlestore_core_columns_smallint = require('./smallint.cjs');
const require_singlestore_core_columns_text = require('./text.cjs');
const require_singlestore_core_columns_time = require('./time.cjs');
const require_singlestore_core_columns_timestamp = require('./timestamp.cjs');
const require_singlestore_core_columns_tinyint = require('./tinyint.cjs');
const require_singlestore_core_columns_varbinary = require('./varbinary.cjs');
const require_singlestore_core_columns_varchar = require('./varchar.cjs');
const require_singlestore_core_columns_vector = require('./vector.cjs');
const require_singlestore_core_columns_year = require('./year.cjs');

//#region src/singlestore-core/columns/all.ts
function getSingleStoreColumnBuilders() {
	return {
		bigint: require_singlestore_core_columns_bigint.bigint,
		binary: require_singlestore_core_columns_binary.binary,
		boolean: require_singlestore_core_columns_boolean.boolean,
		char: require_singlestore_core_columns_char.char,
		customType: require_singlestore_core_columns_custom.customType,
		date: require_singlestore_core_columns_date.date,
		datetime: require_singlestore_core_columns_datetime.datetime,
		decimal: require_singlestore_core_columns_decimal.decimal,
		double: require_singlestore_core_columns_double.double,
		singlestoreEnum: require_singlestore_core_columns_enum.singlestoreEnum,
		float: require_singlestore_core_columns_float.float,
		int: require_singlestore_core_columns_int.int,
		json: require_singlestore_core_columns_json.json,
		mediumint: require_singlestore_core_columns_mediumint.mediumint,
		real: require_singlestore_core_columns_real.real,
		serial: require_singlestore_core_columns_serial.serial,
		smallint: require_singlestore_core_columns_smallint.smallint,
		longtext: require_singlestore_core_columns_text.longtext,
		mediumtext: require_singlestore_core_columns_text.mediumtext,
		text: require_singlestore_core_columns_text.text,
		tinytext: require_singlestore_core_columns_text.tinytext,
		time: require_singlestore_core_columns_time.time,
		timestamp: require_singlestore_core_columns_timestamp.timestamp,
		tinyint: require_singlestore_core_columns_tinyint.tinyint,
		varbinary: require_singlestore_core_columns_varbinary.varbinary,
		varchar: require_singlestore_core_columns_varchar.varchar,
		vector: require_singlestore_core_columns_vector.vector,
		year: require_singlestore_core_columns_year.year
	};
}

//#endregion
exports.getSingleStoreColumnBuilders = getSingleStoreColumnBuilders;
//# sourceMappingURL=all.cjs.map