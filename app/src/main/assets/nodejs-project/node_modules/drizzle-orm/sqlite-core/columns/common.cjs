Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../../entity.cjs");
let __column_ts = require("../../column.cjs");
let __column_builder_ts = require("../../column-builder.cjs");
let __sqlite_core_foreign_keys_ts = require("../foreign-keys.cjs");

//#region src/sqlite-core/columns/common.ts
var SQLiteColumnBuilder = class extends __column_builder_ts.ColumnBuilder {
	static [__entity_ts.entityKind] = "SQLiteColumnBuilder";
	foreignKeyConfigs = [];
	references(ref, actions = {}) {
		this.foreignKeyConfigs.push({
			ref,
			actions
		});
		return this;
	}
	unique(name) {
		this.config.isUnique = true;
		this.config.uniqueName = name;
		return this;
	}
	generatedAlwaysAs(as, config) {
		this.config.generated = {
			as,
			type: "always",
			mode: config?.mode ?? "virtual"
		};
		return this;
	}
	/** @internal */
	buildForeignKeys(column, table) {
		return this.foreignKeyConfigs.map(({ ref, actions }) => {
			return ((ref, actions) => {
				const builder = new __sqlite_core_foreign_keys_ts.ForeignKeyBuilder(() => {
					const foreignColumn = ref();
					return {
						columns: [column],
						foreignColumns: [foreignColumn]
					};
				});
				if (actions.onUpdate) builder.onUpdate(actions.onUpdate);
				if (actions.onDelete) builder.onDelete(actions.onDelete);
				return builder.build(table);
			})(ref, actions);
		});
	}
};
var SQLiteColumn = class extends __column_ts.Column {
	static [__entity_ts.entityKind] = "SQLiteColumn";
	/** @internal */
	table;
	constructor(table, config) {
		super(table, config);
		this.table = table;
	}
};

//#endregion
exports.SQLiteColumn = SQLiteColumn;
exports.SQLiteColumnBuilder = SQLiteColumnBuilder;
//# sourceMappingURL=common.cjs.map