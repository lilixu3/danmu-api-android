import { entityKind } from "../entity.js";
import { View } from "../sql/sql.js";

//#region src/singlestore-core/view-base.ts
var SingleStoreViewBase = class extends View {
	static [entityKind] = "SingleStoreViewBase";
};

//#endregion
export { SingleStoreViewBase };
//# sourceMappingURL=view-base.js.map