'use strict';

const crypto = require('crypto');
const net = require('net');

const ACCESS_CONTROL_MODES = new Set(['off', 'blacklist']);
const ACCESS_INTERNAL_PATHS = new Set(['/__health', '/__shutdown', '/__access-control', '/__outbound', '/__source-probe']);
const ACCESS_TRACK_MAX_DEVICES = 240;

function normalizeClientIp(rawIp) {
  let value = String(rawIp || '').trim().toLowerCase();
  if (!value) return '';
  if (value.includes(',')) value = value.split(',')[0].trim();

  if (value.startsWith('[')) {
    const closeIndex = value.indexOf(']');
    if (closeIndex > 0) value = value.substring(1, closeIndex);
  }

  const zoneIndex = value.indexOf('%');
  if (zoneIndex > 0) value = value.substring(0, zoneIndex);

  if (/^\d{1,3}(?:\.\d{1,3}){3}:\d+$/.test(value)) value = value.split(':')[0];

  if (value.startsWith('::ffff:')) {
    const mapped = value.substring(7);
    if (net.isIP(mapped) === 4) return mapped;
  }

  if (net.isIP(value) > 0) return value;
  return '';
}

function normalizeIpList(rawList) {
  const source = Array.isArray(rawList)
    ? rawList
    : typeof rawList === 'string'
      ? rawList.split(/[\s,;]+/)
      : [];
  const seen = new Set();
  const result = [];
  for (const item of source) {
    const ip = normalizeClientIp(item);
    if (!ip || seen.has(ip)) continue;
    seen.add(ip);
    result.push(ip);
  }
  return result;
}

function normalizeAccessMode(raw, fallback = 'off') {
  const mode = String(raw || '').trim().toLowerCase();
  if (!mode) return fallback;
  if (mode === 'blacklist' || mode === 'black' || mode === 'block' || mode === 'deny') return 'blacklist';
  if (mode === 'whitelist' || mode === 'white' || mode === 'allow') return 'off';
  if (mode === 'off' || mode === 'none' || mode === 'disable' || mode === 'disabled') return 'off';
  return fallback;
}

function isLoopbackIp(ip) {
  const raw = normalizeClientIp(ip);
  if (!raw) return false;
  if (raw === '::1' || raw === '0:0:0:0:0:0:0:1') return true;
  if (raw.startsWith('127.')) return true;
  if (raw.startsWith('::ffff:127.')) return true;
  return false;
}

function readClientIp(req) {
  const forwarded = req.headers['x-forwarded-for'];
  if (typeof forwarded === 'string' && forwarded) return normalizeClientIp(forwarded);
  const realIp = req.headers['x-real-ip'];
  if (typeof realIp === 'string' && realIp) return normalizeClientIp(realIp);
  return normalizeClientIp(req.socket?.remoteAddress || '');
}

function createAccessControl(options) {
  const fileSystem = options.fs;
  const file = options.file;
  const ensureDirs = options.ensureDirs;
  const stripTokenPrefix = options.stripTokenPrefix;
  const isAdminAuthorized = options.isAdminAuthorized;
  const sendJson = options.sendJson;
  const readJsonBody = options.readJsonBody;
  const log = options.log || (() => {});
  const now = options.now || Date.now;
  const randomBytes = options.randomBytes || crypto.randomBytes;

  let accessControl = { mode: 'off', blacklist: new Set(), updatedAtMs: 0 };
  let allowCount = 0;
  let blockCount = 0;
  const devices = new Map();

  function snapshot() {
    const updatedAtMs = Number(accessControl.updatedAtMs || now()) || now();
    return {
      mode: accessControl.mode || 'off',
      blacklist: Array.from(accessControl.blacklist || []).sort(),
      updatedAtMs,
      updatedAt: new Date(updatedAtMs).toISOString(),
    };
  }

  function sanitize(raw, fallback = null, strictMode = false) {
    const base = fallback || snapshot();
    const data = raw && typeof raw === 'object' ? raw : {};

    let mode = base.mode;
    if (Object.prototype.hasOwnProperty.call(data, 'mode')) {
      const parsed = normalizeAccessMode(data.mode, '');
      if (!parsed) {
        if (strictMode) throw new Error('mode 仅支持 off / blacklist');
      } else {
        mode = parsed;
      }
    }
    if (!ACCESS_CONTROL_MODES.has(mode)) mode = 'off';

    const blacklist = Object.prototype.hasOwnProperty.call(data, 'blacklist')
      ? normalizeIpList(data.blacklist)
      : normalizeIpList(base.blacklist);
    const updatedAtMs = Number(data.updatedAtMs || base.updatedAtMs || now()) || now();
    return { mode, blacklist, updatedAtMs };
  }

  function apply(next) {
    accessControl = {
      mode: normalizeAccessMode(next?.mode, 'off'),
      blacklist: new Set(normalizeIpList(next?.blacklist)),
      updatedAtMs: Number(next?.updatedAtMs || now()) || now(),
    };
  }

  function persist() {
    try {
      ensureDirs();
      const next = {
        mode: accessControl.mode || 'off',
        blacklist: Array.from(accessControl.blacklist || []).sort(),
        updatedAtMs: now(),
      };
      apply(next);
      const temporary = `${file}.${randomBytes(6).toString('hex')}.tmp`;
      try {
        fileSystem.writeFileSync(temporary, `${JSON.stringify(next, null, 2)}\n`, 'utf-8');
        fileSystem.renameSync(temporary, file);
      } finally {
        try { fileSystem.unlinkSync(temporary); } catch {}
      }
      return true;
    } catch (error) {
      log('Failed to persist access-control config:', error?.message || error);
      return false;
    }
  }

  function loadFromDisk() {
    try {
      if (!fileSystem.existsSync(file)) {
        if (!ACCESS_CONTROL_MODES.has(accessControl.mode)) {
          apply({ mode: 'off', blacklist: [], updatedAtMs: now() });
        }
        return;
      }
      const raw = fileSystem.readFileSync(file, 'utf-8');
      const parsed = raw.trim() ? JSON.parse(raw) : {};
      apply(sanitize(parsed, { mode: 'off', blacklist: [], updatedAtMs: now() }));
    } catch (error) {
      log('Failed to load access-control config:', error?.message || error);
    }
  }

  function isInternalPath(pathname) {
    const stripped = stripTokenPrefix(String(pathname || '/'));
    return ACCESS_INTERNAL_PATHS.has(stripped);
  }

  function evaluate(clientIp, pathname) {
    const normalizedIp = normalizeClientIp(clientIp);
    const mode = accessControl.mode || 'off';

    if (isInternalPath(pathname)) return { allowed: true, reason: 'internal', clientIp: normalizedIp };
    if (isLoopbackIp(normalizedIp)) return { allowed: true, reason: 'loopback', clientIp: normalizedIp };
    if (mode === 'off') return { allowed: true, reason: 'mode_off', clientIp: normalizedIp };
    if (mode === 'blacklist') {
      if (normalizedIp && accessControl.blacklist.has(normalizedIp)) {
        return { allowed: false, reason: 'blacklist_hit', clientIp: normalizedIp };
      }
      return { allowed: true, reason: 'blacklist_pass', clientIp: normalizedIp };
    }
    return { allowed: true, reason: 'unknown_mode', clientIp: normalizedIp };
  }

  function evictOldestDevice() {
    let oldestKey = '';
    let oldestSeen = Number.MAX_SAFE_INTEGER;
    for (const [ip, item] of devices.entries()) {
      const timestamp = Number(item.lastSeenAtMs || 0);
      if (timestamp < oldestSeen) {
        oldestSeen = timestamp;
        oldestKey = ip;
      }
    }
    if (oldestKey) devices.delete(oldestKey);
  }

  function recordDeviceAccess({ clientIp, method, pathname, allowed, reason, userAgent }) {
    const ip = normalizeClientIp(clientIp);
    if (!ip || isLoopbackIp(ip)) return;

    const timestamp = now();
    let item = devices.get(ip);
    if (!item) {
      if (devices.size >= ACCESS_TRACK_MAX_DEVICES) evictOldestDevice();
      item = {
        ip,
        firstSeenAtMs: timestamp,
        lastSeenAtMs: timestamp,
        totalRequests: 0,
        allowedRequests: 0,
        blockedRequests: 0,
        lastMethod: 'GET',
        lastPath: '/',
        lastReason: '',
        lastUserAgent: '',
      };
      devices.set(ip, item);
    }

    item.lastSeenAtMs = timestamp;
    item.totalRequests += 1;
    if (allowed) item.allowedRequests += 1;
    else item.blockedRequests += 1;
    item.lastMethod = String(method || 'GET').toUpperCase();
    item.lastPath = String(pathname || '/');
    item.lastReason = String(reason || '');
    if (userAgent) item.lastUserAgent = String(userAgent).slice(0, 220);

    if (allowed) allowCount += 1;
    else blockCount += 1;
  }

  function buildDevicesPayload() {
    const mode = accessControl.mode || 'off';
    const blacklist = accessControl.blacklist || new Set();
    const list = [];
    for (const item of devices.values()) {
      const inBlacklist = blacklist.has(item.ip);
      list.push({
        ip: item.ip,
        firstSeenAtMs: item.firstSeenAtMs,
        firstSeenAt: new Date(item.firstSeenAtMs).toISOString(),
        lastSeenAtMs: item.lastSeenAtMs,
        lastSeenAt: new Date(item.lastSeenAtMs).toISOString(),
        totalRequests: item.totalRequests,
        allowedRequests: item.allowedRequests,
        blockedRequests: item.blockedRequests,
        lastMethod: item.lastMethod,
        lastPath: item.lastPath,
        lastReason: item.lastReason,
        lastUserAgent: item.lastUserAgent,
        inWhitelist: false,
        inBlacklist,
        effectiveBlocked: !isLoopbackIp(item.ip) && mode === 'blacklist' && inBlacklist,
      });
    }
    list.sort((left, right) => right.lastSeenAtMs - left.lastSeenAtMs);
    return list;
  }

  function buildPayload() {
    const current = snapshot();
    return {
      success: true,
      config: {
        mode: current.mode,
        whitelist: [],
        blacklist: current.blacklist,
        updatedAtMs: current.updatedAtMs,
        updatedAt: current.updatedAt,
        file,
      },
      stats: {
        trackedDevices: devices.size,
        whitelistCount: 0,
        blacklistCount: current.blacklist.length,
        totalAllowedRequests: allowCount,
        totalBlockedRequests: blockCount,
      },
      devices: buildDevicesPayload(),
    };
  }

  async function handleEndpoint(req, res, urlObj, clientIp) {
    if (!isAdminAuthorized(req, urlObj, clientIp)) {
      sendJson(res, 403, { success: false, errorCode: 403, errorMessage: 'Forbidden' });
      return true;
    }

    const method = String(req.method || 'GET').toUpperCase();
    if (method === 'GET') {
      sendJson(res, 200, buildPayload());
      return true;
    }
    if (!['POST', 'PUT', 'PATCH'].includes(method)) {
      sendJson(res, 405, { success: false, errorCode: 405, errorMessage: 'Method Not Allowed' });
      return true;
    }

    try {
      const body = await readJsonBody(req);
      if (body && body.clearDevices === true) devices.clear();

      const next = sanitize(body, snapshot(), true);
      if (body && body.clearRules === true) next.blacklist = [];
      next.updatedAtMs = now();
      apply(next);
      if (!persist()) throw new Error('保存访问控制配置失败');
      sendJson(res, 200, buildPayload());
    } catch (error) {
      sendJson(res, 400, { success: false, errorCode: 400, errorMessage: error?.message || '参数错误' });
    }
    return true;
  }

  return {
    file,
    snapshot,
    sanitize,
    apply,
    persist,
    loadFromDisk,
    isInternalPath,
    evaluate,
    recordDeviceAccess,
    buildPayload,
    handleEndpoint,
    stats: () => ({ trackedDevices: devices.size, totalAllowedRequests: allowCount, totalBlockedRequests: blockCount }),
    config: () => ({ mode: accessControl.mode || 'off', blacklistCount: accessControl.blacklist.size }),
    clearDevices: () => devices.clear(),
  };
}

module.exports = {
  ACCESS_CONTROL_MODES,
  ACCESS_INTERNAL_PATHS,
  ACCESS_TRACK_MAX_DEVICES,
  createAccessControl,
  isLoopbackIp,
  normalizeAccessMode,
  normalizeClientIp,
  normalizeIpList,
  readClientIp,
};
