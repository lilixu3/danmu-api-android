Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
const require_mssql_core_table = require('./table.cjs');
const require_mssql_core_view_base = require('./view-base.cjs');
const require_mssql_core_query_builders_query_builder = require('./query-builders/query-builder.cjs');
const require_mssql_core_view_common = require('./view-common.cjs');
let __entity_ts = require("../entity.cjs");
let __utils_ts = require("../utils.cjs");
let __selection_proxy_ts = require("../selection-proxy.cjs");

//#region src/mssql-core/view.ts
var ViewBuilderCore = class {
	static [__entity_ts.entityKind] = "MsSqlViewBuilder";
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
	static [__entity_ts.entityKind] = "MsSqlViewBuilder";
	as(qb) {
		if (typeof qb === "function") qb = qb(new require_mssql_core_query_builders_query_builder.QueryBuilder());
		const selectionProxy = new __selection_proxy_ts.SelectionProxyHandler({
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
	static [__entity_ts.entityKind] = "MsSqlManualViewBuilder";
	columns;
	constructor(name, columns, schema, casing) {
		super(name, schema);
		this.columns = (0, __utils_ts.getTableColumns)(require_mssql_core_table.mssqlTableWithSchema(name, columns, void 0, schema, casing));
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
		}), new __selection_proxy_ts.SelectionProxyHandler({
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
		}), new __selection_proxy_ts.SelectionProxyHandler({
			alias: this.name,
			sqlBehavior: "error",
			sqlAliasedBehavior: "alias",
			replaceOriginalName: true
		}));
	}
};
var MsSqlView = class extends require_mssql_core_view_base.MsSqlViewBase {
	static [__entity_ts.entityKind] = "MsSqlView";
	[require_mssql_core_view_common.MsSqlViewConfig];
	constructor({ mssqlConfig, config }) {
		super(config);
		this[require_mssql_core_view_common.MsSqlViewConfig] = mssqlConfig;
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
exports.ManualViewBuilder = ManualViewBuilder;
exports.MsSqlView = MsSqlView;
exports.ViewBuilder = ViewBuilder;
exports.ViewBuilderCore = ViewBuilderCore;
exports.mssqlView = mssqlView;
exports.mssqlViewWithCasing = mssqlViewWithCasing;
exports.mssqlViewWithSchema = mssqlViewWithSchema;
//# sourceMappingURL=view.cjs.map