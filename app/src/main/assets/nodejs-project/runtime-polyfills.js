'use strict';

/**
 * Node 18 compatibility for ECMAScript helpers used by bundled core dependencies.
 * Install non-enumerable shims only when the embedded runtime has no native implementation.
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

defineMethod(Array.prototype, 'toReversed', function toReversed() {
  return Array.from(this).reverse();
});

defineMethod(Array.prototype, 'toSorted', function toSorted(compareFn) {
  return Array.from(this).sort(compareFn);
});

const iteratorPrototype = Object.getPrototypeOf(
  Object.getPrototypeOf([][Symbol.iterator]()),
);

defineMethod(iteratorPrototype, 'map', function map(mapper) {
  if (typeof mapper !== 'function') throw new TypeError('mapper must be a function');
  const source = this;
  return (function* mappedIterator() {
    let index = 0;
    for (const value of source) {
      yield mapper(value, index++);
    }
  })();
});

defineMethod(iteratorPrototype, 'filter', function filter(predicate) {
  if (typeof predicate !== 'function') throw new TypeError('predicate must be a function');
  const source = this;
  return (function* filteredIterator() {
    let index = 0;
    for (const value of source) {
      if (predicate(value, index++)) yield value;
    }
  })();
});

defineMethod(iteratorPrototype, 'reduce', function reduce(reducer, initialValue) {
  if (typeof reducer !== 'function') throw new TypeError('reducer must be a function');
  let accumulator = initialValue;
  let hasAccumulator = arguments.length > 1;
  let index = 0;
  for (const value of this) {
    if (!hasAccumulator) {
      accumulator = value;
      hasAccumulator = true;
    } else {
      accumulator = reducer(accumulator, value, index);
    }
    index += 1;
  }
  if (!hasAccumulator) throw new TypeError('Reduce of empty iterator with no initial value');
  return accumulator;
});

defineMethod(iteratorPrototype, 'some', function some(predicate) {
  if (typeof predicate !== 'function') throw new TypeError('predicate must be a function');
  let index = 0;
  for (const value of this) {
    if (predicate(value, index++)) return true;
  }
  return false;
});

defineMethod(iteratorPrototype, 'toArray', function toArray() {
  return Array.from(this);
});

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
