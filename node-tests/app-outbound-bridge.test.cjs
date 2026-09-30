'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const https = require('node:https');
const { once } = require('node:events');
const { pathToFileURL } = require('node:url');
const path = require('node:path');
const zlib = require('node:zlib');
const { installBridge, eligible } = require('../app/src/main/assets/nodejs-project/app-outbound-bridge.js');
const { validateConfig } = require('../app/src/main/assets/nodejs-project/app-outbound-runtime.js');
const config = { enabled:true, sources:['bahamut','tmdb'],httpVersion:'auto' };

async function fixture(fn) {
  const seen=[];
  const server = http.createServer((req,res)=>{
    const body=[];
    req.on('data',chunk=>body.push(chunk));
    req.on('end',()=>{
      const target=req.headers['x-danmu-outbound-target'];
      seen.push({target, method:req.method, headers:req.headers, body:Buffer.concat(body).toString()});
      if (target && req.headers['x-danmu-outbound-token']!=='offline-secret') {res.writeHead(401,{'X-Danmu-Outbound-Error':'1'});res.end();return;}
      const url=new URL(target || req.url, 'http://localhost');
      if (url.pathname==='/redirect307') {res.writeHead(307,{Location:'https://api.tmdb.org/final'});res.end();return;}
      if (url.pathname==='/redirect') {res.writeHead(302,{Location:'https://api.tmdb.org/final'});res.end();return;}
      if (url.pathname==='/failed') {res.writeHead(502,{'X-Danmu-Outbound-Error':'1'});res.end();return;}
      if (url.pathname==='/slow') {
        res.writeHead(200);res.write('begin');
        const timer=setTimeout(()=>res.end('end'),10000);timer.unref();req.on('close',()=>clearTimeout(timer));return;
      }
      const bytes=Buffer.from(JSON.stringify({method:req.method,body:Buffer.concat(body).toString(),contentType:req.headers['content-type'] || null}));
      res.setHeader('Set-Cookie',['a=1; Path=/','b=2; Path=/']);
      res.setHeader('Content-Type','application/json');
      if (url.pathname==='/gzip') {res.setHeader('Content-Encoding','gzip');res.end(zlib.gzipSync(bytes));}
      else res.end(bytes);
    });
  });
  server.listen(0,'127.0.0.1');await once(server,'listening');
  const endpoint=`http://127.0.0.1:${server.address().port}`;
  const originalFetch=globalThis.fetch;
  const originalRequest=https.request;
  const bridge=installBridge({snapshot:{status:'ready',config,endpoint,token:'offline-secret'}});
  try {await fn({bridge,endpoint,seen});}
  finally {
    bridge.stop();assert.equal(globalThis.fetch,originalFetch);assert.equal(https.request,originalRequest);
    server.closeAllConnections();await new Promise(resolve=>server.close(resolve));
  }
}
test('target, proxy and explicit transport eligibility',()=>{
  const snapshot={config};
  assert.equal(eligible(new URL('https://api.gamer.com.tw/x'),snapshot,{}),true);
  for(const target of ['https://evil.api.gamer.com.tw/x','http://api.gamer.com.tw/x','https://api.gamer.com.tw:444/x','https://a:b@api.gamer.com.tw/x']) assert.equal(eligible(new URL(target),snapshot,{}),false);
  for(const proxy of ['bahamut@https://example.invalid','@https://example.invalid','http://127.0.0.1:7890']) assert.equal(eligible(new URL('https://api.gamer.com.tw/x'),snapshot,{PROXY_URL:proxy}),false);
  assert.equal(eligible(new URL('https://api.gamer.com.tw/x'),snapshot,{PROXY_URL:'tmdb@https://example.invalid'}),true);
  assert.equal(eligible(new URL('https://api.tmdb.org/x'),snapshot,{}, {dispatcher:{}}),false);
  assert.throws(()=>validateConfig({enabled:true,httpVersion:'h1'}));
  assert.throws(()=>validateConfig({dohUrl:'http://resolver.invalid'}));
});
test('native fetch body, URL, cookies and untouched ordinary requests',async()=>fixture(async({endpoint,seen})=>{
  const response=await fetch('https://api.gamer.com.tw/echo?x=1',{method:'POST',body:new URLSearchParams({a:'b'})});
  assert.equal(response.url,'https://api.gamer.com.tw/echo?x=1');
  assert.equal(response.headers.getSetCookie().length,2);
  const copy=response.clone();assert.equal(copy.url,response.url);await copy.text();
  const body=await response.json();assert.equal(body.body,'a=b');assert.match(body.contentType,/application\/x-www-form-urlencoded/);
  await fetch(endpoint+'/ordinary');assert.equal(seen[1].target,undefined);
}));
test('native cross-origin redirects strip credentials and remain enhanced',async()=>fixture(async({seen})=>{
  const response=await fetch('https://api.gamer.com.tw/redirect',{headers:{Authorization:'secret',Cookie:'cookie-secret'}});
  assert.equal(response.url,'https://api.tmdb.org/final');assert.equal(response.redirected,true);await response.text();
  assert.equal(seen.length,2);assert.equal(seen[1].headers.authorization,undefined);assert.equal(seen[1].headers.cookie,undefined);
}));
test('native fetch cancellation during response body',async()=>fixture(async()=>{
  const controller=new AbortController();
  const response=await fetch('https://api.gamer.com.tw/slow',{signal:controller.signal});
  const reading=response.text();controller.abort();await assert.rejects(reading);
}));
test('component failure never falls back to ordinary target fetch',async()=>fixture(async({bridge})=>{
  await assert.rejects(fetch('https://api.gamer.com.tw/failed'),/增强直连/);
  bridge.update({config,status:'failed',reason:'component failed'});
  await assert.rejects(fetch('https://api.gamer.com.tw/echo'),/component failed/);
}));
test('bundled node-fetch POST, gzip, repeated cookies and helper errors',async()=>fixture(async()=>{
  const {default:nodeFetch}=await import(pathToFileURL(path.resolve(__dirname,'../app/src/main/assets/nodejs-project/node_modules/node-fetch/src/index.js')));
  const response=await nodeFetch('https://api.gamer.com.tw/gzip',{method:'POST',body:'node-fetch-body'});
  assert.equal(response.headers.raw()['set-cookie'].length,2);
  assert.equal((await response.json()).body,'node-fetch-body');
  await assert.rejects(nodeFetch('https://api.gamer.com.tw/failed'),/增强直连/);
}));
test('https.request object overload, ESM exports and body writes',async()=>fixture(async()=>{
  const esm=await import('node:https');assert.equal(esm.request,https.request);
  const result=await new Promise((resolve,reject)=>{
    const request=esm.request({hostname:'api.tmdb.org',path:'/post',method:'POST'},res=>{
      const chunks=[];res.on('data',chunk=>chunks.push(chunk));res.on('end',()=>resolve(JSON.parse(Buffer.concat(chunks))));
    });request.on('error',reject);request.write('part1');request.end('part2');
  });
  assert.equal(result.body,'part1part2');
}));
test('native 307 redirects replay the buffered business body once per redirect',async()=>fixture(async({seen})=>{
  const response=await fetch('https://api.gamer.com.tw/redirect307',{method:'POST',body:'business'});
  await response.text();assert.equal(seen.length,2);assert.equal(seen[0].body,'business');assert.equal(seen[1].method,'POST');assert.equal(seen[1].body,'business');
}));
test('cancel blocked upload before contacting the helper',async()=>fixture(async({seen})=>{
  const controller=new AbortController();
  const body=new ReadableStream({pull(){},cancel(){return new Promise(()=>{});}});
  const pending=fetch('https://api.gamer.com.tw/upload',{method:'POST',body,duplex:'half',signal:controller.signal});
  controller.abort();await assert.rejects(pending);assert.equal(seen.length,0);
}));


test('new sources opt in exact hosts, preserve proxies and carry POST bodies once', async()=>fixture(async({bridge,seen,endpoint})=>{
  const groups={dandan:['api.danmaku.weeblify.app','nipaplay.aimes-soft.com'],animeko:['api.animeko.org','danmaku-global.myani.org','danmaku-cn.myani.org','s1.animeko.openani.org','api.bangumi.vip']};
  const extended={...config,sources:['bahamut','tmdb','dandan','animeko']};
  assert.deepEqual(validateConfig(extended).sources,extended.sources);
  for(const [source,hosts] of Object.entries(groups)) for(const host of hosts) {
    assert.equal(eligible(new URL('https://'+host+'/x'),{config},{}),false);
    assert.equal(eligible(new URL('https://'+host+'/x'),{config:extended},{}),true);
    assert.equal(eligible(new URL('https://'+host+'.evil.test/x'),{config:extended},{}),false);
    for(const proxy of [source+'@https://proxy.test','@https://proxy.test','http://127.0.0.1:7890']) {
      assert.equal(eligible(new URL('https://'+host+'/x'),{config:extended},{PROXY_URL:proxy}),false);
    }
  }
  bridge.update({config:extended,status:'ready',endpoint,token:'offline-secret'});
  for(const host of ['api.danmaku.weeblify.app','api.bangumi.vip','danmaku-global.myani.org']) {
    const response=await fetch('https://'+host+'/search',{method:'POST',body:'one-business-body'});
    assert.equal((await response.json()).body,'one-business-body');
  }
  assert.equal(seen.length,3);
  assert.ok(seen.every(request=>request.method==='POST' && request.body==='one-business-body'));
}));


test('selected-source diagnostics use node fallback, validate responses, respect proxies and cancel',async()=>{
  const {diagnoseSource}=require('../app/src/main/assets/nodejs-project/app-outbound-diagnostics.js');
  const snapshot={config:{...config,sources:['bahamut','tmdb','dandan','animeko']}};
  const seen=[];
  const result=await diagnoseSource('animeko',snapshot,{}, {fetchImpl:async(url)=>{
    seen.push(new URL(url).hostname);
    if(seen.length===1) throw new Error('primary unavailable');
    return Response.json({id:400602});
  }});
  assert.equal(result.ok,true);assert.deepEqual(seen,['api.animeko.org','danmaku-global.myani.org']);
  assert.equal((await diagnoseSource('animeko',snapshot,{PROXY_URL:'animeko@https://proxy.test'},{fetchImpl:()=>{throw new Error('must not send');}})).httpStatus,409);
  assert.equal((await diagnoseSource('dandan',snapshot,{}, {fetchImpl:async()=>new Response('<html>challenge</html>')})).ok,false);
  assert.equal((await diagnoseSource('tmdb',snapshot,{}, {fetchImpl:async()=>Response.json({status_message:'Unauthorized'},{status:401})})).ok,true);
  const controller=new AbortController();controller.abort();
  await assert.rejects(diagnoseSource('animeko',snapshot,{}, {signal:controller.signal,fetchImpl:()=>{throw new Error('must not send');}}),{name:'AbortError'});
});
