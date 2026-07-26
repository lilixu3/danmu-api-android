import { entityKind } from "../entity.js";

//#region src/cockroach-core/foreign-keys.ts
var ForeignKeyBuilder = class {
	static [entityKind] = "CockroachForeignKeyBuilder";
	/** @internal */
	reference;
	/** @internal */
	_onUpdate = "no action";
	/** @internal */
	_onDelete = "no action";
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
		this._onUpdate = action === void 0 ? "no action" : action;
		return this;
	}
	onDelete(action) {
		this._onDelete = action === void 0 ? "no action" : action;
		return this;
	}
	/** @internal */
	build(table) {
		return new ForeignKey(table, this);
	}
};
var ForeignKey = class {
	static [entityKind] = "CockroachForeignKey";
	reference;
	onUpdate;
	onDelete;
	name;
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
export { ForeignKey, ForeignKeyBuilder, foreignKey };
//# sourceMappingURL=foreign-keys.js.map