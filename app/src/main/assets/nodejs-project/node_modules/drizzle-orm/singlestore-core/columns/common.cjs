Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../../entity.cjs");
let __column_ts = require("../../column.cjs");
let __column_builder_ts = require("../../column-builder.cjs");

//#region src/singlestore-core/columns/common.ts
var SingleStoreColumnBuilder = class extends __column_builder_ts.ColumnBuilder {
	static [__entity_ts.entityKind] = "SingleStoreColumnBuilder";
	unique(name) {
		this.config.isUnique = true;
		this.config.uniqueName = name;
		return this;
	}
	generatedAlwaysAs(as, config) {
		this.config.generated = {
			as,
			type: "always",
			mode: config?.mode ?? "virtual"
		};
		return this;
	}
};
var SingleStoreColumn = class extends __column_ts.Column {
	static [__entity_ts.entityKind] = "SingleStoreColumn";
	/** @internal */
	table;
	constructor(table, config) {
		super(table, config);
		this.table = table;
	}
};
var SingleStoreColumnBuilderWithAutoIncrement = class extends SingleStoreColumnBuilder {
	static [__entity_ts.entityKind] = "SingleStoreColumnBuilderWithAutoIncrement";
	constructor(name, dataType, columnType) {
		super(name, dataType, columnType);
		this.config.autoIncrement = false;
	}
	autoincrement() {
		this.config.autoIncrement = true;
		this.config.hasDefault = true;
		return this;
	}
};
var SingleStoreColumnWithAutoIncrement = class extends SingleStoreColumn {
	static [__entity_ts.entityKind] = "SingleStoreColumnWithAutoIncrement";
	autoIncrement = this.config.autoIncrement;
};

//#endregion
exports.SingleStoreColumn = SingleStoreColumn;
exports.SingleStoreColumnBuilder = SingleStoreColumnBuilder;
exports.SingleStoreColumnBuilderWithAutoIncrement = SingleStoreColumnBuilderWithAutoIncrement;
exports.SingleStoreColumnWithAutoIncrement = SingleStoreColumnWithAutoIncrement;
//# sourceMappingURL=common.cjs.map