Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_mysql_core_columns_bigint = require('./bigint.cjs');
const require_mysql_core_columns_binary = require('./binary.cjs');
const require_mysql_core_columns_blob = require('./blob.cjs');
const require_mysql_core_columns_boolean = require('./boolean.cjs');
const require_mysql_core_columns_char = require('./char.cjs');
const require_mysql_core_columns_custom = require('./custom.cjs');
const require_mysql_core_columns_date = require('./date.cjs');
const require_mysql_core_columns_datetime = require('./datetime.cjs');
const require_mysql_core_columns_decimal = require('./decimal.cjs');
const require_mysql_core_columns_double = require('./double.cjs');
const require_mysql_core_columns_enum = require('./enum.cjs');
const require_mysql_core_columns_float = require('./float.cjs');
const require_mysql_core_columns_int = require('./int.cjs');
const require_mysql_core_columns_json = require('./json.cjs');
const require_mysql_core_columns_mediumint = require('./mediumint.cjs');
const require_mysql_core_columns_real = require('./real.cjs');
const require_mysql_core_columns_serial = require('./serial.cjs');
const require_mysql_core_columns_smallint = require('./smallint.cjs');
const require_mysql_core_columns_text = require('./text.cjs');
const require_mysql_core_columns_time = require('./time.cjs');
const require_mysql_core_columns_timestamp = require('./timestamp.cjs');
const require_mysql_core_columns_tinyint = require('./tinyint.cjs');
const require_mysql_core_columns_varbinary = require('./varbinary.cjs');
const require_mysql_core_columns_varchar = require('./varchar.cjs');
const require_mysql_core_columns_year = require('./year.cjs');

//#region src/mysql-core/columns/all.ts
function getMySqlColumnBuilders() {
	return {
		bigint: require_mysql_core_columns_bigint.bigint,
		binary: require_mysql_core_columns_binary.binary,
		boolean: require_mysql_core_columns_boolean.boolean,
		char: require_mysql_core_columns_char.char,
		customType: require_mysql_core_columns_custom.customType,
		date: require_mysql_core_columns_date.date,
		datetime: require_mysql_core_columns_datetime.datetime,
		decimal: require_mysql_core_columns_decimal.decimal,
		double: require_mysql_core_columns_double.double,
		mysqlEnum: require_mysql_core_columns_enum.mysqlEnum,
		float: require_mysql_core_columns_float.float,
		int: require_mysql_core_columns_int.int,
		json: require_mysql_core_columns_json.json,
		mediumint: require_mysql_core_columns_mediumint.mediumint,
		real: require_mysql_core_columns_real.real,
		serial: require_mysql_core_columns_serial.serial,
		smallint: require_mysql_core_columns_smallint.smallint,
		text: require_mysql_core_columns_text.text,
		time: require_mysql_core_columns_time.time,
		timestamp: require_mysql_core_columns_timestamp.timestamp,
		tinyint: require_mysql_core_columns_tinyint.tinyint,
		varbinary: require_mysql_core_columns_varbinary.varbinary,
		varchar: require_mysql_core_columns_varchar.varchar,
		year: require_mysql_core_columns_year.year,
		longtext: require_mysql_core_columns_text.longtext,
		mediumtext: require_mysql_core_columns_text.mediumtext,
		tinytext: require_mysql_core_columns_text.tinytext,
		blob: require_mysql_core_columns_blob.blob,
		longblob: require_mysql_core_columns_blob.longblob,
		mediumblob: require_mysql_core_columns_blob.mediumblob,
		tinyblob: require_mysql_core_columns_blob.tinyblob
	};
}

//#endregion
exports.getMySqlColumnBuilders = getMySqlColumnBuilders;
//# sourceMappingURL=all.cjs.map