'use strict';

const net = require('node:net');
const path = require('node:path');

const APP_RESERVED_ENV_KEYS = new Set(['DANMU_APP_OUTBOUND_CONFIG', 'DANMUAPI_LIBDIR']);
const DEFAULT_HOST = '0.0.0.0';
const DEFAULT_PORT = 9321;
const DEFAULT_PROXY_PORT = 5321;
const CONFIG_RELOAD_DELAY_MS = 500;
const CONFIG_STAT_THROTTLE_MS = 2000;

function parseDotEnv(envText) {
  const out = {};
  const lines = String(envText || '').split(/\r?\n/);
  for (const rawLine of lines) {
    const line = rawLine.trim().replace(/^\uFEFF/, '');
    if (!line || line.startsWith('#')) continue;
    const eq = line.indexOf('=');
    if (eq <= 0) continue;
    const key = line.slice(0, eq).trim();
    let val = line.slice(eq + 1).trim();
    if (val.startsWith('"') && val.endsWith('"')) {
      val = unescapeDoubleQuotedEnvValue(val.slice(1, -1));
    } else if (val.startsWith("'") && val.endsWith("'")) {
      val = val.slice(1, -1);
    }
    out[key] = val;
  }
  return out;
}

function unescapeDoubleQuotedEnvValue(value) {
  let out = '';
  for (let i = 0; i < value.length; i++) {
    const ch = value[i];
    if (ch !== '\\' || i === value.length - 1) {
      out += ch;
      continue;
    }

    const next = value[++i];
    if (next === '\\') out += '\\';
    else if (next === '"') out += '"';
    else if (next === 'n') out += '\n';
    else if (next === 'r') out += '\r';
    else if (next === 't') out += '\t';
    else out += `\\${next}`;
  }
  return out;
}

function applyEnv(env, kv, { override = true, reservedKeys = APP_RESERVED_ENV_KEYS } = {}) {
  for (const [k, v] of Object.entries(kv || {})) {
    if (reservedKeys.has(k)) continue;
    if (!override && env[k] !== undefined) continue;
    env[k] = v;
  }
}

function safePort(raw, def) {
  const n = Number(raw);
  if (!Number.isFinite(n)) return def;
  const p = Math.trunc(n);
  if (p < 1 || p > 65535) return def;
  return p;
}

function readListenConfig(env) {
  const host = String(env?.DANMU_API_HOST || DEFAULT_HOST).trim() || DEFAULT_HOST;
  return {
    host,
    port: safePort(env?.DANMU_API_PORT, DEFAULT_PORT),
    proxyPort: safePort(env?.DANMU_API_PROXY_PORT, DEFAULT_PROXY_PORT),
  };
}

function listenOptions(host, port) {
  const options = { port, host };
  if (net.isIP(host) === 6) options.ipv6Only = false;
  return options;
}

function listenUrl(host, port, pathSuffix = '') {
  const displayHost = net.isIP(host) === 6 ? `[${host}]` : host;
  return `http://${displayHost}:${port}${pathSuffix}`;
}

function createConfigRuntime(options) {
  const env = options.env;
  const fileSystem = options.fs;
  const home = options.home;
  const configDir = path.join(home, 'config');
  const envFile = path.join(configDir, '.env');
  const accessControlFile = path.join(configDir, 'access-control.json');
  const getBaseEnvSnapshot = options.getBaseEnvSnapshot || (() => ({ ...env }));
  const onReload = options.onReload || (() => {});
  const loadAccessControl = options.loadAccessControl || (() => {});
  const ensureDirs = options.ensureDirs || (() => {});
  const log = options.log || (() => {});
  const setTimer = options.setTimeout || setTimeout;
  const clearTimer = options.clearTimeout || clearTimeout;
  const now = options.now || Date.now;
  const reloadDelayMs = options.reloadDelayMs ?? CONFIG_RELOAD_DELAY_MS;
  const statThrottleMs = options.statThrottleMs ?? CONFIG_STAT_THROTTLE_MS;

  const baseEnvSnapshot = getBaseEnvSnapshot();
  let lastLoadedDotEnvKeys = new Set();
  let reloadTimer = null;
  let lastStatCheckMs = 0;
  let lastEnvMtimeMs = 0;
  let lastAccessControlMtimeMs = 0;
  const watchers = [];

  function readMtimeMs(file) {
    try {
      return fileSystem.statSync(file).mtimeMs || 0;
    } catch {
      return 0;
    }
  }

  function recordMtimes() {
    lastEnvMtimeMs = readMtimeMs(envFile);
    lastAccessControlMtimeMs = readMtimeMs(accessControlFile);
  }

  function restoreKeyFromBase(key) {
    if (Object.prototype.hasOwnProperty.call(baseEnvSnapshot, key)) {
      env[key] = baseEnvSnapshot[key];
    } else {
      delete env[key];
    }
  }

  function replaceDotEnv(kv) {
    const next = kv && typeof kv === 'object' ? kv : {};
    for (const key of lastLoadedDotEnvKeys) {
      if (!(key in next)) restoreKeyFromBase(key);
    }
    applyEnv(env, next, { override: true });
    lastLoadedDotEnvKeys = new Set(Object.keys(next));
  }

  function loadConfigOnce() {
    ensureDirs();
    if (fileSystem.existsSync(envFile)) {
      try {
        const text = fileSystem.readFileSync(envFile, 'utf-8');
        replaceDotEnv(parseDotEnv(text));
        log('Loaded .env:', envFile);
      } catch (error) {
        log('Failed to load .env:', error?.message || error);
      }
    } else {
      replaceDotEnv({});
      log('.env not found, skipping:', envFile);
    }
    loadAccessControl();
    recordMtimes();
  }

  function scheduleReload() {
    if (reloadTimer) clearTimer(reloadTimer);
    reloadTimer = setTimer(async () => {
      reloadTimer = null;
      log('Config changed, reloading ...');
      loadConfigOnce();
      try {
        await onReload();
      } catch (error) {
        log('Config reload failed:', error?.stack || error);
      }
    }, reloadDelayMs);
  }

  function maybeReloadOnTraffic() {
    const current = now();
    if (current - lastStatCheckMs < statThrottleMs) return false;
    lastStatCheckMs = current;

    const envMtime = readMtimeMs(envFile);
    const accessControlMtime = readMtimeMs(accessControlFile);
    if (envMtime > lastEnvMtimeMs || accessControlMtime > lastAccessControlMtimeMs) {
      scheduleReload();
      return true;
    }
    return false;
  }

  function watch() {
    try {
      if (fileSystem.existsSync(configDir)) {
        watchers.push(fileSystem.watch(configDir, { persistent: false }, () => scheduleReload()));
      }
    } catch (error) {
      log('fs.watch(CONFIG_DIR) unavailable:', error?.message || error);
    }

    for (const file of [envFile, accessControlFile]) {
      try {
        if (!fileSystem.existsSync(file)) continue;
        watchers.push(fileSystem.watch(file, { persistent: false }, () => scheduleReload()));
      } catch {
        log('fs.watch unavailable for', file, '- will reload on traffic');
      }
    }
  }

  function dispose() {
    if (reloadTimer) {
      clearTimer(reloadTimer);
      reloadTimer = null;
    }
    for (const watcher of watchers.splice(0)) {
      try { watcher.close(); } catch {}
    }
  }

  return {
    accessControlFile,
    configDir,
    dispose,
    envFile,
    get lastAccessControlMtimeMs() { return lastAccessControlMtimeMs; },
    get lastEnvMtimeMs() { return lastEnvMtimeMs; },
    loadConfigOnce,
    maybeReloadOnTraffic,
    parseDotEnv,
    replaceDotEnv,
    scheduleReload,
    watch,
  };
}

module.exports = {
  APP_RESERVED_ENV_KEYS,
  CONFIG_RELOAD_DELAY_MS,
  CONFIG_STAT_THROTTLE_MS,
  DEFAULT_HOST,
  DEFAULT_PORT,
  DEFAULT_PROXY_PORT,
  applyEnv,
  createConfigRuntime,
  listenOptions,
  listenUrl,
  parseDotEnv,
  readListenConfig,
  safePort,
  unescapeDoubleQuotedEnvValue,
};
