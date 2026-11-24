'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { wrapSeriesRefresh } = require('../app/src/main/assets/nodejs-project/app-series-refresh.js');
const net = require('node:net');
const { spawn } = require('node:child_process');
const { once } = require('node:events');
const { copyRuntimeAssets } = require('./runtime-fixture.cjs');

function fixture(t, source) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'danmu-fresh-series-'));
  fs.mkdirSync(path.join(root, 'apis'));
  fs.writeFileSync(path.join(root, 'package.json'), '{"type":"module"}');
  if (source) fs.writeFileSync(path.join(root, 'apis/dandan-api.js'), source);
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  return root;
}
const request = suffix => new Request('http://localhost/token/api/v2/search/anime?keyword=Show&season=2&episode=13' + (suffix ?? '&app_refresh=1'));
const cached = () => Response.json({ success: true, animes: [{ animeId: 42, animeTitle: 'Show from tencent', source: 'tencent' }] });
const api = `
  export async function searchAnime(url, preferId = null, preferSource = null, details = null, target = null, forceRefresh = false) {
    if (!forceRefresh) throw new Error('Missing forced refresh');
    if (url.searchParams.has('season') || url.searchParams.has('episode')) throw new Error('Seasonal cache fallback was not disabled');
    details.set('request', url.searchParams.get('keyword'));
    return Response.json({success:true, animes:[
      {animeId:1,animeTitle:'Show from local',source:'local'},
      {animeId:42,animeTitle:'Show from tencent',source:'tencent'}]});
  }
  export async function getBangumi(path, details, source) {
    if (source !== 'tencent' || !details.has('request')) throw new Error('Wrong request snapshot');
    return Response.json({success:true,bangumi:{animeTitle:details.get('request')+' from tencent',
      episodes:Array.from({length:18},(_,i)=>({episodeId:100+i,episodeNumber:i+1}))}});
  }
`;

test('explicit refresh returns latest remote episodes with request-scoped detail snapshots', async t => {
  let authorizationChecks = 0;
  const handle = wrapSeriesRefresh(async () => { authorizationChecks++; return cached(); }, fixture(t, api));
  const response = await handle(request());
  const body = await response.json();
  assert.equal(authorizationChecks, 1);
  assert.equal(body.appRefreshFresh, true);
  assert.equal(body.appBangumis.length, 1);
  assert.equal(body.appBangumis[0].source, 'tencent');
  assert.equal(body.appBangumis[0].detail.bangumi.episodes.length, 18);
  assert.equal(response.headers.get('cache-control'), 'no-store');
});

test('unauthorized and rate-limited requests keep core responses and never import the API', async t => {
  const directory = fixture(t, `throw new Error('Authentication bypassed');`);
  for (const status of [401, 403, 429]) {
    const response = await wrapSeriesRefresh(async () => Response.json({success:false}, {status}), directory)(request());
    assert.equal(response.status, status);
    assert.deepEqual(await response.json(), {success:false});
  }
  const rejected = await wrapSeriesRefresh(async () => Response.json({animes:[],errorCode:429}), directory)(request());
  assert.deepEqual(await rejected.json(), {animes:[],errorCode:429});
});

test('ordinary searches do not trigger an additional search or change responses', async t => {
  const response = cached();
  const handle = wrapSeriesRefresh(async () => response, fixture(t, `throw new Error('Unexpected import');`));
  assert.equal(await handle(request('')), response);
});

test('missing or older core refresh support is marked unconfirmed', async t => {
  for (const source of [null, `export function searchAnime() { throw new Error('Old API should not run'); } export function getBangumi() {}`]) {
    const response = await wrapSeriesRefresh(async () => cached(), fixture(t, source))(request());
    const body = await response.json();
    assert.equal(body.appRefreshFresh, false);
    assert.equal(body.animes[0].animeId, 42);
  }
});

test('failed forced search preserves cached results without claiming they are current', async t => {
  const source = `export function searchAnime(url,a,b,c,d,forceRefresh) { return Response.json({success:false}, {status:503}); }
    export function getBangumi() {}`;
  const body = await (await wrapSeriesRefresh(async () => cached(), fixture(t, source))(request())).json();
  assert.equal(body.appRefreshFresh, false);
  assert.equal(body.animes[0].animeId, 42);
});

test('concurrent refreshes keep detail maps isolated', async t => {
  const handle = wrapSeriesRefresh(async () => cached(), fixture(t, api));
  const responses = await Promise.all(['First','Second'].map(name => handle(new Request(
    'http://localhost/token/api/v2/search/anime?app_refresh=1&keyword=' + name))));
  const bodies = await Promise.all(responses.map(response => response.json()));
  assert.equal(bodies[0].appBangumis[0].detail.bangumi.animeTitle, 'First from tencent');
  assert.equal(bodies[1].appBangumis[0].detail.bangumi.animeTitle, 'Second from tencent');
});

test('refresh keeps slow source work, fallback and denial in the originating trace', async t => {
  const directory = fixture(t, `
    import { currentTrace } from '../utils/pipeline-trace.js';
    export async function searchAnime(url, a, b, details, c, forceRefresh) {
      const trace = currentTrace();
      await new Promise(resolve => setTimeout(resolve, 20));
      if (!forceRefresh || trace.clientIp !== url.searchParams.get('keyword')) throw Error('wrong request');
      return Response.json({success:true, animes:[{animeId:42, source:'fixture'}]});
    }
    export async function getBangumi() { return Response.json({bangumi:{episodes:[]}}); }
  `);
  fs.mkdirSync(path.join(directory, 'utils'));
  fs.mkdirSync(path.join(directory, 'configs'));
  fs.writeFileSync(path.join(directory, 'configs/globals.js'), 'export const Globals = {reqRecords:[]};');
  fs.writeFileSync(path.join(directory, 'utils/pipeline-trace.js'), `
    import { AsyncLocalStorage } from 'node:async_hooks';
    const storage = new AsyncLocalStorage();
    export const retained = [];
    export const createTrace = (method, path, clientIp) => ({id:clientIp, clientIp, startedAt:Date.now(), steps:[]});
    export const currentTrace = () => storage.getStore();
    export const runWithTrace = (trace, fn) => storage.run(trace, fn);
    export function traceStep(name, start, detail) { currentTrace().steps.push({name, ...detail}); }
    export function finishTrace(trace, keep) { if (keep) retained.push(trace); }
  `);
  const { pathToFileURL } = require('node:url');
  const { retained, currentTrace } = await import(pathToFileURL(path.join(directory, 'utils/pipeline-trace.js')));
  const { Globals } = await import(pathToFileURL(path.join(directory, 'configs/globals.js')));
  const handle = wrapSeriesRefresh(() => assert.fail('traced twice'), directory, () => {
    const trace = currentTrace();
    if (trace.clientIp === 'denied') return Response.json({}, {status:403});
    Globals.reqRecords.push({requestId:trace.id});
    return cached();
  });
  const run = (ip, keyword) => handle(new Request('http://localhost/api/v2/search/anime?app_refresh=1&keyword=' + keyword), {}, 'node', ip);
  const [first, second, fallback, denied] = await Promise.all([
    run('first','first'), run('second','second'), run('fallback','other'), run('denied','denied')
  ]);
  assert.equal((await first.json()).appRefreshFresh, true);
  assert.equal((await second.json()).appRefreshFresh, true);
  assert.equal((await fallback.json()).appRefreshFresh, false);
  assert.equal(denied.status, 403);
  assert.deepEqual(retained.map(t => t.id).sort(), ['fallback', 'first', 'second']);
  for (const trace of retained) {
    assert.equal(trace.statusCode, 200);
    assert.equal(trace.steps[0].status, trace.id === 'fallback' ? 'fail' : 'ok');
    if (trace.id !== 'fallback') assert.equal(trace.steps[1].outputs.count, 1);
    const record = Globals.reqRecords.find(r => r.requestId === trace.id);
    assert.equal(record.statusCode, 200);
    assert.ok(record.durationMs >= 15);
  }
});

const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
async function freePort() {
  const server = net.createServer();
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  const port = server.address().port; await new Promise(resolve => server.close(resolve)); return port;
}
for (const worker of [false, true]) test(`fresh searches use the core trace entry and retain authentication (${worker ? 'Worker' : 'direct'})`, {timeout:25000}, async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'danmu-fresh-series-host-'));
  let child, output = '';
  try {
    const runtime = path.resolve('app/src/main/assets/nodejs-project');
    copyRuntimeAssets(runtime, directory);
    fs.symlinkSync(path.join(runtime, 'node_modules'), path.join(directory, 'node_modules'));
    fs.mkdirSync(path.join(directory, 'config'));
    fs.writeFileSync(path.join(directory, 'config', '.env'), `TOKEN=user\nADMIN_TOKEN=administrator\nDANMU_API_WORKER=${worker}\nDANMU_API_HOT_RELOAD=false\n`);
    const core = path.join(directory, 'danmu_api_stable');
    fs.mkdirSync(path.join(core, 'apis'), {recursive:true});
    fs.writeFileSync(path.join(core, 'package.json'), '{"type":"module"}');
    fs.writeFileSync(path.join(core, 'worker.js'), `export function handleRequest() {
      throw new Error('Host bypassed the core trace entry');
    }
    export function handleRequestWithTrace(req) {
      if (!new URL(req.url).pathname.startsWith('/user/')) return Response.json({success:false},{status:401});
      return Response.json({success:true,animes:[{animeId:42,animeTitle:'Show from tencent',source:'tencent'}]});
    }`);
    fs.writeFileSync(path.join(core, 'apis', 'dandan-api.js'), api);
    fs.mkdirSync(path.join(core, 'utils'));
    fs.mkdirSync(path.join(core, 'sources'));
    fs.writeFileSync(path.join(core, 'sources', 'bilibili.js'), 'export default class Bilibili {}');
    fs.writeFileSync(path.join(core, 'utils', 'http-util.js'), `export async function httpGet() {
      return {data:{code:0,data:{bvid:'BVfixture',title:'Current video',pic:'https://i0.hdslb.com/current.jpg',
        pages:[{cid:1,page:1,part:'First'},{cid:2,page:2,part:'Second'}]}}};
    }`);
    const port = await freePort();
    child = spawn(process.execPath, [path.join(directory, 'main.js')], {
      cwd:directory, env:{...process.env, DANMU_API_HOME:directory, DANMU_API_PORT:String(port),
        DANMU_API_HOST:'127.0.0.1', DANMU_API_PROXY_PORT:String(await freePort()),
        OUTBOUND_MODE:'off', DANMU_APP_OUTBOUND_CONFIG:'', PROXY_URL:''}, stdio:['ignore','pipe','pipe']
    });
    child.stdout.on('data', data => output += data); child.stderr.on('data', data => output += data);
    const base = `http://127.0.0.1:${port}`;
    let ready = false;
    for (let n = 0; n < 150; n++) {
      try { ready = (await fetch(base+'/__health')).ok; } catch {}
      if (ready) break; await pause(100);
    }
    assert.ok(ready, output);
    const suffix = '/api/v2/search/anime?keyword=Show&season=2&episode=13&app_refresh=1';
    const response = await fetch(base+'/user'+suffix);
    const body = await response.json();
    assert.equal(response.status, 200, output);
    assert.equal(body.appRefreshFresh, true, JSON.stringify(body));
    assert.equal(body.appBangumis[0].detail.bangumi.episodes.length, 18);
    const denied = await fetch(base+'/invalid'+suffix);
    assert.equal(denied.status, 401);
    const metadataPath = '/api/v2/search/anime?app_url_metadata=1&keyword=' + encodeURIComponent('https://www.bilibili.com/video/BVfixture?p=2');
    const metadata = await (await fetch(base + '/user' + metadataPath)).json();
    assert.equal(metadata.appUrlMetadata?.selectedEpisodeId, '2', JSON.stringify(metadata) + output);
    assert.equal(metadata.appUrlMetadata?.episodes.length, 2);
    assert.equal((await fetch(base + '/invalid' + metadataPath)).status, 401);
    assert.equal((await denied.json()).appRefreshFresh, undefined);
  } finally {
    if (child && child.exitCode === null) {
      child.kill('SIGTERM'); await Promise.race([once(child,'exit'),pause(2000)]);
      if (child.exitCode === null) {child.kill('SIGKILL'); await once(child,'exit');}
    }
    fs.rmSync(directory,{recursive:true,force:true});
  }
});
