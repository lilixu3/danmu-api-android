import { sqliteTableBase } from "./table.js";
import { SQLiteViewBase } from "./view-base.js";
import { QueryBuilder } from "./query-builders/query-builder.js";
import { entityKind } from "../entity.js";
import { getTableColumns } from "../utils.js";
import { SelectionProxyHandler } from "../selection-proxy.js";

//#region src/sqlite-core/view.ts
var ViewBuilderCore = class {
	static [entityKind] = "SQLiteViewBuilderCore";
	constructor(name) {
		this.name = name;
	}
	config = {};
};
var ViewBuilder = class extends ViewBuilderCore {
	static [entityKind] = "SQLiteViewBuilder";
	as(qb) {
		if (typeof qb === "function") qb = qb(new QueryBuilder());
		const selectionProxy = new SelectionProxyHandler({
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
	static [entityKind] = "SQLiteManualViewBuilder";
	columns;
	constructor(name, columns, casing) {
		super(name);
		this.columns = getTableColumns(sqliteTableBase(name, columns, void 0, void 0, casing));
	}
	existing() {
		return new Proxy(new SQLiteView({ config: {
			name: this.name,
			schema: void 0,
			selectedFields: this.columns,
			query: void 0
		} }), new SelectionProxyHandler({
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
		} }), new SelectionProxyHandler({
			alias: this.name,
			sqlBehavior: "error",
			sqlAliasedBehavior: "alias",
			replaceOriginalName: true
		}));
	}
};
var SQLiteView = class extends SQLiteViewBase {
	static [entityKind] = "SQLiteView";
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
export { ManualViewBuilder, SQLiteView, ViewBuilder, ViewBuilderCore, sqliteView, sqliteViewWithCasing, view };
//# sourceMappingURL=view.js.map