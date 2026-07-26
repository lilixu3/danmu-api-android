import { singlestoreTable } from "./table.js";
import { SingleStoreViewBase } from "./view-base.js";
import { QueryBuilder } from "./query-builders/query-builder.js";
import { SingleStoreViewConfig } from "./view-common.js";
import { entityKind } from "../entity.js";
import { getTableColumns } from "../utils.js";
import { SelectionProxyHandler } from "../selection-proxy.js";

//#region src/singlestore-core/view.ts
var ViewBuilderCore = class {
	static [entityKind] = "SingleStoreViewBuilder";
	constructor(name, schema) {
		this.name = name;
		this.schema = schema;
	}
	config = {};
	algorithm(algorithm) {
		this.config.algorithm = algorithm;
		return this;
	}
	definer(definer) {
		this.config.definer = definer;
		return this;
	}
	sqlSecurity(sqlSecurity) {
		this.config.sqlSecurity = sqlSecurity;
		return this;
	}
	withCheckOption(withCheckOption) {
		this.config.withCheckOption = withCheckOption ?? "cascaded";
		return this;
	}
};
var ViewBuilder = class extends ViewBuilderCore {
	static [entityKind] = "SingleStoreViewBuilder";
	as(qb) {
		if (typeof qb === "function") qb = qb(new QueryBuilder());
		const selectionProxy = new SelectionProxyHandler({
			alias: this.name,
			sqlBehavior: "error",
			sqlAliasedBehavior: "alias",
			replaceOriginalName: true
		});
		const aliasedSelection = new Proxy(qb.getSelectedFields(), selectionProxy);
		return new Proxy(new SingleStoreView({
			singlestoreConfig: this.config,
			config: {
				name: this.name,
				schema: this.schema,
				selectedFields: aliasedSelection,
				query: qb.getSQL().inlineParams()
			}
		}), selectionProxy);
	}
};
var ManualViewBuilder = class extends ViewBuilderCore {
	static [entityKind] = "SingleStoreManualViewBuilder";
	columns;
	constructor(name, columns, schema) {
		super(name, schema);
		this.columns = getTableColumns(singlestoreTable(name, columns));
	}
	existing() {
		return new Proxy(new SingleStoreView({
			singlestoreConfig: void 0,
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
		return new Proxy(new SingleStoreView({
			singlestoreConfig: this.config,
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
var SingleStoreView = class extends SingleStoreViewBase {
	static [entityKind] = "SingleStoreView";
	[SingleStoreViewConfig];
	constructor({ singlestoreConfig, config }) {
		super(config);
		this[SingleStoreViewConfig] = singlestoreConfig;
	}
};

//#endregion
export { ManualViewBuilder, SingleStoreView, ViewBuilder, ViewBuilderCore };
//# sourceMappingURL=view.js.map