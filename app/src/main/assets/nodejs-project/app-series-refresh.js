'use strict';

const path = require('node:path');
const { pathToFileURL } = require('node:url');

/** Keep core authentication/rate limits, then request a fresh search with request-scoped details. */
function wrapSeriesRefresh(handler, coreDirectory) {
  let apiPromise;
  const loadApi = () => apiPromise ||= import(pathToFileURL(path.join(coreDirectory, 'apis/dandan-api.js')).href);
  return async function handleSeriesRefresh(request, ...args) {
    const url = new URL(request.url);
    const refresh = request.method === 'GET' && /(?:^|\/)api\/v2\/search\/anime\/?$/.test(url.pathname) &&
      url.searchParams.get('app_refresh') === '1';
    const response = await handler(request, ...args);
    if (!refresh || !response.ok || !String(response.headers.get('content-type') || '').includes('application/json')) return response;
    let initial;
    try { initial = await response.clone().json(); } catch { return response; }
    if (initial.success !== true || !Array.isArray(initial.animes)) return response;
    let body = { ...initial, appRefreshFresh: false };
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
        const snapshots = [];
        for (const anime of fresh.animes.slice(0, 200)) {
          const id = anime.animeId || anime.bangumiId;
          const source = anime.source || /\bfrom\s+(.+)$/i.exec(anime.animeTitle || '')?.[1]?.trim() || '';
          if (!id || source === 'local') continue;
          try {
            const detailResponse = await api.getBangumi(`/api/v2/bangumi/${id}`, details, source);
            const detail = typeof detailResponse?.json === 'function' ? await detailResponse.json() : detailResponse;
            if (detail?.success !== false && Array.isArray(detail?.bangumi?.episodes)) {
              snapshots.push({ animeId: id, source, detail });
            }
          } catch { /* This candidate can still use the core's regular detail endpoint. */ }
        }
        body = { ...fresh, appRefreshFresh: true, appBangumis: snapshots };
      }
    } catch {
      // The caller can still offer known missing episodes, but must not announce that the source is up to date.
    }
    const headers = new Headers(response.headers);
    headers.delete('content-length');
    headers.delete('content-encoding');
    headers.set('cache-control', 'no-store');
    return new Response(JSON.stringify(body), { status: response.status, headers });
  };
}

module.exports = { wrapSeriesRefresh };
