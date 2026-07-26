import { entityKind } from "../entity.js";
import { View } from "../sql/sql.js";

//#region src/mssql-core/view-base.ts
var MsSqlViewBase = class extends View {
	static [entityKind] = "MsSqlViewBase";
};

//#endregion
export { MsSqlViewBase };
//# sourceMappingURL=view-base.js.map