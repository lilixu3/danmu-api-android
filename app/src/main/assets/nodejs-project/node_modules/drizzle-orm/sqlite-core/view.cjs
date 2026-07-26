Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_sqlite_core_table = require('./table.cjs');
const require_sqlite_core_view_base = require('./view-base.cjs');
const require_sqlite_core_query_builders_query_builder = require('./query-builders/query-builder.cjs');
let __entity_ts = require("../entity.cjs");
let __utils_ts = require("../utils.cjs");
let __selection_proxy_ts = require("../selection-proxy.cjs");

//#region src/sqlite-core/view.ts
var ViewBuilderCore = class {
	static [__entity_ts.entityKind] = "SQLiteViewBuilderCore";
	constructor(name) {
		this.name = name;
	}
	config = {};
};
var ViewBuilder = class extends ViewBuilderCore {
	static [__entity_ts.entityKind] = "SQLiteViewBuilder";
	as(qb) {
		if (typeof qb === "function") qb = qb(new require_sqlite_core_query_builders_query_builder.QueryBuilder());
		const selectionProxy = new __selection_proxy_ts.SelectionProxyHandler({
			alias: this.name,
			sqlBehavior: "error",
			sqlAliasedBehavior: "alias",
			replaceOriginalName: true
		});
		const aliasedSelectedFields = qb.getSelectedFields();
		return new Proxy(new SQLiteView({ config: {
			name: this.name,
			schema: void 0,
			selectedFields: aliasedSelectedFields,
			query: qb.getSQL().inlineParams()
		} }), selectionProxy);
	}
};
var ManualViewBuilder = class extends ViewBuilderCore {
	static [__entity_ts.entityKind] = "SQLiteManualViewBuilder";
	columns;
	constructor(name, columns, casing) {
		super(name);
		this.columns = (0, __utils_ts.getTableColumns)(require_sqlite_core_table.sqliteTableBase(name, columns, void 0, void 0, casing));
	}
	existing() {
		return new Proxy(new SQLiteView({ config: {
			name: this.name,
			schema: void 0,
			selectedFields: this.columns,
			query: void 0
		} }), new __selection_proxy_ts.SelectionProxyHandler({
			alias: this.name,
			sqlBehavior: "error",
			sqlAliasedBehavior: "alias",
			replaceOriginalName: true
		}));
	}
	as(query) {
		return new Proxy(new SQLiteView({ config: {
			name: this.name,
			schema: void 0,
			selectedFields: this.columns,
			query: query.inlineParams()
		} }), new __selection_proxy_ts.SelectionProxyHandler({
			alias: this.name,
			sqlBehavior: "error",
			sqlAliasedBehavior: "alias",
			replaceOriginalName: true
		}));
	}
};
var SQLiteView = class extends require_sqlite_core_view_base.SQLiteViewBase {
	static [__entity_ts.entityKind] = "SQLiteView";
	constructor({ config }) {
		super(config);
	}
};
/** @internal */
function sqliteViewWithCasing(casing) {
	return ((name, columns) => {
		if (columns) return new ManualViewBuilder(name, columns, casing);
		return new ViewBuilder(name);
	});
}
const sqliteView = sqliteViewWithCasing(void 0);
const view = sqliteView;

//#endregion
exports.ManualViewBuilder = ManualViewBuilder;
exports.SQLiteView = SQLiteView;
exports.ViewBuilder = ViewBuilder;
exports.ViewBuilderCore = ViewBuilderCore;
exports.sqliteView = sqliteView;
exports.sqliteViewWithCasing = sqliteViewWithCasing;
exports.view = view;
//# sourceMappingURL=view.cjs.map