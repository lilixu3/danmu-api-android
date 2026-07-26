import { makeCockroachArray, parseCockroachArray } from "../utils/array.js";
import { entityKind, is } from "../../entity.js";
import { Column } from "../../column.js";
import { iife } from "../../tracing-utils.js";
import { ColumnBuilder } from "../../column-builder.js";
import { ForeignKeyBuilder } from "../foreign-keys.js";

//#region src/cockroach-core/columns/common.ts
var CockroachColumnBuilder = class extends ColumnBuilder {
	foreignKeyConfigs = [];
	static [entityKind] = "CockroachColumnBuilder";
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
			return iife((ref, config) => {
				const builder = new ForeignKeyBuilder(() => {
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
	static [entityKind] = "CockroachColumnWithArrayBuilder";
	array(size) {
		return new CockroachArrayBuilder(this.config.name, this, size);
	}
};
var CockroachColumn = class extends Column {
	static [entityKind] = "CockroachColumn";
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
	static [entityKind] = "ExtraConfigColumn";
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
	static [entityKind] = "IndexedColumn";
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
	static [entityKind] = "CockroachArrayBuilder";
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
	static [entityKind] = "CockroachArray";
	constructor(table, config, baseColumn, range) {
		super(table, config);
		this.baseColumn = baseColumn;
		this.range = range;
	}
	getSQLType() {
		return `${this.baseColumn.getSQLType()}[${typeof this.length === "number" ? this.length : ""}]`;
	}
	mapFromDriverValue = (value) => {
		if (typeof value === "string") value = parseCockroachArray(value);
		return value.map((v) => this.baseColumn.mapFromDriverValue(v));
	};
	mapFromJsonValue(value) {
		if (typeof value === "string") value = parseCockroachArray(value);
		const base = this.baseColumn;
		return "mapFromJsonValue" in base ? value.map((v) => base.mapFromJsonValue(v)) : value.map((v) => base.mapFromDriverValue(v));
	}
	mapToDriverValue = (value, isNestedArray = false) => {
		const a = value.map((v) => v === null ? null : is(this.baseColumn, CockroachArray) ? this.baseColumn.mapToDriverValue(v, true) : this.baseColumn.mapToDriverValue(v));
		if (isNestedArray) return a;
		return makeCockroachArray(a);
	};
};

//#endregion
export { CockroachArray, CockroachArrayBuilder, CockroachColumn, CockroachColumnBuilder, CockroachColumnWithArrayBuilder, ExtraConfigColumn, IndexedColumn };
//# sourceMappingURL=common.js.map