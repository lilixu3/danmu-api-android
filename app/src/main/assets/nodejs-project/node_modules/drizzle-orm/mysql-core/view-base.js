import { entityKind } from "../entity.js";
import { View } from "../sql/sql.js";

//#region src/mysql-core/view-base.ts
var MySqlViewBase = class extends View {
	static [entityKind] = "MySqlViewBase";
};

//#endregion
export { MySqlViewBase };
//# sourceMappingURL=view-base.js.map