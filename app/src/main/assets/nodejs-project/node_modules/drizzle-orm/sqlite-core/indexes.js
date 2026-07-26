import { entityKind } from "../entity.js";

//#region src/sqlite-core/indexes.ts
var IndexBuilderOn = class {
	static [entityKind] = "SQLiteIndexBuilderOn";
	constructor(name, unique) {
		this.name = name;
		this.unique = unique;
	}
	on(...columns) {
		return new IndexBuilder(this.name, columns, this.unique);
	}
};
var IndexBuilder = class {
	static [entityKind] = "SQLiteIndexBuilder";
	/** @internal */
	config;
	constructor(name, columns, unique) {
		this.config = {
			name,
			columns,
			unique,
			where: void 0
		};
	}
	/**
	* Condition for partial index.
	*/
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
	static [entityKind] = "SQLiteIndex";
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
	return new IndexBuilderOn(name, false);
}
function uniqueIndex(name) {
	return new IndexBuilderOn(name, true);
}

//#endregion
export { Index, IndexBuilder, IndexBuilderOn, index, uniqueIndex };
//# sourceMappingURL=indexes.js.map