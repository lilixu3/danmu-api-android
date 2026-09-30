'use strict';
const http = require('node:http');
const https = require('node:https');
const { urlToHttpOptions } = require('node:url');
const { syncBuiltinESMExports } = require('node:module');
const SOURCES = new Map([
  ['api.gamer.com.tw', 'bahamut'],
  ['api.tmdb.org', 'tmdb'],
  ['api.themoviedb.org', 'tmdb'],
  ['api.danmaku.weeblify.app', 'dandan'],
  ['nipaplay.aimes-soft.com', 'dandan'],
  ['api.animeko.org', 'animeko'],
  ['danmaku-global.myani.org', 'animeko'],
  ['danmaku-cn.myani.org', 'animeko'],
  ['s1.animeko.openani.org', 'animeko'],
  ['api.bangumi.vip', 'animeko'],
]);
const PREFIX = 'x-danmu-outbound-';

function hasProxy(source, env) {
  return String(env.PROXY_URL || '').split(',').some(raw => {
    const entry = raw.trim();
    if (!entry) return false;
    if (entry.startsWith(source + '@') || entry.startsWith('@')) return true;
    return !entry.includes('@') || /^https?:\/\//i.test(entry);
  });
}
function eligible(target, snapshot, env, options = {}) {
  if (!snapshot?.config?.enabled) return false;
  const source = SOURCES.get(target.hostname);
  if (!source || target.protocol !== 'https:' || (target.port && target.port !== '443') || target.username || target.password) return false;
  if (!snapshot.config.sources.includes(source) || hasProxy(source, env)) return false;
  // Respect explicitly selected transports and proxy agents. Ordinary keepalive
  // agents remain eligible; pooling happens in Go for those requests.
  if (options.dispatcher || /proxy/i.test(options.agent?.constructor?.name || '')) return false;
  if (['ca', 'cert', 'key', 'createConnection', 'rejectUnauthorized'].some(key => key in options)) return false;
  return true;
}
function helperHeaders(headers, target, snapshot, budget) {
  const result = new Headers(headers);
  for (const name of [...result.keys()]) {
    if (name.startsWith(PREFIX) || ['host','connection','proxy-connection','proxy-authorization','transfer-encoding'].includes(name)) result.delete(name);
  }
  result.set('X-Danmu-Outbound-Token', snapshot.token);
  result.set('X-Danmu-Outbound-Target', target.href);
  result.set('X-Danmu-Outbound-Timeout', String(budget));
  return result;
}
async function readBody(request, deadline) {
  if (!request.body) return null;
  const signal = AbortSignal.any([request.signal, AbortSignal.timeout(Math.max(1, deadline - Date.now()))]);
  const reader = request.body.getReader();
  const chunks = []; let size = 0;
  const abort = () => { reader.cancel(signal.reason).catch(() => {}); };
  signal.addEventListener('abort', abort, {once:true});
  try {
    signal.throwIfAborted();
    while (true) {
      const part = await reader.read(); signal.throwIfAborted();
      if (part.done) break;
      size += part.value.byteLength;
      if (size > 32 * 1024 * 1024) throw new RangeError('增强直连请求体超过 32 MiB');
      chunks.push(Buffer.from(part.value));
    }
    return Buffer.concat(chunks, size);
  } catch (error) { reader.cancel(error).catch(() => {}); throw error; }
  finally { signal.removeEventListener('abort', abort); reader.releaseLock(); }
}
function decorateResponse(response, url, redirected, type) {
  Object.defineProperties(response, {
    url:{value:url, configurable:true}, redirected:{value:redirected, configurable:true}, type:{value:type, configurable:true},
    clone:{value:function () { return decorateResponse(Response.prototype.clone.call(this), url, redirected, type); }, configurable:true},
  });
  return response;
}
function ready(snapshot) {
  if (snapshot.status !== 'ready' || !snapshot.endpoint || !snapshot.token) {
    throw new Error(snapshot.reason || '增强直连组件尚未就绪');
  }
}
function normalizeRequest(args, protocol) {
  let options, callback, base;
  if (typeof args[0] === 'string' || args[0] instanceof URL) {
    base = new URL(args[0]);
    options = { ...urlToHttpOptions(base), ...(typeof args[1] === 'object' ? args[1] : {}) };
    callback = typeof args[1] === 'function' ? args[1] : args[2];
  } else {
    options = { ...(args[0] || {}) };
    callback = args[1];
  }
  const hostname = options.hostname || options.host || 'localhost';
  const target = new URL(`${options.protocol || protocol}//${hostname}${options.port ? ':' + options.port : ''}`);
  const requestedPath = options.path || '/';
  const parsedPath = new URL(requestedPath, target);
  target.pathname = parsedPath.pathname;
  target.search = parsedPath.search;
  target.hash = '';
  if (options.auth) {
    const split = options.auth.indexOf(':');
    target.username = split < 0 ? options.auth : options.auth.slice(0, split);
    target.password = split < 0 ? '' : options.auth.slice(split + 1);
  }
  return { options, callback, target };
}
function nodeHeaders(headers) {
  const result = new Headers();
  if (Array.isArray(headers)) {
    for (let i = 0; i < headers.length; i += 2) result.append(headers[i], headers[i + 1]);
  } else {
    for (const [name, value] of Object.entries(headers || {})) {
      for (const part of Array.isArray(value) ? value : [value]) result.append(name, String(part));
    }
  }
  return result;
}

// Install before importing any core. Each worker has its own JS global/module
// cache; all workers receive the same endpoint owned by the host runtime.
function installBridge({ snapshot = {}, env = () => process.env } = {}) {
  let current = snapshot;
  let stopped = false;
  const requests = new Set();
  const nativeFetch = globalThis.fetch;
  const originals = { httpRequest: http.request, httpGet: http.get, httpsRequest: https.request, httpsGet: https.get };

  async function enhancedFetch(input, init) {
    const initialURL = new URL(typeof input === 'string' || input instanceof URL ? input : input.url);
    if (stopped || !eligible(initialURL, current, env(), init || {})) return nativeFetch(input, init);
    ready(current);
    let request = new Request(input, init);
    const deadline = Date.now() + 30000;
    const redirectMode = request.redirect;
    let replayBody = await readBody(request, deadline);
    request = new Request(request.url, {method:request.method, headers:request.headers, body:replayBody, signal:request.signal, redirect:redirectMode});
    let redirected = false;
    for (let count = 0; count <= 20; count++) {
      const target = new URL(request.url); target.hash = '';
      request.signal.throwIfAborted();
      const budget = deadline - Date.now();
      if (budget <= 0) throw new DOMException('增强直连请求超时', 'TimeoutError');
      let response;
      if (eligible(target, current, env(), init || {})) {
        ready(current);
        const controller = new AbortController(); requests.add(controller);
        let timer;
        try {
          timer = setTimeout(() => controller.abort(new DOMException('增强直连请求超时', 'TimeoutError')), budget); timer.unref?.();
          response = await nativeFetch(current.endpoint + '/proxy', {
            method: request.method,
            headers: helperHeaders(request.headers, target, current, budget),
            body: replayBody,
            signal: AbortSignal.any([request.signal, controller.signal]),
            redirect: 'manual',
          });
          if (response.headers.has('X-Danmu-Outbound-Error')) {
            await response.body?.cancel();
            throw new TypeError('增强直连连接失败');
          }
          // Keep the deadline/cancellation active through body consumption.
          const original = response;
          const reader = original.body?.getReader();
          const cleanup = () => { clearTimeout(timer); requests.delete(controller); };
          const body = reader ? new ReadableStream({
            async pull(stream) {
              try { const part = await reader.read(); if (part.done) { cleanup(); stream.close(); } else stream.enqueue(part.value); }
              catch (error) { cleanup(); stream.error(error); }
            },
            async cancel(reason) { cleanup(); await reader.cancel(reason); },
          }) : null;
          response = new Response(body, {status: original.status, statusText: original.statusText, headers: original.headers});
          decorateResponse(response, target.href, redirected, original.type);
          if (!reader) cleanup();
        } catch (error) { clearTimeout(timer); requests.delete(controller); throw error; }
      } else {
        response = await nativeFetch(request, { redirect: 'manual', signal:AbortSignal.any([request.signal, AbortSignal.timeout(budget)]) });
      }
      const location = response.headers.get('location');
      if (!location || ![301,302,303,307,308].includes(response.status) || redirectMode === 'manual') return response;
      if (redirectMode === 'error') { await response.body?.cancel(); throw new TypeError('Redirect disallowed'); }
      if (count === 20) { await response.body?.cancel(); throw new TypeError('Too many redirects'); }
      const next = new URL(location, target);
      if (!['https:', 'http:'].includes(next.protocol) || next.username || next.password) { await response.body?.cancel(); throw new TypeError('Invalid redirect'); }
      const headers = new Headers(request.headers);
      if (next.origin !== target.origin) { for (const name of ['authorization','cookie','proxy-authorization']) headers.delete(name); }
      let method = request.method;
      if ((response.status === 303 && method !== 'HEAD') || ([301,302].includes(response.status) && method === 'POST')) {
        method = 'GET'; replayBody = null; headers.delete('content-type'); headers.delete('content-length');
      }
      await response.body?.cancel();
      request = new Request(next, {method, headers, body:replayBody, signal:request.signal, redirect:redirectMode});
      redirected = true;
    }
  }
  function wrapRequest(original, protocol) {
    return function (...args) {
      if (stopped) return original.apply(this, args);
      let parsed;
      try { parsed = normalizeRequest(args, protocol); } catch { return original.apply(this, args); }
      if (!eligible(parsed.target, current, env(), parsed.options)) return original.apply(this, args);
      ready(current);
      const endpoint = new URL(current.endpoint);
      const budget = 30000;
      const headers = Object.fromEntries(helperHeaders(nodeHeaders(parsed.options.headers), parsed.target, current, budget));
      const request = originals.httpRequest.call(http, {
        hostname:endpoint.hostname, port:endpoint.port, path:'/proxy', method:parsed.options.method || 'GET',
        headers, agent:false, signal:parsed.options.signal, timeout:parsed.options.timeout,
      });
      requests.add(request);
      const timer = setTimeout(() => request.destroy(new Error('增强直连请求超时')), budget); timer.unref?.();
      const cleanup = () => { clearTimeout(timer); requests.delete(request); };
      request.once('close', cleanup);
      const emit = request.emit;
      request.emit = function (event, ...values) {
        if (event === 'response') {
          const response = values[0];
          if (response.headers['x-danmu-outbound-error']) { response.resume(); request.destroy(new Error('增强直连连接失败')); return true; }
          response.once('end', cleanup); response.once('close', cleanup);
        }
        return emit.call(this, event, ...values);
      };
      if (typeof parsed.callback === 'function') request.on('response', parsed.callback);
      return request;
    };
  }
  globalThis.fetch = enhancedFetch;
  http.request = wrapRequest(originals.httpRequest, 'http:');
  https.request = wrapRequest(originals.httpsRequest, 'https:');
  http.get = function (...args) { const request = http.request(...args); request.end(); return request; };
  https.get = function (...args) { const request = https.request(...args); request.end(); return request; };
  syncBuiltinESMExports();
  return {
    update(next) { current = next || {}; },
    stop() {
      stopped = true;
      for (const request of requests) {
        if (request instanceof AbortController) request.abort(); else request.destroy(new Error('增强直连已停止'));
      }
      requests.clear();
      if (globalThis.fetch === enhancedFetch) globalThis.fetch = nativeFetch;
      http.request = originals.httpRequest; http.get = originals.httpGet;
      https.request = originals.httpsRequest; https.get = originals.httpsGet;
      syncBuiltinESMExports();
    },
  };
}
module.exports = { installBridge, eligible, hasProxy };
