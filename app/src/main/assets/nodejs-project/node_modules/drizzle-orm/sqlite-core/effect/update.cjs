Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
const require_sqlite_core_utils = require('../utils.cjs');
let __entity_ts = require("../../entity.cjs");
let __effect_core_query_effect_ts = require("../../effect-core/query-effect.cjs");
let __sqlite_core_query_builders_update_ts = require("../query-builders/update.cjs");

//#region src/sqlite-core/effect/update.ts
var SQLiteEffectUpdateBase = class extends __sqlite_core_query_builders_update_ts.SQLiteUpdateBase {
	static [__entity_ts.entityKind] = "SQLiteEffectUpdate";
	/** @internal */
	_prepare(prepare = false) {
		return this.session.prepareQuery(this.dialect.sqlToQuery(this.getSQL()), "arrays", prepare, this.config.returning ? "all" : "run", this.config.returning ? this.dialect.mapperGenerators.rows(this.config.returning, void 0) : void 0, {
			type: "update",
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
(0, __effect_core_query_effect_ts.applyEffectWrapper)(SQLiteEffectUpdateBase);

//#endregion
exports.SQLiteEffectUpdateBase = SQLiteEffectUpdateBase;
//# sourceMappingURL=update.cjs.map