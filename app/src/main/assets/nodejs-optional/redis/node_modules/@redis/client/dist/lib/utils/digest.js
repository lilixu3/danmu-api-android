"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.digest = void 0;
let xxh3Cache = null;
async function getXxh3() {
    if (!xxh3Cache) {
        try {
            const module = await import('@node-rs/xxhash');
            xxh3Cache = module.xxh3;
        }
        catch {
            throw new Error('The "digest" function requires the "@node-rs/xxhash" package, but it was not found.');
        }
    }
    return xxh3Cache;
}
/**
 * Computes a deterministic 64-bit XXH3 digest of the input.
 *
 * This produces the same digest that Redis computes internally via the `DIGEST` command,
 * allowing you to use it with conditional SET and DELEX operations (`IFDEQ`, `IFDNE`).
 *
 * @param value - The value to compute the digest for (string or Buffer)
 * @returns A 16-character lowercase hexadecimal digest
 * @throws If the `@node-rs/xxhash` package is not found
 */
async function digest(value) {
    const xxh3 = await getXxh3();
    const data = typeof value === 'string' ? value : new Uint8Array(value);
    const hash = xxh3.xxh64(data);
    return hash.toString(16).padStart(16, '0');
}
exports.digest = digest;
//# sourceMappingURL=digest.js.map