import { pgTableWithSchema } from "./table.js";
import { PgMaterializedViewConfig, PgViewConfig } from "./view-common.js";
import { QueryBuilder } from "./query-builders/query-builder.js";
import { PgViewBase } from "./view-base.js";
import { entityKind, is } from "../entity.js";
import { getTableColumns } from "../utils.js";
import { SelectionProxyHandler } from "../selection-proxy.js";

//#region src/pg-core/view.ts
var DefaultViewBuilderCore = class {
	static [entityKind] = "PgDefaultViewBuilderCore";
	constructor(name, schema) {
		this.name = name;
		this.schema = schema;
	}
	config = {};
	with(config) {
		this.config.with = config;
		return this;
	}
};
var ViewBuilder = class extends DefaultViewBuilderCore {
	static [entityKind] = "PgViewBuilder";
	as(qb) {
		if (typeof qb === "function") qb = qb(new QueryBuilder());
		const selectionProxy = new SelectionProxyHandler({
			alias: this.name,
			sqlBehavior: "error",
			sqlAliasedBehavior: "alias",
			replaceOriginalName: true
		});
		const aliasedSelection = new Proxy(qb.getSelectedFields(), selectionProxy);
		return new Proxy(new PgView({
			pgConfig: this.config,
			config: {
				name: this.name,
				schema: this.schema,
				selectedFields: aliasedSelection,
				query: qb.withoutSelectionCastCodecs().getSQL().inlineParams()
			}
		}), selectionProxy);
	}
};
var ManualViewBuilder = class extends DefaultViewBuilderCore {
	static [entityKind] = "PgManualViewBuilder";
	columns;
	constructor(name, columns, schema, casing) {
		super(name, schema);
		this.columns = getTableColumns(pgTableWithSchema(name, columns, void 0, schema, casing));
	}
	existing() {
		return new Proxy(new PgView({
			pgConfig: void 0,
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
		return new Proxy(new PgView({
			pgConfig: this.config,
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
var MaterializedViewBuilderCore = class {
	static [entityKind] = "PgMaterializedViewBuilderCore";
	constructor(name, schema) {
		this.name = name;
		this.schema = schema;
	}
	config = {};
	using(using) {
		this.config.using = using;
		return this;
	}
	with(config) {
		this.config.with = config;
		return this;
	}
	tablespace(tablespace) {
		this.config.tablespace = tablespace;
		return this;
	}
	withNoData() {
		this.config.withNoData = true;
		return this;
	}
};
var MaterializedViewBuilder = class extends MaterializedViewBuilderCore {
	static [entityKind] = "PgMaterializedViewBuilder";
	as(qb) {
		if (typeof qb === "function") qb = qb(new QueryBuilder());
		const selectionProxy = new SelectionProxyHandler({
			alias: this.name,
			sqlBehavior: "error",
			sqlAliasedBehavior: "alias",
			replaceOriginalName: true
		});
		const aliasedSelection = new Proxy(qb.getSelectedFields(), selectionProxy);
		return new Proxy(new PgMaterializedView({
			pgConfig: {
				with: this.config.with,
				using: this.config.using,
				tablespace: this.config.tablespace,
				withNoData: this.config.withNoData
			},
			config: {
				name: this.name,
				schema: this.schema,
				selectedFields: aliasedSelection,
				query: qb.withoutSelectionCastCodecs().getSQL().inlineParams()
			}
		}), selectionProxy);
	}
};
var ManualMaterializedViewBuilder = class extends MaterializedViewBuilderCore {
	static [entityKind] = "PgManualMaterializedViewBuilder";
	columns;
	constructor(name, columns, schema, casing) {
		super(name, schema);
		this.columns = getTableColumns(pgTableWithSchema(name, columns, void 0, schema, casing));
	}
	existing() {
		return new Proxy(new PgMaterializedView({
			pgConfig: {
				tablespace: this.config.tablespace,
				using: this.config.using,
				with: this.config.with,
				withNoData: this.config.withNoData
			},
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
		return new Proxy(new PgMaterializedView({
			pgConfig: {
				tablespace: this.config.tablespace,
				using: this.config.using,
				with: this.config.with,
				withNoData: this.config.withNoData
			},
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
var PgView = class extends PgViewBase {
	static [entityKind] = "PgView";
	[PgViewConfig];
	constructor({ pgConfig, config }) {
		super(config);
		if (pgConfig) this[PgViewConfig] = { with: pgConfig.with };
	}
};
var PgMaterializedView = class extends PgViewBase {
	static [entityKind] = "PgMaterializedView";
	[PgMaterializedViewConfig];
	constructor({ pgConfig, config }) {
		super(config);
		this[PgMaterializedViewConfig] = {
			with: pgConfig?.with,
			using: pgConfig?.using,
			tablespace: pgConfig?.tablespace,
			withNoData: pgConfig?.withNoData
		};
	}
};
/** @internal */
function pgViewWithSchema(name, selection, schema, casing) {
	if (selection) return new ManualViewBuilder(name, selection, schema, casing);
	return new ViewBuilder(name, schema);
}
/** @internal */
function pgMaterializedViewWithSchema(name, selection, schema, casing) {
	if (selection) return new ManualMaterializedViewBuilder(name, selection, schema, casing);
	return new MaterializedViewBuilder(name, schema);
}
/** @internal */
function pgViewWithCasing(casing) {
	return ((name, columns) => pgViewWithSchema(name, columns, void 0, casing));
}
/** @internal */
function pgMaterializedViewWithCasing(casing) {
	return ((name, columns) => pgMaterializedViewWithSchema(name, columns, void 0, casing));
}
const pgView = pgViewWithCasing(void 0);
const pgMaterializedView = pgMaterializedViewWithCasing(void 0);
function isPgView(obj) {
	return is(obj, PgView);
}
function isPgMaterializedView(obj) {
	return is(obj, PgMaterializedView);
}

//#endregion
export { DefaultViewBuilderCore, ManualMaterializedViewBuilder, ManualViewBuilder, MaterializedViewBuilder, MaterializedViewBuilderCore, PgMaterializedView, PgView, ViewBuilder, isPgMaterializedView, isPgView, pgMaterializedView, pgMaterializedViewWithCasing, pgMaterializedViewWithSchema, pgView, pgViewWithCasing, pgViewWithSchema };
//# sourceMappingURL=view.js.map