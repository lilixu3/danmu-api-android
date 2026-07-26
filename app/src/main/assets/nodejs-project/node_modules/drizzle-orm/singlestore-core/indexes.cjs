Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");

//#region src/singlestore-core/indexes.ts
var IndexBuilderOn = class {
	static [__entity_ts.entityKind] = "SingleStoreIndexBuilderOn";
	constructor(name, unique) {
		this.name = name;
		this.unique = unique;
	}
	on(...columns) {
		return new IndexBuilder(this.name, columns, this.unique);
	}
};
var IndexBuilder = class {
	static [__entity_ts.entityKind] = "SingleStoreIndexBuilder";
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
	static [__entity_ts.entityKind] = "SingleStoreIndex";
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
/** @internal */
/** @internal */
/** @internal */

//#endregion
exports.Index = Index;
exports.IndexBuilder = IndexBuilder;
exports.IndexBuilderOn = IndexBuilderOn;
exports.index = index;
exports.uniqueIndex = uniqueIndex;
//# sourceMappingURL=indexes.cjs.map