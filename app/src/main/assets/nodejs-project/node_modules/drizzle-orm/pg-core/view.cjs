Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_pg_core_table = require('./table.cjs');
const require_pg_core_view_common = require('./view-common.cjs');
const require_pg_core_query_builders_query_builder = require('./query-builders/query-builder.cjs');
const require_pg_core_view_base = require('./view-base.cjs');
let __entity_ts = require("../entity.cjs");
let __utils_ts = require("../utils.cjs");
let __selection_proxy_ts = require("../selection-proxy.cjs");

//#region src/pg-core/view.ts
var DefaultViewBuilderCore = class {
	static [__entity_ts.entityKind] = "PgDefaultViewBuilderCore";
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
	static [__entity_ts.entityKind] = "PgViewBuilder";
	as(qb) {
		if (typeof qb === "function") qb = qb(new require_pg_core_query_builders_query_builder.QueryBuilder());
		const selectionProxy = new __selection_proxy_ts.SelectionProxyHandler({
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
	static [__entity_ts.entityKind] = "PgManualViewBuilder";
	columns;
	constructor(name, columns, schema, casing) {
		super(name, schema);
		this.columns = (0, __utils_ts.getTableColumns)(require_pg_core_table.pgTableWithSchema(name, columns, void 0, schema, casing));
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
		}), new __selection_proxy_ts.SelectionProxyHandler({
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
		}), new __selection_proxy_ts.SelectionProxyHandler({
			alias: this.name,
			sqlBehavior: "error",
			sqlAliasedBehavior: "alias",
			replaceOriginalName: true
		}));
	}
};
var MaterializedViewBuilderCore = class {
	static [__entity_ts.entityKind] = "PgMaterializedViewBuilderCore";
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
	static [__entity_ts.entityKind] = "PgMaterializedViewBuilder";
	as(qb) {
		if (typeof qb === "function") qb = qb(new require_pg_core_query_builders_query_builder.QueryBuilder());
		const selectionProxy = new __selection_proxy_ts.SelectionProxyHandler({
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
	static [__entity_ts.entityKind] = "PgManualMaterializedViewBuilder";
	columns;
	constructor(name, columns, schema, casing) {
		super(name, schema);
		this.columns = (0, __utils_ts.getTableColumns)(require_pg_core_table.pgTableWithSchema(name, columns, void 0, schema, casing));
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
		}), new __selection_proxy_ts.SelectionProxyHandler({
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
		}), new __selection_proxy_ts.SelectionProxyHandler({
			alias: this.name,
			sqlBehavior: "error",
			sqlAliasedBehavior: "alias",
			replaceOriginalName: true
		}));
	}
};
var PgView = class extends require_pg_core_view_base.PgViewBase {
	static [__entity_ts.entityKind] = "PgView";
	[require_pg_core_view_common.PgViewConfig];
	constructor({ pgConfig, config }) {
		super(config);
		if (pgConfig) this[require_pg_core_view_common.PgViewConfig] = { with: pgConfig.with };
	}
};
var PgMaterializedView = class extends require_pg_core_view_base.PgViewBase {
	static [__entity_ts.entityKind] = "PgMaterializedView";
	[require_pg_core_view_common.PgMaterializedViewConfig];
	constructor({ pgConfig, config }) {
		super(config);
		this[require_pg_core_view_common.PgMaterializedViewConfig] = {
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
	return (0, __entity_ts.is)(obj, PgView);
}
function isPgMaterializedView(obj) {
	return (0, __entity_ts.is)(obj, PgMaterializedView);
}

//#endregion
exports.DefaultViewBuilderCore = DefaultViewBuilderCore;
exports.ManualMaterializedViewBuilder = ManualMaterializedViewBuilder;
exports.ManualViewBuilder = ManualViewBuilder;
exports.MaterializedViewBuilder = MaterializedViewBuilder;
exports.MaterializedViewBuilderCore = MaterializedViewBuilderCore;
exports.PgMaterializedView = PgMaterializedView;
exports.PgView = PgView;
exports.ViewBuilder = ViewBuilder;
exports.isPgMaterializedView = isPgMaterializedView;
exports.isPgView = isPgView;
exports.pgMaterializedView = pgMaterializedView;
exports.pgMaterializedViewWithCasing = pgMaterializedViewWithCasing;
exports.pgMaterializedViewWithSchema = pgMaterializedViewWithSchema;
exports.pgView = pgView;
exports.pgViewWithCasing = pgViewWithCasing;
exports.pgViewWithSchema = pgViewWithSchema;
//# sourceMappingURL=view.cjs.map