import { MySqlColumn, MySqlColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/mysql-core/columns/string.common.ts
var MySqlStringColumnBaseBuilder = class extends MySqlColumnBuilder {
	static [entityKind] = "MySqlStringColumnBuilder";
	charSet(charSet) {
		this.config.charSet = charSet;
		return this;
	}
	collate(collation) {
		this.config.collation = collation;
		return this;
	}
};
var MySqlStringBaseColumn = class extends MySqlColumn {
	static [entityKind] = "MySqlStringColumn";
	charSet = this.config.charSet;
	collation = this.config.collation;
};

//#endregion
export { MySqlStringBaseColumn, MySqlStringColumnBaseBuilder };
//# sourceMappingURL=string.common.js.map