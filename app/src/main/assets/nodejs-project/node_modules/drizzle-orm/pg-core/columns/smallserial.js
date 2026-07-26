import { PgColumn, PgColumnBuilder } from "./common.js";
import { entityKind } from "../../entity.js";

//#region src/pg-core/columns/smallserial.ts
var PgSmallSerialBuilder = class extends PgColumnBuilder {
	static [entityKind] = "PgSmallSerialBuilder";
	constructor(name) {
		super(name, "number int16", "PgSmallSerial");
		this.config.hasDefault = true;
		this.config.notNull = true;
	}
	/** @internal */
	build(table) {
		return new PgSmallSerial(table, this.config);
	}
};
var PgSmallSerial = class extends PgColumn {
	static [entityKind] = "PgSmallSerial";
	/** @internal */
	codec = "smallserial";
	getSQLType() {
		return "smallserial";
	}
};
function smallserial(name) {
	return new PgSmallSerialBuilder(name ?? "");
}

//#endregion
export { PgSmallSerial, PgSmallSerialBuilder, smallserial };
//# sourceMappingURL=smallserial.js.map