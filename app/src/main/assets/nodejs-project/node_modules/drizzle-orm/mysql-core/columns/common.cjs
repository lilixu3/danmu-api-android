Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../../entity.cjs");
let __column_ts = require("../../column.cjs");
let __column_builder_ts = require("../../column-builder.cjs");
let __mysql_core_foreign_keys_ts = require("../foreign-keys.cjs");

//#region src/mysql-core/columns/common.ts
var MySqlColumnBuilder = class extends __column_builder_ts.ColumnBuilder {
	static [__entity_ts.entityKind] = "MySqlColumnBuilder";
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
				const builder = new __mysql_core_foreign_keys_ts.ForeignKeyBuilder(() => {
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
var MySqlColumn = class extends __column_ts.Column {
	static [__entity_ts.entityKind] = "MySqlColumn";
	/** @internal */
	table;
	constructor(table, config) {
		super(table, config);
		this.table = table;
	}
};
var MySqlColumnBuilderWithAutoIncrement = class extends MySqlColumnBuilder {
	static [__entity_ts.entityKind] = "MySqlColumnBuilderWithAutoIncrement";
	constructor(name, dataType, columnType) {
		super(name, dataType, columnType);
		this.config.autoIncrement = false;
	}
	autoincrement() {
		this.config.autoIncrement = true;
		this.config.hasDefault = true;
		return this;
	}
};
var MySqlColumnWithAutoIncrement = class extends MySqlColumn {
	static [__entity_ts.entityKind] = "MySqlColumnWithAutoIncrement";
	autoIncrement = this.config.autoIncrement;
};

//#endregion
exports.MySqlColumn = MySqlColumn;
exports.MySqlColumnBuilder = MySqlColumnBuilder;
exports.MySqlColumnBuilderWithAutoIncrement = MySqlColumnBuilderWithAutoIncrement;
exports.MySqlColumnWithAutoIncrement = MySqlColumnWithAutoIncrement;
//# sourceMappingURL=common.cjs.map