Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_sqlite_core_utils = require('../utils.cjs');
let __entity_ts = require("../../entity.cjs");
let __effect_core_query_effect_ts = require("../../effect-core/query-effect.cjs");
let __sqlite_core_query_builders_delete_ts = require("../query-builders/delete.cjs");

//#region src/sqlite-core/effect/delete.ts
var SQLiteEffectDeleteBase = class extends __sqlite_core_query_builders_delete_ts.SQLiteDeleteBase {
	static [__entity_ts.entityKind] = "SQLiteEffectDelete";
	/** @internal */
	_prepare(prepare = false) {
		return this.session.prepareQuery(this.dialect.sqlToQuery(this.getSQL()), "arrays", prepare, this.config.returning ? "all" : "run", this.config.returning ? this.dialect.mapperGenerators.rows(this.config.returning, void 0) : void 0, {
			type: "delete",
			tables: require_sqlite_core_utils.extractUsedTable(this.config.table)
		});
	}
	prepare() {
		return this._prepare(true);
	}
	run = (placeholderValues) => {
		return this._prepare().run(placeholderValues);
	};
	all = (placeholderValues) => {
		return this._prepare().all(placeholderValues);
	};
	get = (placeholderValues) => {
		return this._prepare().get(placeholderValues);
	};
	values = (placeholderValues) => {
		return this._prepare().values(placeholderValues);
	};
	execute = (placeholderValues) => {
		return this._prepare().execute(placeholderValues);
	};
};
(0, __effect_core_query_effect_ts.applyEffectWrapper)(SQLiteEffectDeleteBase);

//#endregion
exports.SQLiteEffectDeleteBase = SQLiteEffectDeleteBase;
//# sourceMappingURL=delete.cjs.map