Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_sqlite_core_columns_common = require('./common.cjs');
let __entity_ts = require("../../entity.cjs");
let __utils_ts = require("../../utils.cjs");

//#region src/sqlite-core/columns/text.ts
var SQLiteTextBuilder = class extends require_sqlite_core_columns_common.SQLiteColumnBuilder {
	static [__entity_ts.entityKind] = "SQLiteTextBuilder";
	constructor(name, config) {
		super(name, config.enum?.length ? "string enum" : "string", "SQLiteText");
		this.config.enumValues = config.enum;
		this.config.length = config.length;
	}
	/** @internal */
	build(table) {
		return new SQLiteText(table, this.config);
	}
};
var SQLiteText = class extends require_sqlite_core_columns_common.SQLiteColumn {
	static [__entity_ts.entityKind] = "SQLiteText";
	enumValues = this.config.enumValues;
	constructor(table, config) {
		super(table, config);
	}
	getSQLType() {
		return `text${this.config.length ? `(${this.config.length})` : ""}`;
	}
};
var SQLiteTextJsonBuilder = class extends require_sqlite_core_columns_common.SQLiteColumnBuilder {
	static [__entity_ts.entityKind] = "SQLiteTextJsonBuilder";
	constructor(name) {
		super(name, "object json", "SQLiteTextJson");
	}
	/** @internal */
	build(table) {
		return new SQLiteTextJson(table, this.config);
	}
};
var SQLiteTextJson = class extends require_sqlite_core_columns_common.SQLiteColumn {
	static [__entity_ts.entityKind] = "SQLiteTextJson";
	getSQLType() {
		return "text";
	}
	mapFromDriverValue = (value) => {
		return JSON.parse(value);
	};
	mapToDriverValue = (value) => {
		return JSON.stringify(value);
	};
};
function text(a, b = {}) {
	const { name, config } = (0, __utils_ts.getColumnNameAndConfig)(a, b);
	if (config.mode === "json") return new SQLiteTextJsonBuilder(name);
	return new SQLiteTextBuilder(name, config);
}

//#endregion
exports.SQLiteText = SQLiteText;
exports.SQLiteTextBuilder = SQLiteTextBuilder;
exports.SQLiteTextJson = SQLiteTextJson;
exports.SQLiteTextJsonBuilder = SQLiteTextJsonBuilder;
exports.text = text;
//# sourceMappingURL=text.cjs.map