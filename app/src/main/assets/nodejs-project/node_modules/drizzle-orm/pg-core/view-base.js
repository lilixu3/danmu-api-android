import { entityKind } from "../entity.js";
import { View } from "../sql/sql.js";

//#region src/pg-core/view-base.ts
var PgViewBase = class extends View {
	static [entityKind] = "PgViewBase";
};

//#endregion
export { PgViewBase };
//# sourceMappingURL=view-base.js.map