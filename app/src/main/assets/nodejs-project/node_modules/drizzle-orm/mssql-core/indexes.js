import { entityKind } from "../entity.js";

//#region src/mssql-core/indexes.ts
var IndexBuilderOn = class {
	static [entityKind] = "MsSqlIndexBuilderOn";
	constructor(name, unique) {
		this.name = name;
		this.unique = unique;
	}
	on(...columns) {
		return new IndexBuilder(this.name, columns, this.unique);
	}
};
var IndexBuilder = class {
	static [entityKind] = "MsSqlIndexBuilder";
	/** @internal */
	config;
	constructor(name, columns, unique) {
		this.config = {
			name,
			columns,
			unique
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
	static [entityKind] = "MsSqlIndex";
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