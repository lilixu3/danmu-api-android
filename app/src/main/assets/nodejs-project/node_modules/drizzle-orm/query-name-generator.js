import { is } from "./entity.js";
import { Placeholder } from "./sql/sql.js";

//#region src/query-name-generator.ts
function isBinary(value) {
	if (typeof Buffer !== "undefined" && typeof Buffer.isBuffer === "function" && Buffer.isBuffer(value)) return true;
	if (value instanceof ArrayBuffer) return true;
	if (ArrayBuffer.isView(value)) return true;
	return false;
}
function arrayTypeId(arr) {
	if (!arr.length) return "array<void>";
	let elementId;
	for (let i = 0; i < arr.length; i++) {
		const id = jsTypeId(arr[i]);
		if (!elementId) {
			elementId = id;
			continue;
		}
		if (elementId !== id) {
			elementId = `${elementId},${id}`;
			continue;
		}
		elementId = id;
	}
	return `array<${elementId}>`;
}
function jsTypeId(value) {
	if (value === null) return "null";
	if (is(value, Placeholder)) return "placeholder";
	if (value instanceof Date) return "date";
	if (Array.isArray(value)) return arrayTypeId(value);
	if (isBinary(value)) return "binary";
	return typeof value;
}
function hash(str, seed = 5381) {
	let h = seed;
	for (let i = 0; i < str.length; i++) h = (h << 5) + h ^ str.charCodeAt(i);
	return h >>> 0;
}
const safeChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
function stringify(hash, length, startWithLetter = false) {
	let result = "";
	let h = hash;
	if (startWithLetter) {
		result += safeChars[h % 52];
		h = h >>> 6;
		--length;
	}
	while (result.length < length) {
		result += safeChars[h % 63];
		h = h >>> 6;
		if (h === 0) break;
	}
	return result;
}
function preparedStatementName(sql, params = []) {
	let hash1 = hash(sql);
	let hash2 = hash(sql, -559043606);
	const paramIds = params.map(jsTypeId).join(",");
	for (let ti = 0; ti < paramIds.length; ti++) {
		hash1 = (hash1 << 5) + hash1 ^ paramIds.charCodeAt(ti);
		hash2 = (hash2 << 5) + hash2 ^ paramIds.charCodeAt(ti);
	}
	return stringify(hash1, 31, true) + stringify(hash2, 32);
}

//#endregion
export { preparedStatementName };
//# sourceMappingURL=query-name-generator.js.map