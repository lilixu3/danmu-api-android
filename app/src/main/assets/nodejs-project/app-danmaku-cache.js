'use strict';

const PREPARED_DANMAKU_TTL_MS = 2 * 60 * 1000;
const PREPARED_DANMAKU_MAX_ENTRIES = 8;
const PREPARED_DANMAKU_MAX_ENTRY_BYTES = 16 * 1024 * 1024;
const PREPARED_DANMAKU_MAX_TOTAL_BYTES = 32 * 1024 * 1024;
const DANMAKU_OVERRIDE_KEYS = new Set([
  'offset', 'danmu_offset', 'offsetMs', 'offset_ms',
  'fontSize', 'font_size', 'fontsize',
]);

function countDanmakuXml(xml) {
  const text = String(xml || '');
  let count = 0;
  let index = 0;
  while ((index = text.indexOf('<d', index)) >= 0) {
    const next = text.charAt(index + 2);
    if (next === '>' || /\s/.test(next)) count++;
    index += 2;
  }
  return count;
}

function looksLikeDanmakuXml(contentType, text) {
  const type = String(contentType || '').toLowerCase();
  const value = String(text || '').trimStart();
  if (!value.startsWith('<')) return false;
  return type.includes('xml') || value.startsWith('<?xml') ||
    value.startsWith('<i>') || value.startsWith('<i ') ||
    value.startsWith('<d>') || value.startsWith('<d ');
}

function preparedDanmakuError(message, statusCode = 500) {
  const error = new Error(message);
  error.statusCode = statusCode;
  return error;
}

function normalizeNumber(value, digits = 3) {
  if (!Number.isFinite(value)) return null;
  const fixed = Number(value).toFixed(digits);
  return fixed.replace(/\.?0+$/, '');
}

function applyDanmuXmlOverrides(xml, { offsetSec = 0, fontSize = null } = {}) {
  const offset = Number(offsetSec);
  const hasOffset = Number.isFinite(offset) && Math.abs(offset) > 1e-6;

  let fontSizeInt = null;
  if (fontSize !== null && typeof fontSize !== 'undefined') {
    const parsed = Number.parseInt(String(fontSize), 10);
    if (Number.isFinite(parsed) && parsed > 0) fontSizeInt = parsed;
  }
  const hasFont = fontSizeInt !== null;

  if (!hasOffset && !hasFont) return xml;
  if (typeof xml !== 'string' || !xml.includes('<d')) return xml;

  return xml.replace(/(<d[^>]*?\sp=")([^"]+)(")/g, (match, prefix, pValue, suffix) => {
    const parts = String(pValue).split(',');
    if (parts.length < 3) return match;

    if (hasOffset) {
      const time = Number(parts[0]);
      if (Number.isFinite(time)) {
        let nextTime = time + offset;
        if (nextTime < 0) nextTime = 0;
        const normalized = normalizeNumber(nextTime);
        if (normalized !== null) parts[0] = normalized;
      }
    }

    if (hasFont) parts[2] = String(fontSizeInt);
    return prefix + parts.join(',') + suffix;
  });
}

function readDanmakuOverrides(urlObj) {
  const offsetMsRaw = urlObj.searchParams.get('offsetMs') || urlObj.searchParams.get('offset_ms');
  const offsetRaw = urlObj.searchParams.get('offset') || urlObj.searchParams.get('danmu_offset');
  const fontRaw = urlObj.searchParams.get('fontSize') || urlObj.searchParams.get('font_size') ||
    urlObj.searchParams.get('fontsize');
  let offsetSec = 0;
  if (offsetMsRaw && String(offsetMsRaw).trim() !== '') {
    const milliseconds = Number.parseFloat(String(offsetMsRaw));
    if (Number.isFinite(milliseconds)) offsetSec = milliseconds / 1000.0;
  } else if (offsetRaw && String(offsetRaw).trim() !== '') {
    const seconds = Number.parseFloat(String(offsetRaw));
    if (Number.isFinite(seconds)) offsetSec = seconds;
  }
  let fontSize = null;
  if (fontRaw && String(fontRaw).trim() !== '') {
    const parsed = Number.parseInt(String(fontRaw), 10);
    if (Number.isFinite(parsed) && parsed > 0) fontSize = parsed;
  }
  return { offsetSec, fontSize };
}

function createDanmakuCache(options) {
  const sendJson = options.sendJson;
  const isAdminAuthorized = options.isAdminAuthorized;
  const stripTokenPrefix = options.stripTokenPrefix;
  const getRequestHandler = options.getRequestHandler;
  const getEnvSnapshot = options.getEnvSnapshot;
  const port = options.port || (() => options.portNumber);
  const now = options.now || Date.now;
  const ttlMs = options.ttlMs ?? PREPARED_DANMAKU_TTL_MS;
  const maxEntries = options.maxEntries ?? PREPARED_DANMAKU_MAX_ENTRIES;
  const maxEntryBytes = options.maxEntryBytes ?? PREPARED_DANMAKU_MAX_ENTRY_BYTES;
  const maxTotalBytes = options.maxTotalBytes ?? PREPARED_DANMAKU_MAX_TOTAL_BYTES;

  const entries = new Map();
  const bySource = new Map();
  const inflight = new Map();
  let totalBytes = 0;
  let sequence = 1;

  function deleteEntry(id) {
    const entry = entries.get(id);
    if (!entry) return;
    entries.delete(id);
    if (bySource.get(entry.sourceUrl) === id) bySource.delete(entry.sourceUrl);
    totalBytes = Math.max(0, totalBytes - entry.body.length);
  }

  function cleanup(current = now()) {
    for (const [id, entry] of entries.entries()) {
      if (entry.expiresAtMs <= current) deleteEntry(id);
    }
    while (entries.size > maxEntries || totalBytes > maxTotalBytes) {
      const oldest = entries.keys().next().value;
      if (!oldest) break;
      deleteEntry(oldest);
    }
  }

  function touch(id) {
    const entry = entries.get(id);
    if (!entry) return null;
    entries.delete(id);
    entries.set(id, entry);
    return entry;
  }

  function getBySource(sourceUrl) {
    cleanup();
    const id = bySource.get(sourceUrl);
    return id ? touch(id) : null;
  }

  function getById(id) {
    cleanup();
    return touch(id);
  }

  function resolvePort() {
    return typeof port === 'function' ? port() : port;
  }

  function normalizeTarget(rawUrl) {
    let target;
    try {
      target = new URL(String(rawUrl || '').trim());
    } catch {
      throw preparedDanmakuError('缺少或无法解析弹幕 URL', 400);
    }
    if (target.protocol !== 'http:') {
      throw preparedDanmakuError('预取仅支持本机 HTTP 弹幕 URL', 400);
    }
    if (target.username || target.password) {
      throw preparedDanmakuError('弹幕 URL 不允许包含用户信息', 400);
    }
    const hostname = String(target.hostname || '').toLowerCase();
    const isLoopbackHost = hostname === 'localhost' || hostname === '::1' ||
      hostname === '[::1]' || hostname.startsWith('127.');
    const targetPort = Number(target.port || 80);
    if (!isLoopbackHost || targetPort !== resolvePort()) {
      throw preparedDanmakuError('预取只允许访问当前核心服务', 400);
    }
    const pathname = stripTokenPrefix(target.pathname || '/');
    if (pathname !== '/api/v2/comment' && !pathname.startsWith('/api/v2/comment/')) {
      throw preparedDanmakuError('预取只允许弹幕评论接口', 400);
    }
    target.hash = '';
    return target;
  }

  async function loadPrepared(target, requestHeaders, clientIp) {
    const handler = getRequestHandler();
    if (typeof handler !== 'function') {
      throw preparedDanmakuError('核心弹幕处理器尚未就绪', 503);
    }
    const { offsetSec, fontSize } = readDanmakuOverrides(target);
    const cleanUrl = new URL(target.toString());
    for (const key of DANMAKU_OVERRIDE_KEYS) cleanUrl.searchParams.delete(key);

    const headers = { ...requestHeaders, host: cleanUrl.host };
    delete headers['content-length'];
    delete headers['content-encoding'];
    const coreResponse = await handler(new Request(cleanUrl.toString(), {
      method: 'GET',
      headers,
    }), getEnvSnapshot(), 'node', clientIp);
    const contentType = String(coreResponse.headers.get('content-type') || '');
    const rawText = await coreResponse.text();
    if (coreResponse.status < 200 || coreResponse.status > 299) {
      const summary = rawText.trim().replace(/\s+/g, ' ').slice(0, 160);
      throw preparedDanmakuError(`核心弹幕接口返回 HTTP ${coreResponse.status}${summary ? `: ${summary}` : ''}`, 502);
    }

    const outText = applyDanmuXmlOverrides(rawText, { offsetSec, fontSize });
    if (!looksLikeDanmakuXml(contentType, outText)) {
      throw preparedDanmakuError('核心弹幕接口未返回有效 XML', 502);
    }
    const body = Buffer.from(outText, 'utf-8');
    if (body.length > maxEntryBytes) {
      throw preparedDanmakuError('弹幕 XML 超出预取缓存上限', 413);
    }
    return {
      sourceUrl: target.toString(),
      body,
      count: countDanmakuXml(outText),
      contentType: contentType.toLowerCase().includes('xml')
        ? contentType
        : 'application/xml; charset=utf-8',
    };
  }

  function cache(payload) {
    const oldId = bySource.get(payload.sourceUrl);
    if (oldId) deleteEntry(oldId);
    const id = `${now().toString(36)}-${(sequence++).toString(36)}`;
    const entry = {
      ...payload,
      id,
      expiresAtMs: now() + ttlMs,
    };
    entries.set(id, entry);
    bySource.set(entry.sourceUrl, id);
    totalBytes += entry.body.length;
    cleanup();
    return entry;
  }

  async function prepare(rawUrl, requestHeaders, clientIp) {
    const target = normalizeTarget(rawUrl);
    const sourceUrl = target.toString();
    const cached = getBySource(sourceUrl);
    if (cached) return { entry: cached, cached: true };

    let pending = inflight.get(sourceUrl);
    if (!pending) {
      pending = loadPrepared(target, requestHeaders, clientIp).then(cache);
      inflight.set(sourceUrl, pending);
    }
    try {
      return { entry: await pending, cached: false };
    } finally {
      if (inflight.get(sourceUrl) === pending) inflight.delete(sourceUrl);
    }
  }

  async function handlePrepare(req, res, fullUrl, clientIp, headers) {
    if (!isAdminAuthorized(req, fullUrl, clientIp)) {
      sendJson(res, 403, { success: false, errorMessage: 'Forbidden' });
      return;
    }
    if (String(req.method || 'GET').toUpperCase() !== 'GET') {
      sendJson(res, 405, { success: false, errorMessage: 'Method Not Allowed' });
      return;
    }
    try {
      const result = await prepare(fullUrl.searchParams.get('url'), headers, clientIp);
      const entry = result.entry;
      sendJson(res, 200, {
        success: true,
        url: `http://127.0.0.1:${resolvePort()}/__danmaku/prepared/${entry.id}.xml`,
        count: entry.count,
        size: entry.body.length,
        expiresAt: entry.expiresAtMs,
        cached: result.cached,
      });
    } catch (error) {
      sendJson(res, Number(error?.statusCode || 500), {
        success: false,
        errorMessage: error?.message || '弹幕预取失败',
      });
    }
  }

  function handlePrepared(req, res, strippedPathname, fullUrl, clientIp) {
    if (!isAdminAuthorized(req, fullUrl, clientIp)) {
      sendJson(res, 403, { success: false, errorMessage: 'Forbidden' });
      return;
    }
    const method = String(req.method || 'GET').toUpperCase();
    if (method !== 'GET' && method !== 'HEAD') {
      sendJson(res, 405, { success: false, errorMessage: 'Method Not Allowed' });
      return;
    }
    const prefix = '/__danmaku/prepared/';
    const fileName = strippedPathname.substring(prefix.length);
    const id = fileName.endsWith('.xml') ? fileName.slice(0, -4) : '';
    if (!/^[a-z0-9-]+$/.test(id)) {
      sendJson(res, 404, { success: false, errorMessage: 'Prepared danmaku not found' });
      return;
    }
    const entry = getById(id);
    if (!entry) {
      sendJson(res, 404, { success: false, errorMessage: 'Prepared danmaku expired' });
      return;
    }
    res.statusCode = 200;
    res.setHeader('content-type', entry.contentType);
    res.setHeader('content-length', String(entry.body.length));
    res.setHeader('cache-control', 'no-store');
    res.setHeader('x-danmaku-count', String(entry.count));
    res.end(method === 'HEAD' ? undefined : entry.body);
  }

  return {
    cleanup,
    getById,
    getBySource,
    handlePrepare,
    handlePrepared,
    prepare,
  };
}

module.exports = {
  DANMAKU_OVERRIDE_KEYS,
  PREPARED_DANMAKU_MAX_ENTRIES,
  PREPARED_DANMAKU_MAX_ENTRY_BYTES,
  PREPARED_DANMAKU_MAX_TOTAL_BYTES,
  PREPARED_DANMAKU_TTL_MS,
  applyDanmuXmlOverrides,
  countDanmakuXml,
  createDanmakuCache,
  looksLikeDanmakuXml,
  normalizeNumber,
  preparedDanmakuError,
  readDanmakuOverrides,
};
