import { ExtraConfigColumn, IndexedColumn } from "./columns/common.js";
import { entityKind, is } from "../entity.js";
import { SQL } from "../sql/sql.js";

//#region src/cockroach-core/indexes.ts
var IndexBuilderOn = class {
	static [entityKind] = "CockroachIndexBuilderOn";
	constructor(unique, name) {
		this.unique = unique;
		this.name = name;
	}
	on(...columns) {
		return new IndexBuilder(columns.map((it) => {
			if (is(it, SQL)) return it;
			if (is(it, ExtraConfigColumn)) {
				const clonedIndexedColumn = new IndexedColumn(it.name, !!it.keyAsName, it.columnType, it.indexConfig);
				it.indexConfig = JSON.parse(JSON.stringify(it.defaultConfig));
				return clonedIndexedColumn;
			}
			it = it;
			return new IndexedColumn(it.name, !!it.keyAsName, it.columnType, {});
		}), this.unique, false, this.name);
	}
	onOnly(...columns) {
		return new IndexBuilder(columns.map((it) => {
			if (is(it, SQL)) return it;
			if (is(it, ExtraConfigColumn)) {
				const clonedIndexedColumn = new IndexedColumn(it.name, !!it.keyAsName, it.columnType, it.indexConfig);
				it.indexConfig = JSON.parse(JSON.stringify(it.defaultConfig));
				return clonedIndexedColumn;
			}
			it = it;
			return new IndexedColumn(it.name, !!it.keyAsName, it.columnType, {});
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
			if (is(it, SQL)) return it;
			if (is(it, ExtraConfigColumn)) {
				const clonedIndexedColumn = new IndexedColumn(it.name, !!it.keyAsName, it.columnType, it.indexConfig);
				it.indexConfig = JSON.parse(JSON.stringify(it.defaultConfig));
				return clonedIndexedColumn;
			}
			it = it;
			return new IndexedColumn(it.name, !!it.keyAsName, it.columnType, {});
		}), this.unique, true, this.name, method);
	}
};
var IndexBuilder = class {
	static [entityKind] = "CockroachIndexBuilder";
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
	static [entityKind] = "CockroachIndex";
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
export { Index, IndexBuilder, IndexBuilderOn, index, uniqueIndex };
//# sourceMappingURL=indexes.js.map