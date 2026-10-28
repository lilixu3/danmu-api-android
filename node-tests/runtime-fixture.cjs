'use strict';

const fs = require('node:fs');
const path = require('node:path');

const MANIFEST_NAME = 'app-runtime-assets.json';

function normalizeRuntimePath(raw) {
  if (typeof raw !== 'string') throw new Error(`Runtime manifest entry is not a string: ${raw}`);
  const value = raw.trim().replaceAll('\\', '/');
  if (!value || value.startsWith('/')) throw new Error(`Invalid runtime path: ${raw}`);
  const segments = value.split('/');
  if (segments.some(segment => !segment || segment === '.' || segment === '..')) {
    throw new Error(`Runtime path escapes its root: ${raw}`);
  }
  return segments.join('/');
}

function runtimeAssetEntries(runtimeRoot) {
  const manifestPath = path.join(runtimeRoot, MANIFEST_NAME);
  const parsed = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  if (!Array.isArray(parsed) || parsed.length === 0) {
    throw new Error('app-runtime-assets.json must be a non-empty array');
  }
  const entries = parsed.map(normalizeRuntimePath);
  if (new Set(entries).size !== entries.length) throw new Error('Runtime manifest contains duplicates');
  if (entries.includes(MANIFEST_NAME)) throw new Error('Runtime manifest must not contain itself');
  const missing = entries.filter(entry => !fs.statSync(path.join(runtimeRoot, entry), { throwIfNoEntry: false })?.isFile());
  if (missing.length) throw new Error(`Runtime manifest entries are missing: ${missing.join(', ')}`);
  return entries;
}

function copyRuntimeAssets(runtimeRoot, destinationRoot) {
  const entries = [MANIFEST_NAME, ...runtimeAssetEntries(runtimeRoot)];
  for (const entry of entries) {
    const destination = path.join(destinationRoot, entry);
    fs.mkdirSync(path.dirname(destination), { recursive: true });
    fs.copyFileSync(path.join(runtimeRoot, entry), destination);
  }
  return entries;
}

module.exports = { copyRuntimeAssets, normalizeRuntimePath, runtimeAssetEntries };
