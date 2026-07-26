import { cockroachTableWithSchema } from "./table.js";
import { QueryBuilder } from "./query-builders/query-builder.js";
import { CockroachViewBase } from "./view-base.js";
import { entityKind, is } from "../entity.js";
import { getTableColumns } from "../utils.js";
import { SelectionProxyHandler } from "../selection-proxy.js";

//#region src/cockroach-core/view.ts
var DefaultViewBuilderCore = class {
	static [entityKind] = "CockroachDefaultViewBuilderCore";
	constructor(name, schema) {
		this.name = name;
		this.schema = schema;
	}
};
var ViewBuilder = class extends DefaultViewBuilderCore {
	static [entityKind] = "CockroachViewBuilder";
	as(qb) {
		if (typeof qb === "function") qb = qb(new QueryBuilder());
		const selectionProxy = new SelectionProxyHandler({
			alias: this.name,
			sqlBehavior: "error",
			sqlAliasedBehavior: "alias",
			replaceOriginalName: true
		});
		const aliasedSelection = new Proxy(qb.getSelectedFields(), selectionProxy);
		return new Proxy(new CockroachView({ config: {
			name: this.name,
			schema: this.schema,
			selectedFields: aliasedSelection,
			query: qb.getSQL().inlineParams()
		} }), selectionProxy);
	}
};
var ManualViewBuilder = class extends DefaultViewBuilderCore {
	static [entityKind] = "CockroachManualViewBuilder";
	columns;
	constructor(name, columns, schema, casing) {
		super(name, schema);
		this.columns = getTableColumns(cockroachTableWithSchema(name, columns, void 0, schema, casing));
	}
	existing() {
		return new Proxy(new CockroachView({ config: {
			name: this.name,
			schema: this.schema,
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
		return new Proxy(new CockroachView({ config: {
			name: this.name,
			schema: this.schema,
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
var MaterializedViewBuilderCore = class {
	static [entityKind] = "CockroachMaterializedViewBuilderCore";
	constructor(name, schema) {
		this.name = name;
		this.schema = schema;
	}
	config = {};
	withNoData() {
		this.config.withNoData = true;
		return this;
	}
};
var MaterializedViewBuilder = class extends MaterializedViewBuilderCore {
	static [entityKind] = "CockroachMaterializedViewBuilder";
	as(qb) {
		if (typeof qb === "function") qb = qb(new QueryBuilder());
		const selectionProxy = new SelectionProxyHandler({
			alias: this.name,
			sqlBehavior: "error",
			sqlAliasedBehavior: "alias",
			replaceOriginalName: true
		});
		const aliasedSelection = new Proxy(qb.getSelectedFields(), selectionProxy);
		return new Proxy(new CockroachMaterializedView({
			cockroachConfig: { withNoData: this.config.withNoData },
			config: {
				name: this.name,
				schema: this.schema,
				selectedFields: aliasedSelection,
				query: qb.getSQL().inlineParams()
			}
		}), selectionProxy);
	}
};
var ManualMaterializedViewBuilder = class extends MaterializedViewBuilderCore {
	static [entityKind] = "CockroachManualMaterializedViewBuilder";
	columns;
	constructor(name, columns, schema, casing) {
		super(name, schema);
		this.columns = getTableColumns(cockroachTableWithSchema(name, columns, void 0, schema, casing));
	}
	existing() {
		return new Proxy(new CockroachMaterializedView({
			cockroachConfig: { withNoData: this.config.withNoData },
			config: {
				name: this.name,
				schema: this.schema,
				selectedFields: this.columns,
				query: void 0
			}
		}), new SelectionProxyHandler({
			alias: this.name,
			sqlBehavior: "error",
			sqlAliasedBehavior: "alias",
			replaceOriginalName: true
		}));
	}
	as(query) {
		return new Proxy(new CockroachMaterializedView({
			cockroachConfig: { withNoData: this.config.withNoData },
			config: {
				name: this.name,
				schema: this.schema,
				selectedFields: this.columns,
				query: query.inlineParams()
			}
		}), new SelectionProxyHandler({
			alias: this.name,
			sqlBehavior: "error",
			sqlAliasedBehavior: "alias",
			replaceOriginalName: true
		}));
	}
};
var CockroachView = class extends CockroachViewBase {
	static [entityKind] = "CockroachView";
	constructor({ config }) {
		super(config);
	}
};
const CockroachMaterializedViewConfig = Symbol.for("drizzle:CockroachMaterializedViewConfig");
var CockroachMaterializedView = class extends CockroachViewBase {
	static [entityKind] = "CockroachMaterializedView";
	[CockroachMaterializedViewConfig];
	constructor({ cockroachConfig, config }) {
		super(config);
		this[CockroachMaterializedViewConfig] = { withNoData: cockroachConfig?.withNoData };
	}
};
/** @internal */
function cockroachViewWithSchema(name, selection, schema, casing) {
	if (selection) return new ManualViewBuilder(name, selection, schema, casing);
	return new ViewBuilder(name, schema);
}
/** @internal */
function cockroachMaterializedViewWithSchema(name, selection, schema, casing) {
	if (selection) return new ManualMaterializedViewBuilder(name, selection, schema, casing);
	return new MaterializedViewBuilder(name, schema);
}
function cockroachViewWithCasing(casing) {
	return ((name, columns) => cockroachViewWithSchema(name, columns, void 0, casing));
}
/** @internal */
function cockroachMaterializedViewWithCasing(casing) {
	return ((name, columns) => cockroachMaterializedViewWithSchema(name, columns, void 0, casing));
}
const cockroachView = cockroachViewWithCasing(void 0);
const cockroachMaterializedView = cockroachMaterializedViewWithCasing(void 0);
function isCockroachView(obj) {
	return is(obj, CockroachView);
}
function isCockroachMaterializedView(obj) {
	return is(obj, CockroachMaterializedView);
}

//#endregion
export { CockroachMaterializedView, CockroachMaterializedViewConfig, CockroachView, DefaultViewBuilderCore, ManualMaterializedViewBuilder, ManualViewBuilder, MaterializedViewBuilder, MaterializedViewBuilderCore, ViewBuilder, cockroachMaterializedView, cockroachMaterializedViewWithCasing, cockroachMaterializedViewWithSchema, cockroachView, cockroachViewWithCasing, cockroachViewWithSchema, isCockroachMaterializedView, isCockroachView };
//# sourceMappingURL=view.js.map