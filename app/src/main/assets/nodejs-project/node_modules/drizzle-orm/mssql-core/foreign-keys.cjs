Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
const require_runtime = require('../_virtual/_rolldown/runtime.cjs');
let __entity_ts = require("../entity.cjs");

//#region src/mssql-core/foreign-keys.ts
var ForeignKeyBuilder = class {
	static [__entity_ts.entityKind] = "MsSqlForeignKeyBuilder";
	/** @internal */
	reference;
	/** @internal */
	_onUpdate;
	/** @internal */
	_onDelete;
	constructor(config, actions) {
		this.reference = () => {
			const { name, columns, foreignColumns } = config();
			return {
				name,
				columns,
				foreignTable: foreignColumns[0].table,
				foreignColumns
			};
		};
		if (actions) {
			this._onUpdate = actions.onUpdate;
			this._onDelete = actions.onDelete;
		}
	}
	onUpdate(action) {
		this._onUpdate = action;
		return this;
	}
	onDelete(action) {
		this._onDelete = action;
		return this;
	}
	/** @internal */
	build(table) {
		return new ForeignKey(table, this);
	}
};
var ForeignKey = class {
	static [__entity_ts.entityKind] = "MsSqlForeignKey";
	reference;
	onUpdate;
	onDelete;
	constructor(table, builder) {
		this.table = table;
		this.reference = builder.reference;
		this.onUpdate = builder._onUpdate;
		this.onDelete = builder._onDelete;
	}
	getName() {
		const { name } = this.reference();
		return name;
	}
	isNameExplicit() {
		return !!this.reference().name;
	}
};
function foreignKey(config) {
	function mappedConfig() {
		const { name, columns, foreignColumns } = config;
		return {
			name,
			columns,
			foreignColumns
		};
	}
	return new ForeignKeyBuilder(mappedConfig);
}

//#endregion
exports.ForeignKey = ForeignKey;
exports.ForeignKeyBuilder = ForeignKeyBuilder;
exports.foreignKey = foreignKey;
//# sourceMappingURL=foreign-keys.cjs.map