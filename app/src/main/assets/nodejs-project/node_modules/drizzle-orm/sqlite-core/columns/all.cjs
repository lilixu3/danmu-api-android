Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_sqlite_core_columns_blob = require('./blob.cjs');
const require_sqlite_core_columns_custom = require('./custom.cjs');
const require_sqlite_core_columns_integer = require('./integer.cjs');
const require_sqlite_core_columns_numeric = require('./numeric.cjs');
const require_sqlite_core_columns_real = require('./real.cjs');
const require_sqlite_core_columns_text = require('./text.cjs');

//#region src/sqlite-core/columns/all.ts
function getSQLiteColumnBuilders() {
	return {
		blob: require_sqlite_core_columns_blob.blob,
		customType: require_sqlite_core_columns_custom.customType,
		integer: require_sqlite_core_columns_integer.integer,
		numeric: require_sqlite_core_columns_numeric.numeric,
		real: require_sqlite_core_columns_real.real,
		text: require_sqlite_core_columns_text.text
	};
}

//#endregion
exports.getSQLiteColumnBuilders = getSQLiteColumnBuilders;
//# sourceMappingURL=all.cjs.map