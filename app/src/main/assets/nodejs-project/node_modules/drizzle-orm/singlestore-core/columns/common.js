import { entityKind } from "../../entity.js";
import { Column } from "../../column.js";
import { ColumnBuilder } from "../../column-builder.js";

//#region src/singlestore-core/columns/common.ts
var SingleStoreColumnBuilder = class extends ColumnBuilder {
	static [entityKind] = "SingleStoreColumnBuilder";
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
};
var SingleStoreColumn = class extends Column {
	static [entityKind] = "SingleStoreColumn";
	/** @internal */
	table;
	constructor(table, config) {
		super(table, config);
		this.table = table;
	}
};
var SingleStoreColumnBuilderWithAutoIncrement = class extends SingleStoreColumnBuilder {
	static [entityKind] = "SingleStoreColumnBuilderWithAutoIncrement";
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
var SingleStoreColumnWithAutoIncrement = class extends SingleStoreColumn {
	static [entityKind] = "SingleStoreColumnWithAutoIncrement";
	autoIncrement = this.config.autoIncrement;
};

//#endregion
export { SingleStoreColumn, SingleStoreColumnBuilder, SingleStoreColumnBuilderWithAutoIncrement, SingleStoreColumnWithAutoIncrement };
//# sourceMappingURL=common.js.map