Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_cockroach_core_columns_common = require('./columns/common.cjs');
let __entity_ts = require("../entity.cjs");
let __sql_sql_ts = require("../sql/sql.cjs");

//#region src/cockroach-core/indexes.ts
var IndexBuilderOn = class {
	static [__entity_ts.entityKind] = "CockroachIndexBuilderOn";
	constructor(unique, name) {
		this.unique = unique;
		this.name = name;
	}
	on(...columns) {
		return new IndexBuilder(columns.map((it) => {
			if ((0, __entity_ts.is)(it, __sql_sql_ts.SQL)) return it;
			if ((0, __entity_ts.is)(it, require_cockroach_core_columns_common.ExtraConfigColumn)) {
				const clonedIndexedColumn = new require_cockroach_core_columns_common.IndexedColumn(it.name, !!it.keyAsName, it.columnType, it.indexConfig);
				it.indexConfig = JSON.parse(JSON.stringify(it.defaultConfig));
				return clonedIndexedColumn;
			}
			it = it;
			return new require_cockroach_core_columns_common.IndexedColumn(it.name, !!it.keyAsName, it.columnType, {});
		}), this.unique, false, this.name);
	}
	onOnly(...columns) {
		return new IndexBuilder(columns.map((it) => {
			if ((0, __entity_ts.is)(it, __sql_sql_ts.SQL)) return it;
			if ((0, __entity_ts.is)(it, require_cockroach_core_columns_common.ExtraConfigColumn)) {
				const clonedIndexedColumn = new require_cockroach_core_columns_common.IndexedColumn(it.name, !!it.keyAsName, it.columnType, it.indexConfig);
				it.indexConfig = JSON.parse(JSON.stringify(it.defaultConfig));
				return clonedIndexedColumn;
			}
			it = it;
			return new require_cockroach_core_columns_common.IndexedColumn(it.name, !!it.keyAsName, it.columnType, {});
		}), this.unique, true, this.name);
	}
	/**
	* Specify what index method to use. Choices are `btree`, `hash`, `gin`, `cspann`. The default method is `btree`.
	*
	* @param method The name of the index method to be used
	* @param columns
	* @returns
	*/
	using(method, ...columns) {
		return new IndexBuilder(columns.map((it) => {
			if ((0, __entity_ts.is)(it, __sql_sql_ts.SQL)) return it;
			if ((0, __entity_ts.is)(it, require_cockroach_core_columns_common.ExtraConfigColumn)) {
				const clonedIndexedColumn = new require_cockroach_core_columns_common.IndexedColumn(it.name, !!it.keyAsName, it.columnType, it.indexConfig);
				it.indexConfig = JSON.parse(JSON.stringify(it.defaultConfig));
				return clonedIndexedColumn;
			}
			it = it;
			return new require_cockroach_core_columns_common.IndexedColumn(it.name, !!it.keyAsName, it.columnType, {});
		}), this.unique, true, this.name, method);
	}
};
var IndexBuilder = class {
	static [__entity_ts.entityKind] = "CockroachIndexBuilder";
	/** @internal */
	config;
	constructor(columns, unique, only, name, method = "btree") {
		this.config = {
			name,
			columns,
			unique,
			only,
			method
		};
	}
	where(condition) {
		this.config.where = condition;
		return this;
	}
	/** @internal */
	build(table) {
		return new Index(this.config, table);
	}
};
var Index = class {
	static [__entity_ts.entityKind] = "CockroachIndex";
	config;
	isNameExplicit;
	constructor(config, table) {
		this.config = {
			...config,
			table
		};
		this.isNameExplicit = !!config.name;
	}
};
function index(name) {
	return new IndexBuilderOn(false, name);
}
function uniqueIndex(name) {
	return new IndexBuilderOn(true, name);
}

//#endregion
exports.Index = Index;
exports.IndexBuilder = IndexBuilder;
exports.IndexBuilderOn = IndexBuilderOn;
exports.index = index;
exports.uniqueIndex = uniqueIndex;
//# sourceMappingURL=indexes.cjs.map