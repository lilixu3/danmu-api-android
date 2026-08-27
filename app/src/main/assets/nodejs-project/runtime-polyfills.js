'use strict';

/**
 * Node 24 compatibility shims for the bundled core dependencies.
 * Install non-enumerable shims only when the embedded runtime has no native implementation.
 *
 * 2026-08 (Node 24.19.0): Array.prototype.{toReversed,toSorted} and all
 * Iterator helpers (map/filter/reduce/some/toArray) are native now; only the
 * Map upsert proposal (getOrInsert/getOrInsertComputed) still lacks a native
 * implementation and is shimmed below.
 */
function defineMethod(target, name, implementation) {
  if (typeof target[name] === 'function') return;
  Object.defineProperty(target, name, {
    value: implementation,
    writable: true,
    configurable: true,
    enumerable: false,
  });
}

defineMethod(Map.prototype, 'getOrInsert', function getOrInsert(key, defaultValue) {
  if (this.has(key)) return this.get(key);
  this.set(key, defaultValue);
  return defaultValue;
});

defineMethod(
  Map.prototype,
  'getOrInsertComputed',
  function getOrInsertComputed(key, callback) {
    if (typeof callback !== 'function') throw new TypeError('callback must be a function');
    if (this.has(key)) return this.get(key);
    const value = callback(key);
    this.set(key, value);
    return value;
  },
);
