import { mssqlTableWithSchema } from "./table.js";
import { MsSqlViewBase } from "./view-base.js";
import { QueryBuilder } from "./query-builders/query-builder.js";
import { MsSqlViewConfig } from "./view-common.js";
import { entityKind } from "../entity.js";
import { getTableColumns } from "../utils.js";
import { SelectionProxyHandler } from "../selection-proxy.js";

//#region src/mssql-core/view.ts
var ViewBuilderCore = class {
	static [entityKind] = "MsSqlViewBuilder";
	constructor(name, schema) {
		this.name = name;
		this.schema = schema;
	}
	config = {
		encryption: false,
		schemaBinding: false,
		viewMetadata: false
	};
	with(config) {
		this.config.encryption = config?.encryption;
		this.config.schemaBinding = config?.schemaBinding;
		this.config.viewMetadata = config?.viewMetadata;
		this.config.checkOption = config?.checkOption;
		return this;
	}
};
var ViewBuilder = class extends ViewBuilderCore {
	static [entityKind] = "MsSqlViewBuilder";
	as(qb) {
		if (typeof qb === "function") qb = qb(new QueryBuilder());
		const selectionProxy = new SelectionProxyHandler({
			alias: this.name,
			sqlBehavior: "error",
			sqlAliasedBehavior: "alias",
			replaceOriginalName: true
		});
		const aliasedSelection = new Proxy(qb.getSelectedFields(), selectionProxy);
		return new Proxy(new MsSqlView({
			mssqlConfig: this.config,
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
	static [entityKind] = "MsSqlManualViewBuilder";
	columns;
	constructor(name, columns, schema, casing) {
		super(name, schema);
		this.columns = getTableColumns(mssqlTableWithSchema(name, columns, void 0, schema, casing));
	}
	existing() {
		return new Proxy(new MsSqlView({
			mssqlConfig: void 0,
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
		return new Proxy(new MsSqlView({
			mssqlConfig: this.config,
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
var MsSqlView = class extends MsSqlViewBase {
	static [entityKind] = "MsSqlView";
	[MsSqlViewConfig];
	constructor({ mssqlConfig, config }) {
		super(config);
		this[MsSqlViewConfig] = mssqlConfig;
	}
};
/** @internal */
function mssqlViewWithSchema(name, selection, schema, casing) {
	if (selection) return new ManualViewBuilder(name, selection, schema, casing);
	return new ViewBuilder(name, schema);
}
/** @internal */
function mssqlViewWithCasing(casing) {
	return ((name, columns) => mssqlViewWithSchema(name, columns, void 0, casing));
}
const mssqlView = mssqlViewWithCasing(void 0);

//#endregion
export { ManualViewBuilder, MsSqlView, ViewBuilder, ViewBuilderCore, mssqlView, mssqlViewWithCasing, mssqlViewWithSchema };
//# sourceMappingURL=view.js.map