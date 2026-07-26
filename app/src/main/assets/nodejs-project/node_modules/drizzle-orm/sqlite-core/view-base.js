import { entityKind } from "../entity.js";
import { View } from "../sql/sql.js";

//#region src/sqlite-core/view-base.ts
var SQLiteViewBase = class extends View {
	static [entityKind] = "SQLiteViewBase";
};

//#endregion
export { SQLiteViewBase };
//# sourceMappingURL=view-base.js.map