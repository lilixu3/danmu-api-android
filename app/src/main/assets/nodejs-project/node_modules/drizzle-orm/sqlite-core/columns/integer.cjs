Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_sqlite_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");
let __sql_sql_ts = require("../../sql/sql.cjs");

//#region src/sqlite-core/columns/integer.ts
var SQLiteBaseIntegerBuilder = class extends require_sqlite_core_columns_common.SQLiteColumnBuilder {
	static [__entity_ts.entityKind] = "SQLiteBaseIntegerBuilder";
	constructor(name, dataType, columnType) {
		super(name, dataType, columnType);
		this.config.autoIncrement = false;
	}
	primaryKey(config) {
		if (config?.autoIncrement) this.config.autoIncrement = true;
		this.config.hasDefault = true;
		return super.primaryKey();
	}
};
var SQLiteBaseInteger = class extends require_sqlite_core_columns_common.SQLiteColumn {
	static [__entity_ts.entityKind] = "SQLiteBaseInteger";
	autoIncrement = this.config.autoIncrement;
	getSQLType() {
		return "integer";
	}
};
var SQLiteIntegerBuilder = class extends SQLiteBaseIntegerBuilder {
	static [__entity_ts.entityKind] = "SQLiteIntegerBuilder";
	constructor(name) {
		super(name, "number int53", "SQLiteInteger");
	}
	build(table) {
		return new SQLiteInteger(table, this.config);
	}
};
var SQLiteInteger = class extends SQLiteBaseInteger {
	static [__entity_ts.entityKind] = "SQLiteInteger";
};
var SQLiteTimestampBuilder = class extends SQLiteBaseIntegerBuilder {
	static [__entity_ts.entityKind] = "SQLiteTimestampBuilder";
	constructor(name, mode) {
		super(name, "object date", "SQLiteTimestamp");
		this.config.mode = mode;
	}
	/**
	* @deprecated Use `default()` with your own expression instead.
	*
	* Adds `DEFAULT (cast((julianday('now') - 2440587.5)*86400000 as integer))` to the column, which is the current epoch timestamp in milliseconds.
	*/
	defaultNow() {
		return this.default(__sql_sql_ts.sql`(cast((julianday('now') - 2440587.5)*86400000 as integer))`);
	}
	build(table) {
		return new SQLiteTimestamp(table, this.config);
	}
};
var SQLiteTimestamp = class extends SQLiteBaseInteger {
	static [__entity_ts.entityKind] = "SQLiteTimestamp";
	mode = this.config.mode;
	mapFromDriverValue = (value) => {
		if (typeof value === "string") return new Date(value.replaceAll("\"", ""));
		if (this.config.mode === "timestamp") return /* @__PURE__ */ new Date(value * 1e3);
		return new Date(value);
	};
	mapToDriverValue = (value) => {
		if (typeof value === "number") return value;
		const unix = value.getTime();
		if (this.config.mode === "timestamp") return Math.floor(unix / 1e3);
		return unix;
	};
};
var SQLiteBooleanBuilder = class extends SQLiteBaseIntegerBuilder {
	static [__entity_ts.entityKind] = "SQLiteBooleanBuilder";
	constructor(name, mode) {
		super(name, "boolean", "SQLiteBoolean");
		this.config.mode = mode;
	}
	build(table) {
		return new SQLiteBoolean(table, this.config);
	}
};
var SQLiteBoolean = class extends SQLiteBaseInteger {
	static [__entity_ts.entityKind] = "SQLiteBoolean";
	mode = this.config.mode;
	mapFromDriverValue = (value) => {
		return Number(value) === 1;
	};
	mapToDriverValue = (value) => {
		return value ? 1 : 0;
	};
};
function integer(a, b) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	if (config?.mode === "timestamp" || config?.mode === "timestamp_ms") return new SQLiteTimestampBuilder(name, config.mode);
	if (config?.mode === "boolean") return new SQLiteBooleanBuilder(name, config.mode);
	return new SQLiteIntegerBuilder(name);
}
const int = integer;

//#endregion
exports.SQLiteBaseInteger = SQLiteBaseInteger;
exports.SQLiteBaseIntegerBuilder = SQLiteBaseIntegerBuilder;
exports.SQLiteBoolean = SQLiteBoolean;
exports.SQLiteBooleanBuilder = SQLiteBooleanBuilder;
exports.SQLiteInteger = SQLiteInteger;
exports.SQLiteIntegerBuilder = SQLiteIntegerBuilder;
exports.SQLiteTimestamp = SQLiteTimestamp;
exports.SQLiteTimestampBuilder = SQLiteTimestampBuilder;
exports.int = int;
exports.integer = integer;
//# sourceMappingURL=integer.cjs.map