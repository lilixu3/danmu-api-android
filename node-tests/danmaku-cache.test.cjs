'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const danmakuModule = require('../app/src/main/assets/nodejs-project/app-danmaku-cache.js');

function response(body, contentType = 'application/xml') {
  return new Response(body, { headers: { 'content-type': contentType } });
}

function fixture(options = {}) {
  const requests = [];
  let now = 1_000_000;
  let handlerCalls = 0;
  const handler = async (request, env, platform, clientIp) => {
    handlerCalls += 1;
    requests.push({ url: request.url, env, platform, clientIp });
    if (options.handler) return options.handler(request);
    return response(`<i><d p="1,1,25,1">one</d><d p="2,1,25,1">two</d></i>`);
  };
  const cache = danmakuModule.createDanmakuCache({
    sendJson: (res, status, payload) => {
      res.statusCode = status;
      res.body = payload;
    },
    isAdminAuthorized: () => options.authorized !== false,
    stripTokenPrefix: value => value,
    getRequestHandler: () => handler,
    getEnvSnapshot: () => ({ TOKEN: 'fixture' }),
    port: () => 9321,
    now: () => now,
    ttlMs: options.ttlMs,
    maxEntries: options.maxEntries,
    maxEntryBytes: options.maxEntryBytes,
    maxTotalBytes: options.maxTotalBytes,
  });
  return {
    cache,
    get handlerCalls() { return handlerCalls; },
    requests,
    advance: value => { now += value; },
    now: () => now,
  };
}

const sourceUrl = 'http://127.0.0.1:9321/api/v2/comment?url=https%3A%2F%2Fexample.com%2Fepisode&offset=2&fontSize=30';

test('XML helpers preserve counting, detection, and one-shot overrides', () => {
  assert.equal(danmakuModule.countDanmakuXml('<i><d p="1">a</d><d\np="2">b</d><data>no</data></i>'), 2);
  assert.equal(danmakuModule.looksLikeDanmakuXml('application/xml', '<i></i>'), true);
  assert.equal(danmakuModule.looksLikeDanmakuXml('application/json', '{"comments":[]}'), false);

  const xml = '<i><d p="1.5,1,25,white">a</d><d p="-1,1,25,white">b</d></i>';
  assert.equal(
    danmakuModule.applyDanmuXmlOverrides(xml, { offsetSec: 2.25, fontSize: 36 }),
    '<i><d p="3.75,1,36,white">a</d><d p="1.25,1,36,white">b</d></i>'
  );
  assert.deepEqual(
    danmakuModule.readDanmakuOverrides(new URL('http://127.0.0.1:9321/api/v2/comment?offsetMs=2500&font_size=40')),
    { offsetSec: 2.5, fontSize: 40 }
  );
});

test('prepare strips override parameters, forwards environment and deduplicates inflight work', async () => {
  const { cache, requests } = fixture();
  const [first, second] = await Promise.all([
    cache.prepare(sourceUrl, { 'x-test': '1' }, '127.0.0.1'),
    cache.prepare(sourceUrl, { 'x-test': '1' }, '127.0.0.1'),
  ]);

  assert.equal(requests.length, 1);
  assert.equal(requests[0].platform, 'node');
  assert.deepEqual(requests[0].env, { TOKEN: 'fixture' });
  assert.equal(new URL(requests[0].url).searchParams.has('offset'), false);
  assert.equal(new URL(requests[0].url).searchParams.has('fontSize'), false);
  assert.equal(first.entry.id, second.entry.id);
  assert.equal(first.cached, false);
  assert.equal(second.cached, false);
  assert.equal(first.entry.count, 2);
});

test('prepared entries expire, enforce entry size and evict the oldest item', async () => {
  const { cache, advance } = fixture({ ttlMs: 100, maxEntries: 1 });
  const first = await cache.prepare(sourceUrl, {}, '127.0.0.1');
  advance(101);
  assert.equal(cache.getById(first.entry.id), null);

  const second = await cache.prepare(sourceUrl, {}, '127.0.0.1');
  const other = sourceUrl.replace('episode', 'other');
  const third = await cache.prepare(other, {}, '127.0.0.1');
  assert.equal(cache.getById(second.entry.id), null);
  assert.equal(cache.getById(third.entry.id).id, third.entry.id);

  const small = fixture({ maxEntryBytes: 1 });
  await assert.rejects(small.cache.prepare(sourceUrl, {}, '127.0.0.1'), error => error.statusCode === 413);
});

test('invalid or out-of-scope URLs are rejected before dispatch', async () => {
  const { cache, handlerCalls } = fixture();
  const cases = [
    ['http://example.com:9321/api/v2/comment', 400],
    ['https://127.0.0.1:9321/api/v2/comment', 400],
    ['http://user:pass@127.0.0.1:9321/api/v2/comment', 400],
    ['http://127.0.0.1:9321/api/v2/search/anime', 400],
    ['not a url', 400],
  ];
  for (const [url, statusCode] of cases) {
    await assert.rejects(
      cache.prepare(url, {}, '127.0.0.1'),
      error => error.statusCode === statusCode
    );
  }
  assert.equal(handlerCalls, 0);
});

test('prepare and prepared endpoints preserve authorization and response semantics', async () => {
  const { cache } = fixture();
  const prepareRes = {};
  const url = new URL(`http://127.0.0.1:9321/__danmaku/prepare?url=${encodeURIComponent(sourceUrl)}`);
  await cache.handlePrepare({ method: 'GET' }, prepareRes, url, '127.0.0.1', {});
  assert.equal(prepareRes.statusCode, 200);
  assert.equal(prepareRes.body.success, true);
  assert.match(prepareRes.body.url, /^http:\/\/127\.0\.0\.1:9321\/__danmaku\/prepared\/[a-z0-9-]+\.xml$/);

  const id = prepareRes.body.url.split('/').pop().replace(/\.xml$/, '');
  const headers = {};
  let ended;
  const preparedRes = {
    setHeader: (key, value) => { headers[key] = value; },
    end: value => { ended = value; },
  };
  cache.handlePrepared({ method: 'HEAD' }, preparedRes, `/__danmaku/prepared/${id}.xml`, url, '127.0.0.1');
  assert.equal(preparedRes.statusCode, 200);
  assert.equal(headers['x-danmaku-count'], '2');
  assert.equal(ended, undefined);

  const unauthorized = {};
  const denied = fixture({ authorized: false });
  await denied.cache.handlePrepare({ method: 'GET' }, unauthorized, url, '203.0.113.1', {});
  assert.equal(unauthorized.statusCode, 403);
});

