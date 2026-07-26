//#region src/cockroach-core/utils/array.ts
function parseCockroachArrayValue(arrayString, startFrom, inQuotes) {
	for (let i = startFrom; i < arrayString.length; i++) {
		const char = arrayString[i];
		if (char === "\\") {
			i++;
			continue;
		}
		if (char === "\"") return [arrayString.slice(startFrom, i).replace(/\\/g, ""), i + 1];
		if (inQuotes) continue;
		if (char === "," || char === "}") return [arrayString.slice(startFrom, i).replace(/\\/g, ""), i];
	}
	return [arrayString.slice(startFrom).replace(/\\/g, ""), arrayString.length];
}
function parseCockroachNestedArray(arrayString, startFrom = 0) {
	const result = [];
	let i = startFrom;
	let lastCharIsComma = false;
	while (i < arrayString.length) {
		const char = arrayString[i];
		if (char === ",") {
			if (lastCharIsComma || i === startFrom) result.push("");
			lastCharIsComma = true;
			i++;
			continue;
		}
		lastCharIsComma = false;
		if (char === "\\") {
			i += 2;
			continue;
		}
		if (char === "\"") {
			const [value, startFrom] = parseCockroachArrayValue(arrayString, i + 1, true);
			result.push(value);
			i = startFrom;
			continue;
		}
		if (char === "}") return [result, i + 1];
		if (char === "{") {
			const [value, startFrom] = parseCockroachNestedArray(arrayString, i + 1);
			result.push(value);
			i = startFrom;
			continue;
		}
		const [value, newStartFrom] = parseCockroachArrayValue(arrayString, i, false);
		result.push(value);
		i = newStartFrom;
	}
	return [result, i];
}
function parseCockroachArray(arrayString) {
	const [result] = parseCockroachNestedArray(arrayString, 1);
	return result;
}
function makeCockroachArray(array) {
	return `{${array.map((item) => {
		if (Array.isArray(item)) return makeCockroachArray(item);
		if (typeof item === "string") return `"${item.replace(/\\/g, "\\\\").replace(/"/g, "\\\"")}"`;
		return `${item}`;
	}).join(",")}}`;
}

//#endregion
export { makeCockroachArray, parseCockroachArray, parseCockroachNestedArray };
//# sourceMappingURL=array.js.map