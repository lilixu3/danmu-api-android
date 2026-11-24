'use strict';

const path = require('node:path');
const { pathToFileURL } = require('node:url');

// The core's ordinary traced entry finalizes as soon as handleRequest returns. For an
// Android refresh, keep its plain dispatcher and the extra source work in one core trace.
async function loadRefreshTrace(directory) {
  const load = relative => import(pathToFileURL(path.join(directory, relative)).href);
  try {
    const [pipeline, { Globals }] = await Promise.all([load('utils/pipeline-trace.js'), load('configs/globals.js')]);
    if (Array.isArray(Globals?.reqRecords) &&
        ['createTrace', 'runWithTrace', 'finishTrace', 'traceStep'].every(key => typeof pipeline[key] === 'function')) {
      return { pipeline, Globals };
    }
  } catch { /* Older cores use their own normal request entry. */ }
  return null;
}

function wrapSeriesRefresh(handler, coreDirectory, plainHandler) {
  let apiPromise, tracePromise;
  const loadApi = () => apiPromise ||= import(pathToFileURL(path.join(coreDirectory, 'apis/dandan-api.js')).href);
  return async function handleSeriesRefresh(request, ...args) {
    const url = new URL(request.url);
    if (request.method !== 'GET' || !/(?:^|\/)api\/v2\/search\/anime\/?$/.test(url.pathname) ||
        url.searchParams.get('app_refresh') !== '1') return handler(request, ...args);
    const context = plainHandler && await (tracePromise ||= loadRefreshTrace(coreDirectory));
    if (!context) return refreshResponse(await handler(request, ...args), url, loadApi);
    const { pipeline, Globals } = context;
    const trace = pipeline.createTrace(request.method, '', args[2]);
    return pipeline.runWithTrace(trace, async () => {
      try {
        const initial = await plainHandler(request, ...args);
        const response = await refreshResponse(initial, url, loadApi, pipeline.traceStep);
        trace.statusCode = response.status;
        return response;
      } catch (error) {
        trace.statusCode = 500;
        throw error;
      } finally {
        const record = Globals.reqRecords.find(item => item.requestId === trace.id);
        if (record) {
          record.statusCode = trace.statusCode;
          record.durationMs = Math.max(0, Date.now() - trace.startedAt);
        }
        pipeline.finishTrace(trace, Boolean(record));
      }
    });
  };
}

async function refreshResponse(response, url, loadApi, traceStep) {
  if (!response.ok || !String(response.headers.get('content-type') || '').includes('application/json')) return response;
  let initial;
  try { initial = await response.clone().json(); } catch { return response; }
  if (initial.success !== true || !Array.isArray(initial.animes)) return response;
  let body = { ...initial, appRefreshFresh: false };
  const searchStarted = Date.now();
  try {
    const api = await loadApi();
    // Older cores ignore the extra argument. Report their result as unconfirmed instead of claiming freshness.
    if (typeof api.searchAnime === 'function' && typeof api.getBangumi === 'function' &&
        /\bforceRefresh\b/.test(Function.prototype.toString.call(api.searchAnime))) {
      const details = new Map();
      // Some cores fall back from a forced seasonal query to a generic cached search.
      // Search the title freshly, then let the caller validate the requested season.
      const freshUrl = new URL(url);
      freshUrl.searchParams.delete('season');
      freshUrl.searchParams.delete('episode');
      const freshResponse = await api.searchAnime(freshUrl, null, null, details, null, true);
      const fresh = await freshResponse.json();
      if (!freshResponse.ok || fresh.success === false || !Array.isArray(fresh.animes)) {
        throw new Error('源端搜索未完成');
      }
      traceStep?.('下载刷新·源端搜索', searchStarted, { status: 'ok', outputs: { count: fresh.animes.length } });
      const detailsStarted = Date.now();
      const snapshots = [];
      let attempted = 0;
      for (const anime of fresh.animes.slice(0, 200)) {
        const id = anime.animeId || anime.bangumiId;
        const source = anime.source || /\bfrom\s+(.+)$/i.exec(anime.animeTitle || '')?.[1]?.trim() || '';
        if (!id || source === 'local') continue;
        attempted++;
        try {
          const detailResponse = await api.getBangumi(`/api/v2/bangumi/${id}`, details, source);
          const detail = typeof detailResponse?.json === 'function' ? await detailResponse.json() : detailResponse;
          if (detail?.success !== false && Array.isArray(detail?.bangumi?.episodes)) {
            snapshots.push({ animeId: id, source, detail });
          }
        } catch { /* This candidate can still use the core's regular detail endpoint. */ }
      }
      traceStep?.('下载刷新·分集快照', detailsStarted, {
        status: !attempted ? 'skip' : snapshots.length ? 'ok' : 'fail',
        reason: !attempted ? '没有可刷新的远程候选' : `已取得 ${snapshots.length}/${attempted} 个候选的分集快照`,
        outputs: { attempted, count: snapshots.length }
      });
      body = { ...fresh, appRefreshFresh: true, appBangumis: snapshots };
    } else {
      traceStep?.('下载刷新·源端搜索', searchStarted, { status: 'skip', reason: '当前核心不支持强制刷新' });
    }
  } catch {
    traceStep?.('下载刷新·源端搜索', searchStarted, { status: 'fail', reason: '源端刷新未完成，返回已有搜索结果' });
    // The caller can still offer known missing episodes, but must not announce that the source is up to date.
  }
  const headers = new Headers(response.headers);
  headers.delete('content-length');
  headers.delete('content-encoding');
  headers.set('cache-control', 'no-store');
  return new Response(JSON.stringify(body), { status: response.status, headers });
}

module.exports = { wrapSeriesRefresh };
