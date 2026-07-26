Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_mssql_core_columns_bigint = require('./bigint.cjs');
const require_mssql_core_columns_binary = require('./binary.cjs');
const require_mssql_core_columns_bit = require('./bit.cjs');
const require_mssql_core_columns_char = require('./char.cjs');
const require_mssql_core_columns_custom = require('./custom.cjs');
const require_mssql_core_columns_date = require('./date.cjs');
const require_mssql_core_columns_datetime = require('./datetime.cjs');
const require_mssql_core_columns_datetime2 = require('./datetime2.cjs');
const require_mssql_core_columns_datetimeoffset = require('./datetimeoffset.cjs');
const require_mssql_core_columns_decimal = require('./decimal.cjs');
const require_mssql_core_columns_float = require('./float.cjs');
const require_mssql_core_columns_int = require('./int.cjs');
const require_mssql_core_columns_numeric = require('./numeric.cjs');
const require_mssql_core_columns_real = require('./real.cjs');
const require_mssql_core_columns_smallint = require('./smallint.cjs');
const require_mssql_core_columns_text = require('./text.cjs');
const require_mssql_core_columns_time = require('./time.cjs');
const require_mssql_core_columns_tinyint = require('./tinyint.cjs');
const require_mssql_core_columns_varbinary = require('./varbinary.cjs');
const require_mssql_core_columns_varchar = require('./varchar.cjs');

//#region src/mssql-core/columns/all.ts
function getMsSqlColumnBuilders() {
	return {
		bigint: require_mssql_core_columns_bigint.bigint,
		binary: require_mssql_core_columns_binary.binary,
		bit: require_mssql_core_columns_bit.bit,
		char: require_mssql_core_columns_char.char,
		customType: require_mssql_core_columns_custom.customType,
		date: require_mssql_core_columns_date.date,
		datetime: require_mssql_core_columns_datetime.datetime,
		datetime2: require_mssql_core_columns_datetime2.datetime2,
		datetimeoffset: require_mssql_core_columns_datetimeoffset.datetimeoffset,
		decimal: require_mssql_core_columns_decimal.decimal,
		float: require_mssql_core_columns_float.float,
		int: require_mssql_core_columns_int.int,
		real: require_mssql_core_columns_real.real,
		numeric: require_mssql_core_columns_numeric.numeric,
		smallint: require_mssql_core_columns_smallint.smallint,
		text: require_mssql_core_columns_text.text,
		ntext: require_mssql_core_columns_text.ntext,
		time: require_mssql_core_columns_time.time,
		tinyint: require_mssql_core_columns_tinyint.tinyint,
		varbinary: require_mssql_core_columns_varbinary.varbinary,
		varchar: require_mssql_core_columns_varchar.varchar,
		nvarchar: require_mssql_core_columns_varchar.nvarchar
	};
}

//#endregion
exports.getMsSqlColumnBuilders = getMsSqlColumnBuilders;
//# sourceMappingURL=all.cjs.map