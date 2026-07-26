/**
 * SHA3 (keccak) addons.
 *
 * * cSHAKE, KMAC, TupleHash, ParallelHash + XOF variants from
 *   {@link https://csrc.nist.gov/pubs/sp/800/185/final | NIST SP 800-185}
 * * KangarooTwelve 🦘 and TurboSHAKE - reduced-round keccak from
 *   {@link https://datatracker.ietf.org/doc/rfc9861/ | RFC 9861}
 * * KeccakPRG: Pseudo-random generator based on Keccak
 *   ({@link https://keccak.team/files/CSF-0.1.pdf | pdf})
 * @module
 */
import { Keccak } from "./sha3.js";
import { abytes, aexists, anumber, clean, copyBytes, createHasher, kdfInputToBytes, u32, } from "./utils.js";
// cSHAKE && KMAC (NIST SP800-185)
const _8n = /* @__PURE__ */ BigInt(8);
const _ffn = /* @__PURE__ */ BigInt(0xff);
// It is safe to use bigints here, since they used only for length encoding (not actual data).
// We use bigints in sha256 for lengths too.
// Callers are still expected to supply SP 800-185-valid lengths
// (`0 <= x < 2^2040`); this helper does not enforce that bound.
function leftEncode(n) {
    n = BigInt(n);
    const res = [Number(n & _ffn)];
    n >>= _8n;
    for (; n > 0; n >>= _8n)
        res.unshift(Number(n & _ffn));
    res.unshift(res.length);
    return new Uint8Array(res);
}
// Same caller contract as `leftEncode(...)`: lengths must already satisfy SP 800-185 §2.3.1.
function rightEncode(n) {
    n = BigInt(n);
    const res = [Number(n & _ffn)];
    n >>= _8n;
    for (; n > 0; n >>= _8n)
        res.unshift(Number(n & _ffn));
    res.push(res.length);
    return new Uint8Array(res);
}
// `dkLen` validation is deferred to the downstream Keccak constructor.
function chooseLen(opts, outputLen) {
    return opts.dkLen === undefined ? outputLen : opts.dkLen;
}
const abytesOrZero = (buf, title = '') => {
    if (buf === undefined)
        return EMPTY_BUFFER;
    abytes(buf, undefined, title);
    return buf;
};
// NOTE: second modulo is necessary since we don't need to add padding if the
// current element takes a whole block.
// Callers only pass the fixed positive Keccak rates here (`168` or `136`);
// `block <= 0` is not validated locally.
const getPadding = (len, block) => new Uint8Array((block - (len % block)) % block);
// Personalization
function cshakePers(hash, opts = {}) {
    const h = hash;
    if (!opts || (opts.personalization === undefined && opts.NISTfn === undefined))
        return h;
    // Encode and pad inplace to avoid unneccesary memory copies/slices so we
    // don't need to zero them later.
    // bytepad(encode_string(N) || encode_string(S), rate), where `rate` is the
    // current cSHAKE/KMAC/TupleHash/ParallelHash block length.
    const blockLenBytes = leftEncode(h.blockLen);
    const fn = opts.NISTfn === undefined ? EMPTY_BUFFER : kdfInputToBytes(opts.NISTfn);
    const fnLen = leftEncode(_8n * BigInt(fn.length)); // length in bits
    const pers = abytesOrZero(opts.personalization, 'personalization');
    const persLen = leftEncode(_8n * BigInt(pers.length)); // length in bits
    if (!fn.length && !pers.length)
        return h;
    // SP 800-185 cSHAKE appends `00` instead of SHAKE's `1111`; in this Keccak implementation
    // that changes the delimited suffix byte from `0x1f` to `0x04` once N or S is non-empty.
    h.suffix = 0x04;
    h.update(blockLenBytes).update(fnLen).update(fn).update(persLen).update(pers);
    let totalLen = blockLenBytes.length + fnLen.length + fn.length + persLen.length + pers.length;
    h.update(getPadding(totalLen, h.blockLen));
    return h;
}
const gencShake = (suffix, blockLen, outputLen) => createHasher((opts = {}) => cshakePers(new Keccak(blockLen, suffix, chooseLen(opts, outputLen), true), opts));
/**
 * 128-bit NIST cSHAKE XOF.
 * @param msg - message bytes to hash
 * @param opts - Optional output, personalization, and NIST function-name
 *   settings. When both `NISTfn` and `personalization` are empty,
 *   SP 800-185 defines this as plain SHAKE128. Defaults to 16 output bytes
 *   when `dkLen` is omitted. See {@link cShakeOpts}.
 * @returns Digest bytes.
 * @example
 * Hash a message with cSHAKE128.
 * ```ts
 * cshake128(new Uint8Array([1, 2, 3]), { dkLen: 32 });
 * ```
 */
export const cshake128 = /* @__PURE__ */ gencShake(0x1f, 168, 16);
/**
 * 256-bit NIST cSHAKE XOF.
 * @param msg - message bytes to hash
 * @param opts - Optional output, personalization, and NIST function-name
 *   settings. When both `NISTfn` and `personalization` are empty,
 *   SP 800-185 defines this as plain SHAKE256. Defaults to 32 output bytes
 *   when `dkLen` is omitted. See {@link cShakeOpts}.
 * @returns Digest bytes.
 * @example
 * Hash a message with cSHAKE256.
 * ```ts
 * cshake256(new Uint8Array([1, 2, 3]), { dkLen: 64 });
 * ```
 */
export const cshake256 = /* @__PURE__ */ gencShake(0x1f, 136, 32);
/**
 * Internal KMAC class.
 * SP 800-185 §8.4.1 still recommends keys at least as long as the target
 * security strength.
 */
export class _KMAC extends Keccak {
    constructor(blockLen, outputLen, enableXOF, key, opts = {}) {
        super(blockLen, 0x1f, outputLen, enableXOF);
        // Preload T = bytepad(encode_string("KMAC") || encode_string(S), rate); later updates append
        // newX = bytepad(encode_string(K), rate) || X and `finish()` appends right_encode(L or 0).
        cshakePers(this, {
            NISTfn: 'KMAC',
            personalization: opts.personalization,
        });
        abytes(key, undefined, 'key');
        // 1. newX = bytepad(encode_string(K), rate) || X || right_encode(L),
        // with `rate = this.blockLen`.
        const blockLenBytes = leftEncode(this.blockLen);
        const keyLen = leftEncode(_8n * BigInt(key.length));
        this.update(blockLenBytes).update(keyLen).update(key);
        const totalLen = blockLenBytes.length + keyLen.length + key.length;
        this.update(getPadding(totalLen, this.blockLen));
    }
    finish() {
        // SP 800-185 uses right_encode(L) for fixed-length KMAC and right_encode(0) for KMACXOF.
        // outputLen in bits
        if (!this.finished)
            this.update(rightEncode(this.enableXOF ? 0 : _8n * BigInt(this.outputLen)));
        super.finish();
    }
    _cloneInto(to) {
        // Create new instance without calling constructor since the key
        // is already in state and we don't know it.
        // Force "to" to be instance of KMAC instead of Sha3.
        if (!to) {
            to = Object.create(Object.getPrototypeOf(this), {});
            to.state = this.state.slice();
            to.blockLen = this.blockLen;
            to.state32 = u32(to.state);
        }
        return super._cloneInto(to);
    }
    clone() {
        return this._cloneInto();
    }
}
function genKmac(blockLen, outputLen, xof = false) {
    // One-shot XOF wrappers still finalize via `.digest()` because `_KMAC`
    // already bakes the requested output length into the state.
    const kmac = (key, message, opts) => kmac.create(key, opts).update(message).digest();
    kmac.create = (key, opts = {}) => new _KMAC(blockLen, chooseLen(opts, outputLen), xof, key, opts);
    return kmac;
}
/**
 * 128-bit Keccak MAC.
 * @param key - MAC key bytes
 * @param message - message bytes to authenticate
 * @param opts - Optional output and personalization settings. Defaults to
 *   16 output bytes when `dkLen` is omitted. See {@link cShakeOpts}.
 * @returns Authentication tag bytes.
 * @example
 * Authenticate a message with KMAC128.
 * ```ts
 * kmac128(new Uint8Array([1, 2, 3]), new Uint8Array([4, 5, 6]));
 * ```
 */
export const kmac128 = /* @__PURE__ */ genKmac(168, 16);
/**
 * 256-bit Keccak MAC.
 * @param key - MAC key bytes
 * @param message - message bytes to authenticate
 * @param opts - Optional output and personalization settings. Defaults to
 *   32 output bytes when `dkLen` is omitted. See {@link cShakeOpts}.
 * @returns Authentication tag bytes.
 * @example
 * Authenticate a message with KMAC256.
 * ```ts
 * kmac256(new Uint8Array([1, 2, 3]), new Uint8Array([4, 5, 6]));
 * ```
 */
export const kmac256 = /* @__PURE__ */ genKmac(136, 32);
/**
 * 128-bit Keccak-MAC XOF.
 * @param key - MAC key bytes
 * @param message - message bytes to authenticate
 * @param opts - Optional output and personalization settings. Defaults to
 *   16 output bytes when `dkLen` is omitted. See {@link cShakeOpts}.
 * @returns Authentication tag bytes.
 * @example
 * Authenticate a message with KMAC128 XOF output.
 * ```ts
 * kmac128xof(new Uint8Array([1, 2, 3]), new Uint8Array([4, 5, 6]), { dkLen: 32 });
 * ```
 */
export const kmac128xof = /* @__PURE__ */ genKmac(168, 16, true);
/**
 * 256-bit Keccak-MAC XOF.
 * @param key - MAC key bytes
 * @param message - message bytes to authenticate
 * @param opts - Optional output and personalization settings. Defaults to
 *   32 output bytes when `dkLen` is omitted. See {@link cShakeOpts}.
 * @returns Authentication tag bytes.
 * @example
 * Authenticate a message with KMAC256 XOF output.
 * ```ts
 * kmac256xof(new Uint8Array([1, 2, 3]), new Uint8Array([4, 5, 6]), { dkLen: 64 });
 * ```
 */
export const kmac256xof = /* @__PURE__ */ genKmac(136, 32, true);
/**
 * Internal TupleHash class for byte-array tuple elements.
 * This implementation relies on SP 800-185's byte-oriented encoding form
 * rather than arbitrary bit strings.
 */
export class _TupleHash extends Keccak {
    constructor(blockLen, outputLen, enableXOF, opts = {}) {
        super(blockLen, 0x1f, outputLen, enableXOF);
        cshakePers(this, {
            NISTfn: 'TupleHash',
            personalization: opts.personalization,
        });
        // Change update after cshake processed
        this.update = (data) => {
            abytes(data);
            // SP 800-185 encodes each tuple element as
            // encode_string(X[i]) = left_encode(len(X[i])) || X[i].
            super.update(leftEncode(_8n * BigInt(data.length)));
            super.update(data);
            return this;
        };
    }
    finish() {
        // SP 800-185 uses right_encode(L) for fixed-length TupleHash
        // and right_encode(0) for TupleHashXOF.
        if (!this.finished)
            // outputLen in bits
            super.update(rightEncode(this.enableXOF ? 0 : _8n * BigInt(this.outputLen)));
        super.finish();
    }
    _cloneInto(to) {
        to ||= new _TupleHash(this.blockLen, this.outputLen, this.enableXOF);
        return super._cloneInto(to);
    }
    clone() {
        return this._cloneInto();
    }
}
function genTuple(blockLen, outputLen, xof = false) {
    // One-shot XOF wrappers still use `.digest()` because `_TupleHash` stores
    // the requested output length in the state itself.
    const tuple = (messages, opts) => {
        const h = tuple.create(opts);
        if (!Array.isArray(messages))
            throw new Error('expected array of messages');
        for (const msg of messages)
            h.update(msg);
        return h.digest();
    };
    tuple.create = (opts = {}) => new _TupleHash(blockLen, chooseLen(opts, outputLen), xof, opts);
    return tuple;
}
/**
 * 128-bit TupleHASH. `tuple(['ab', 'cd']) != tuple(['a', 'bcd'])`.
 * @param messages - ordered byte-array tuple
 * @param opts - Optional output and personalization settings. Defaults to
 *   16 output bytes when `dkLen` is omitted. See {@link cShakeOpts}.
 * @returns Digest bytes.
 * @example
 * Hash a tuple of byte arrays with TupleHash128.
 * ```ts
 * tuplehash128([new Uint8Array([1]), new Uint8Array([2])]);
 * ```
 */
export const tuplehash128 = /* @__PURE__ */ genTuple(168, 16);
/**
 * 256-bit TupleHASH. `tuple(['ab', 'cd']) != tuple(['a', 'bcd'])`.
 * @param messages - ordered byte-array tuple
 * @param opts - Optional output and personalization settings. Defaults to
 *   32 output bytes when `dkLen` is omitted. See {@link cShakeOpts}.
 * @returns Digest bytes.
 * @example
 * Hash a tuple of byte arrays with TupleHash256.
 * ```ts
 * tuplehash256([new Uint8Array([1]), new Uint8Array([2])]);
 * ```
 */
export const tuplehash256 = /* @__PURE__ */ genTuple(136, 32);
/**
 * 128-bit TupleHASH XOF.
 * @param messages - ordered byte-array tuple
 * @param opts - Optional output and personalization settings. Defaults to
 *   16 output bytes when `dkLen` is omitted. See {@link cShakeOpts}.
 * @returns Digest bytes.
 * @example
 * Hash a tuple of byte arrays with TupleHash128 XOF output.
 * ```ts
 * tuplehash128xof([new Uint8Array([1]), new Uint8Array([2])], { dkLen: 32 });
 * ```
 */
export const tuplehash128xof = /* @__PURE__ */ genTuple(168, 16, true);
/**
 * 256-bit TupleHASH XOF.
 * @param messages - ordered byte-array tuple
 * @param opts - Optional output and personalization settings. Defaults to
 *   32 output bytes when `dkLen` is omitted. See {@link cShakeOpts}.
 * @returns Digest bytes.
 * @example
 * Hash a tuple of byte arrays with TupleHash256 XOF output.
 * ```ts
 * tuplehash256xof([new Uint8Array([1]), new Uint8Array([2])], { dkLen: 64 });
 * ```
 */
export const tuplehash256xof = /* @__PURE__ */ genTuple(136, 32, true);
/** Internal Parallel Keccak Hash class. */
export class _ParallelHash extends Keccak {
    leafHash;
    leafCons;
    chunkPos = 0; // Position of current block in chunk
    chunksDone = 0; // How many chunks we already have
    chunkLen;
    constructor(blockLen, outputLen, leafCons, enableXOF, opts = {}) {
        super(blockLen, 0x1f, outputLen, enableXOF);
        cshakePers(this, {
            NISTfn: 'ParallelHash',
            personalization: opts.personalization,
        });
        this.leafCons = leafCons;
        let { blockLen: B = 8 } = opts;
        anumber(B);
        // blockLen=0 makes take=0 in update(), so pos never advances and the hash hangs.
        if (B < 1)
            throw new Error('"blockLen" must be >= 1, got ' + B);
        this.chunkLen = B;
        // SP 800-185 initializes z = left_encode(B); each completed chunk appends
        // one fixed-size cSHAKE leaf digest before finish() adds right_encode(n)
        // and right_encode(L or 0).
        super.update(leftEncode(B));
        // Change update after cshake processed
        this.update = (data) => {
            abytes(data);
            const { chunkLen, leafCons } = this;
            for (let pos = 0, len = data.length; pos < len;) {
                if (this.chunkPos == chunkLen || !this.leafHash) {
                    if (this.leafHash) {
                        super.update(this.leafHash.digest());
                        this.chunksDone++;
                    }
                    this.leafHash = leafCons();
                    this.chunkPos = 0;
                }
                const take = Math.min(chunkLen - this.chunkPos, len - pos);
                this.leafHash.update(data.subarray(pos, pos + take));
                this.chunkPos += take;
                pos += take;
            }
            return this;
        };
    }
    finish() {
        if (this.finished)
            return;
        if (this.leafHash) {
            super.update(this.leafHash.digest());
            this.chunksDone++;
        }
        // SP 800-185 finishes ParallelHash as
        // z || right_encode(n) || right_encode(L); XOF mode replaces
        // right_encode(L) with right_encode(0).
        super.update(rightEncode(this.chunksDone));
        // outputLen in bits
        super.update(rightEncode(this.enableXOF ? 0 : _8n * BigInt(this.outputLen)));
        super.finish();
    }
    _cloneInto(to) {
        to ||= new _ParallelHash(this.blockLen, this.outputLen, this.leafCons, this.enableXOF);
        to.leafCons = this.leafCons;
        // Reused destinations can carry a stale partial leaf
        // when the source is still on the root sponge.
        if (this.leafHash)
            to.leafHash = this.leafHash._cloneInto(to.leafHash);
        else if (to.leafHash) {
            to.leafHash.destroy();
            to.leafHash = undefined;
        }
        to.chunkPos = this.chunkPos;
        to.chunkLen = this.chunkLen;
        to.chunksDone = this.chunksDone;
        return super._cloneInto(to);
    }
    destroy() {
        super.destroy.call(this);
        if (this.leafHash)
            this.leafHash.destroy();
    }
    clone() {
        return this._cloneInto();
    }
}
function genPrl(blockLen, outputLen, leaf, xof = false) {
    const parallel = (message, opts) => parallel.create(opts).update(message).digest();
    parallel.create = (opts = {}) => new _ParallelHash(blockLen, chooseLen(opts, outputLen), 
    // SP 800-185 fixes leaf digests at 256 bits for ParallelHash128 and
    // 512 bits for ParallelHash256; only the final cSHAKE output uses the
    // caller-selected dkLen.
    () => leaf.create({ dkLen: 2 * outputLen }), xof, opts);
    parallel.outputLen = outputLen;
    parallel.blockLen = blockLen;
    parallel.canXOF = xof;
    return parallel;
}
/**
 * 128-bit ParallelHash. In JS, it is not parallel.
 * @param msg - message bytes to hash
 * @param opts - Optional output, personalization, and chunking settings.
 *   Defaults to 16 output bytes when `dkLen` is omitted.
 *   See {@link ParallelOpts}.
 * @returns Digest bytes.
 * @example
 * Hash a message with ParallelHash128.
 * ```ts
 * parallelhash128(new Uint8Array([1, 2, 3]));
 * ```
 */
export const parallelhash128 = /* @__PURE__ */ genPrl(168, 16, cshake128);
/**
 * 256-bit ParallelHash. In JS, it is not parallel.
 * @param msg - message bytes to hash
 * @param opts - Optional output, personalization, and chunking settings.
 *   Defaults to 32 output bytes when `dkLen` is omitted.
 *   See {@link ParallelOpts}.
 * @returns Digest bytes.
 * @example
 * Hash a message with ParallelHash256.
 * ```ts
 * parallelhash256(new Uint8Array([1, 2, 3]));
 * ```
 */
export const parallelhash256 = /* @__PURE__ */ genPrl(136, 32, cshake256);
/**
 * 128-bit ParallelHash XOF. In JS, it is not parallel.
 * @param msg - message bytes to hash
 * @param opts - Optional output, personalization, and chunking settings.
 *   Defaults to 16 output bytes when `dkLen` is omitted.
 *   See {@link ParallelOpts}.
 * @returns Digest bytes.
 * @example
 * Hash a message with ParallelHash128 XOF output.
 * ```ts
 * parallelhash128xof(new Uint8Array([1, 2, 3]), { dkLen: 32 });
 * ```
 */
export const parallelhash128xof = /* @__PURE__ */ genPrl(168, 16, cshake128, true);
/**
 * 256-bit ParallelHash XOF. In JS, it is not parallel.
 * @param msg - message bytes to hash
 * @param opts - Optional output, personalization, and chunking settings.
 *   Defaults to 32 output bytes when `dkLen` is omitted.
 *   See {@link ParallelOpts}.
 * @returns Digest bytes.
 * @example
 * Hash a message with ParallelHash256 XOF output.
 * ```ts
 * parallelhash256xof(new Uint8Array([1, 2, 3]), { dkLen: 64 });
 * ```
 */
export const parallelhash256xof = /* @__PURE__ */ genPrl(136, 32, cshake256, true);
const genTurbo = (blockLen, outputLen) => createHasher((opts = {}) => {
    const D = opts.D === undefined ? 0x1f : opts.D;
    // RFC 9861 §2.1 fixes the default `D = 0x1f`; §2.2 defines the 12-round
    // TurboSHAKE family selected here.
    if (!Number.isSafeInteger(D) || D < 0x01 || D > 0x7f)
        throw new Error('"D" (domain separation byte) must be 0x01..0x7f, got: ' + D);
    const dkLen = opts.dkLen === undefined ? outputLen : opts.dkLen;
    // RFC 9861 §§2.1-2.2 define output length L as a positive integer.
    if (dkLen < 1)
        throw new Error('"dkLen" must be >= 1');
    return new Keccak(blockLen, D, dkLen, true, 12);
});
/**
 * TurboSHAKE 128-bit: reduced 12-round keccak.
 * Should've been a simple "shake with 12 rounds", but we got a whole new
 * spec about Turbo SHAKE Pro MAX.
 * @param msg - message bytes to hash
 * @param opts - Optional output-length and domain-separation settings.
 *   RFC 9861 §2.1 defaults `D` to `0x1f`. Defaults to 32 output bytes when
 *   `dkLen` is omitted. See {@link TurboshakeOpts}.
 * @returns Digest bytes.
 * @example
 * Hash a message with TurboSHAKE128.
 * ```ts
 * turboshake128(new Uint8Array([1, 2, 3]), { dkLen: 32 });
 * ```
 */
export const turboshake128 = /* @__PURE__ */ genTurbo(168, 32);
/**
 * TurboSHAKE 256-bit: reduced 12-round keccak.
 * @param msg - message bytes to hash
 * @param opts - Optional output-length and domain-separation settings.
 *   RFC 9861 §2.1 defaults `D` to `0x1f`. Defaults to 64 output bytes when
 *   `dkLen` is omitted. See {@link TurboshakeOpts}.
 * @returns Digest bytes.
 * @example
 * Hash a message with TurboSHAKE256.
 * ```ts
 * turboshake256(new Uint8Array([1, 2, 3]), { dkLen: 64 });
 * ```
 */
export const turboshake256 = /* @__PURE__ */ genTurbo(136, 64);
// Same as NIST rightEncode, but returns `[0]` for the zero string.
// Callers still need to keep `x < 256^255` per RFC 9861 §3.3.
function rightEncodeK12(n) {
    n = BigInt(n);
    const res = [];
    for (; n > 0; n >>= _8n)
        res.unshift(Number(n & _ffn));
    res.push(res.length);
    return Uint8Array.from(res);
}
const EMPTY_BUFFER = /* @__PURE__ */ Uint8Array.of();
/** Internal K12 hash class. */
export class _KangarooTwelve extends Keccak {
    chunkLen = 8192;
    leafHash;
    leafLen;
    personalization;
    chunkPos = 0; // Position of current block in chunk
    chunksDone = 0; // How many chunks we already have
    constructor(blockLen, leafLen, outputLen, rounds, opts) {
        super(blockLen, 0x07, outputLen, true, rounds);
        // RFC 9861 §3 defines output length L as a positive integer.
        if (outputLen < 1)
            throw new Error('"dkLen" must be >= 1');
        this.leafLen = leafLen;
        this.personalization =
            opts.personalization === undefined
                ? EMPTY_BUFFER
                : copyBytes(abytes(opts.personalization, undefined, 'personalization'));
    }
    update(data) {
        abytes(data);
        const { chunkLen, blockLen, leafLen, rounds } = this;
        for (let pos = 0, len = data.length; pos < len;) {
            if (this.chunkPos == chunkLen) {
                if (this.leafHash)
                    super.update(this.leafHash.digest());
                else {
                    // RFC 9861 §3.2 switches from SingleNode (`07`) to FinalNode (`06`)
                    // once S exceeds 8192 bytes and prefixes S_0 with
                    // `03 00 00 00 00 00 00 00`.
                    this.suffix = 0x06; // Its safe to change suffix here since its used only in digest()
                    super.update(Uint8Array.from([3, 0, 0, 0, 0, 0, 0, 0]));
                }
                // Secondary chunks S_1..S_(n-1) become fixed-length
                // CV_i = TurboSHAKE*(S_i, `0B`, 32|64) chaining values.
                this.leafHash = new Keccak(blockLen, 0x0b, leafLen, false, rounds);
                this.chunksDone++;
                this.chunkPos = 0;
            }
            const take = Math.min(chunkLen - this.chunkPos, len - pos);
            const chunk = data.subarray(pos, pos + take);
            if (this.leafHash)
                this.leafHash.update(chunk);
            else
                super.update(chunk);
            this.chunkPos += take;
            pos += take;
        }
        return this;
    }
    finish() {
        if (this.finished)
            return;
        const { personalization } = this;
        // RFC 9861 §3.2 forms S = M || C || length_encode(|C|) before any tree hashing logic.
        this.update(personalization).update(rightEncodeK12(personalization.length));
        // Leaf hash
        if (this.leafHash) {
            // Multi-chunk K12 appends
            // CV_1..CV_(n-1) || length_encode(n-1) || `FF FF`
            // before the final TurboSHAKE call.
            super.update(this.leafHash.digest());
            super.update(rightEncodeK12(this.chunksDone));
            super.update(Uint8Array.from([0xff, 0xff]));
        }
        super.finish.call(this);
    }
    destroy() {
        super.destroy.call(this);
        if (this.leafHash)
            this.leafHash.destroy();
        // Personalization is copied on create/clone, so destroy can wipe it
        // without touching caller input.
        if (this.personalization !== EMPTY_BUFFER)
            clean(this.personalization);
        this.personalization = EMPTY_BUFFER;
    }
    _cloneInto(to) {
        const { blockLen, leafLen, leafHash, outputLen, rounds } = this;
        const personalization = this.personalization === EMPTY_BUFFER ? EMPTY_BUFFER : copyBytes(this.personalization);
        // Personalization is absorbed only during finish(), so clones need the same pending value.
        to ||= new _KangarooTwelve(blockLen, leafLen, outputLen, rounds, {
            personalization,
        });
        super._cloneInto(to);
        // Reused destinations can carry a stale leaf from an older multi-chunk state.
        if (leafHash)
            to.leafHash = leafHash._cloneInto(to.leafHash);
        else if (to.leafHash) {
            to.leafHash.destroy();
            to.leafHash = undefined;
        }
        // Snapshot the pending personalization so clone state does not alias caller-owned input.
        to.personalization = personalization;
        to.leafLen = this.leafLen;
        to.chunkPos = this.chunkPos;
        to.chunksDone = this.chunksDone;
        return to;
    }
    clone() {
        return this._cloneInto();
    }
}
/**
 * 128-bit KangarooTwelve (k12): reduced 12-round keccak.
 * @param msg - message bytes to hash
 * @param opts - Optional output and personalization settings. Defaults to
 *   32 output bytes when `dkLen` is omitted. See {@link KangarooOpts}.
 * @returns Digest bytes.
 * @example
 * Hash a message with KangarooTwelve-128.
 * ```ts
 * kt128(new Uint8Array([1, 2, 3]));
 * ```
 */
export const kt128 = /* @__PURE__ */ createHasher((opts = {}) => new _KangarooTwelve(168, 32, chooseLen(opts, 32), 12, opts));
/**
 * 256-bit KangarooTwelve (k12): reduced 12-round keccak.
 * @param msg - message bytes to hash
 * @param opts - Optional output and personalization settings. Defaults to
 *   64 output bytes when `dkLen` is omitted. See {@link KangarooOpts}.
 * @returns Digest bytes.
 * @example
 * Hash a message with KangarooTwelve-256.
 * ```ts
 * kt256(new Uint8Array([1, 2, 3]));
 * ```
 */
export const kt256 = /* @__PURE__ */ createHasher((opts = {}) => new _KangarooTwelve(136, 64, chooseLen(opts, 64), 12, opts));
const genHopMAC = (hash) => (key, message, personalization, dkLen) => {
    const h = hash;
    return h(key, { personalization: h(message, { personalization }), dkLen });
};
/**
 * 128-bit KangarooTwelve-based MAC.
 *
 * These untested (there is no test vectors or implementation available). Use at your own risk.
 * HopMAC128(Key, M, C, L) = KT128(Key, KT128(M, C, 32), L)
 * HopMAC256(Key, M, C, L) = KT256(Key, KT256(M, C, 64), L)
 * The inner KangarooTwelve call always uses a fixed 32-byte digest here,
 * regardless of the outer `dkLen`.
 * @param key - MAC key bytes
 * @param message - message bytes to authenticate
 * @param personalization - personalization bytes mixed into the inner hash
 * @param dkLen - optional output length in bytes
 * @returns Authentication tag bytes.
 * @example
 * Authenticate a message with HopMAC128.
 * ```ts
 * HopMAC128(new Uint8Array([1]), new Uint8Array([2]), new Uint8Array([3]), 32);
 * ```
 */
export const HopMAC128 = /* @__PURE__ */ genHopMAC(kt128);
/**
 * 256-bit KangarooTwelve-based MAC.
 * Like `HopMAC128`, there are no test vectors or known independent
 * implementations available for cross-checking.
 * @param key - MAC key bytes
 * @param message - message bytes to authenticate
 * @param personalization - personalization bytes mixed into the inner hash
 * @param dkLen - optional output length in bytes. The inner KangarooTwelve
 *   call still uses a fixed 64-byte digest here, regardless of the outer
 *   `dkLen`.
 * @returns Authentication tag bytes.
 * @example
 * Authenticate a message with HopMAC256.
 * ```ts
 * HopMAC256(new Uint8Array([1]), new Uint8Array([2]), new Uint8Array([3]), 64);
 * ```
 */
export const HopMAC256 = /* @__PURE__ */ genHopMAC(kt256);
/**
 * More at
 * {@link https://github.com/XKCP/XKCP/tree/master/lib/high/Keccak/PRG}.
 * Accepted capacities must keep `rho = 1598 - capacity` byte-aligned, and
 * `.clean()` later also requires `rate > 801`.
 */
export class _KeccakPRG extends Keccak {
    rate;
    constructor(capacity) {
        anumber(capacity);
        const rate = 1600 - capacity;
        const rho = rate - 2;
        // Rho must be full bytes
        if (capacity < 0 || capacity > 1600 - 10 || rho % 8)
            throw new Error('invalid capacity');
        // blockLen = rho in bytes
        super(rho / 8, 0, 0, true);
        this.rate = rate;
        this.posOut = Math.floor((rate + 7) / 8);
    }
    keccak() {
        // Duplex padding
        this.state[this.pos] ^= 0x01;
        this.state[this.blockLen] ^= 0x02; // Rho is full bytes
        super.keccak();
        this.pos = 0;
        this.posOut = 0;
    }
    update(data) {
        super.update(data);
        this.posOut = this.blockLen;
        return this;
    }
    finish() { }
    digestInto(_out) {
        throw new Error('digest is not allowed, use .randomBytes() instead');
    }
    addEntropy(seed) {
        this.update(seed);
    }
    randomBytes(length) {
        return this.xof(length);
    }
    clean() {
        // clean() mutates live sponge state just like randomBytes(),
        // so destroyed instances must reject it.
        aexists(this, false);
        if (this.rate < 1600 / 2 + 1)
            throw new Error('rate is too low to use .forget()');
        this.keccak();
        for (let i = 0; i < this.blockLen; i++)
            this.state[i] = 0;
        this.pos = this.blockLen;
        this.keccak();
        this.posOut = this.blockLen;
    }
    _cloneInto(to) {
        const { rate } = this;
        to ||= new _KeccakPRG(1600 - rate);
        super._cloneInto(to);
        to.rate = rate;
        return to;
    }
    clone() {
        return this._cloneInto();
    }
}
/**
 * KeccakPRG: pseudo-random generator based on Keccak.
 * See {@link https://keccak.team/files/CSF-0.1.pdf}.
 * @param capacity - sponge capacity in bits. Accepted values are those that
 *   keep `rho = 1598 - capacity` byte-aligned; the default `254` is chosen
 *   because it satisfies that duplex layout while leaving a wide byte-aligned
 *   rate.
 * @returns PRG instance backed by a Keccak sponge.
 * @example
 * Create a Keccak-based pseudorandom generator and read bytes from it.
 * ```ts
 * const prg = keccakprg(254);
 * prg.randomBytes(8);
 * ```
 */
export const keccakprg = (capacity = 254) => new _KeccakPRG(capacity);
//# sourceMappingURL=sha3-addons.js.map