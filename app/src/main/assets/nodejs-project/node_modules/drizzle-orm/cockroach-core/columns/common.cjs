Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_utils_array = require('../utils/array.cjs');
let __entity_ts = require("../../entity.cjs");
let __column_ts = require("../../column.cjs");
let __tracing_utils_ts = require("../../tracing-utils.cjs");
let __column_builder_ts = require("../../column-builder.cjs");
let __cockroach_core_foreign_keys_ts = require("../foreign-keys.cjs");

//#region src/cockroach-core/columns/common.ts
var CockroachColumnBuilder = class extends __column_builder_ts.ColumnBuilder {
	foreignKeyConfigs = [];
	static [__entity_ts.entityKind] = "CockroachColumnBuilder";
	references(ref, config = {}) {
		this.foreignKeyConfigs.push({
			ref,
			config
		});
		return this;
	}
	unique(name) {
		this.config.isUnique = true;
		this.config.uniqueName = name;
		return this;
	}
	generatedAlwaysAs(as) {
		this.config.generated = {
			as,
			type: "always",
			mode: "stored"
		};
		return this;
	}
	/** @internal */
	buildForeignKeys(column, table) {
		return this.foreignKeyConfigs.map(({ ref, config }) => {
			return (0, __tracing_utils_ts.iife)((ref, config) => {
				const builder = new __cockroach_core_foreign_keys_ts.ForeignKeyBuilder(() => {
					const foreignColumn = ref();
					return {
						name: config.name,
						columns: [column],
						foreignColumns: [foreignColumn]
					};
				});
				if (config.onUpdate) builder.onUpdate(config.onUpdate);
				if (config.onDelete) builder.onDelete(config.onDelete);
				return builder.build(table);
			}, ref, config);
		});
	}
	/** @internal */
	buildExtraConfigColumn(table) {
		return new ExtraConfigColumn(table, this.config);
	}
};
var CockroachColumnWithArrayBuilder = class extends CockroachColumnBuilder {
	static [__entity_ts.entityKind] = "CockroachColumnWithArrayBuilder";
	array(size) {
		return new CockroachArrayBuilder(this.config.name, this, size);
	}
};
var CockroachColumn = class extends __column_ts.Column {
	static [__entity_ts.entityKind] = "CockroachColumn";
	/** @internal */
	table;
	constructor(table, config) {
		super(table, config);
		this.table = table;
	}
	/** @internal */
	shouldDisableInsert() {
		return this.config.generatedIdentity !== void 0 && this.config.generatedIdentity.type === "always" || this.config.generated !== void 0 && this.config.generated.type !== "byDefault";
	}
};
var ExtraConfigColumn = class extends CockroachColumn {
	static [__entity_ts.entityKind] = "ExtraConfigColumn";
	getSQLType() {
		return this.getSQLType();
	}
	indexConfig = { order: this.config.order ?? "asc" };
	defaultConfig = { order: "asc" };
	asc() {
		this.indexConfig.order = "asc";
		return this;
	}
	desc() {
		this.indexConfig.order = "desc";
		return this;
	}
};
var IndexedColumn = class {
	static [__entity_ts.entityKind] = "IndexedColumn";
	constructor(name, keyAsName, type, indexConfig) {
		this.name = name;
		this.keyAsName = keyAsName;
		this.type = type;
		this.indexConfig = indexConfig;
	}
	name;
	keyAsName;
	type;
	indexConfig;
};
var CockroachArrayBuilder = class extends CockroachColumnWithArrayBuilder {
	static [__entity_ts.entityKind] = "CockroachArrayBuilder";
	constructor(name, baseBuilder, length) {
		super(name, "array basecolumn", "CockroachArray");
		this.config.baseBuilder = baseBuilder;
		this.config.length = length;
	}
	/** @internal */
	build(table) {
		const baseColumn = this.config.baseBuilder.build(table);
		return new CockroachArray(table, this.config, baseColumn);
	}
};
var CockroachArray = class CockroachArray extends CockroachColumn {
	static [__entity_ts.entityKind] = "CockroachArray";
	constructor(table, config, baseColumn, range) {
		super(table, config);
		this.baseColumn = baseColumn;
		this.range = range;
	}
	getSQLType() {
		return `${this.baseColumn.getSQLType()}[${typeof this.length === "number" ? this.length : ""}]`;
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") value = require_cockroach_core_utils_array.parseCockroachArray(value);
		return value.map((v) => this.baseColumn.mapFromDriverValue(v));
	};
	mapFromJsonValue(value) {
		if (typeof value === "string") value = require_cockroach_core_utils_array.parseCockroachArray(value);
		const base = this.baseColumn;
		return "mapFromJsonValue" in base ? value.map((v) => base.mapFromJsonValue(v)) : value.map((v) => base.mapFromDriverValue(v));
	}
	mapToDriverValue = (value, isNestedArray = false) => {
		const a = value.map((v) => v === null ? null : (0, __entity_ts.is)(this.baseColumn, CockroachArray) ? this.baseColumn.mapToDriverValue(v, true) : this.baseColumn.mapToDriverValue(v));
		if (isNestedArray) return a;
		return require_cockroach_core_utils_array.makeCockroachArray(a);
	};
};

//#endregion
exports.CockroachArray = CockroachArray;
exports.CockroachArrayBuilder = CockroachArrayBuilder;
exports.CockroachColumn = CockroachColumn;
exports.CockroachColumnBuilder = CockroachColumnBuilder;
exports.CockroachColumnWithArrayBuilder = CockroachColumnWithArrayBuilder;
exports.ExtraConfigColumn = ExtraConfigColumn;
exports.IndexedColumn = IndexedColumn;
//# sourceMappingURL=common.cjs.map