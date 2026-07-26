import { entityKind } from "../entity.js";

//#region src/mysql-core/indexes.ts
var IndexBuilderOn = class {
	static [entityKind] = "MySqlIndexBuilderOn";
	constructor(name, unique) {
		this.name = name;
		this.unique = unique;
	}
	on(...columns) {
		return new IndexBuilder(this.name, columns, this.unique);
	}
};
var IndexBuilder = class {
	static [entityKind] = "MySqlIndexBuilder";
	/** @internal */
	config;
	constructor(name, columns, unique) {
		this.config = {
			name,
			columns,
			unique
		};
	}
	using(using) {
		this.config.using = using;
		return this;
	}
	algorithm(algorithm) {
		this.config.algorithm = algorithm;
		return this;
	}
	lock(lock) {
		this.config.lock = lock;
		return this;
	}
	/** @internal */
	build(table) {
		return new Index(this.config, table);
	}
};
var Index = class {
	static [entityKind] = "MySqlIndex";
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