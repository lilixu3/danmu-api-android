'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const {NativeTransport,startNativeOutbound}=require('../app/src/main/assets/nodejs-project/app-outbound-native.js');
const {waitFor}=require('../app/src/main/assets/nodejs-project/app-outbound-dns.js');
const config={enabled:true,sources:['bahamut','tmdb'],httpVersion:'auto',connectTimeoutMs:1000};
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));

test('protocol race sends one POST, closes late successful loser and reuses winning session',async()=>{
 const seen=[],closed=[];let resolves=0;
 const resolver={async resolve(){resolves++;return {ips:['8.8.8.8'],ech:Buffer.from('fixture'),h3:true};},invalidate(){},close(){}};
 const transport=new NativeTransport(config,{resolver,dial:async(host,ip,records,protocol)=>{
  // A dial implementation may complete after its AbortSignal fires.
  await delay(protocol==='h3'?250:1);
  let alive=true;
  return {protocol,ech:true,alive:()=>alive,close(){alive=false;closed.push(protocol);},async request(target,method,headers,body,signal,response){seen.push({protocol,method,body:body.toString(),headers});response.writeHead(201,{'set-cookie':['a=1','b=2']});response.end('ok');}};
 }});
 const backend=await startNativeOutbound(config,{transport});
 try {
  const headers={'X-Danmu-Outbound-Token':backend.token,'X-Danmu-Outbound-Target':'https://api.gamer.com.tw/post','X-Danmu-Outbound-Timeout':'1000','connection':'keep-alive','x-danmu-outbound-extra':'secret'};
  const response=await fetch(backend.endpoint+'/proxy',{method:'POST',body:'submit once',headers});
  assert.equal(response.status,201);assert.equal(await response.text(),'ok');assert.equal(response.headers.getSetCookie().length,2);
  await delay(100);assert(closed.includes('h3'));assert(!closed.includes('h2'));
  await (await fetch(backend.endpoint+'/proxy',{headers})).text();
  assert.equal(resolves,1);assert.equal(seen.length,2);assert.equal(seen[0].body,'submit once');assert.equal(seen[0].protocol,'h2');
  assert.equal(seen[0].headers['x-danmu-outbound-token'],undefined);assert.equal(seen[0].headers.connection,undefined);
 }finally{await backend.close();}
});

test('forced H3 never falls back; business failure is never replayed',async()=>{
 const protocols=[];let requests=0;
 const resolver={async resolve(){return {ips:['8.8.8.8'],h3:true};},invalidate(){},close(){}};
 const transport=new NativeTransport({...config,httpVersion:'h3'},{resolver,dial:async(host,ip,records,protocol)=>{protocols.push(protocol);throw new Error('UDP blocked');}});
 const backend=await startNativeOutbound(config,{transport});
 try {
  const headers={'X-Danmu-Outbound-Token':backend.token,'X-Danmu-Outbound-Target':'https://api.tmdb.org/probe','X-Danmu-Outbound-Timeout':'1000'};
  assert.equal((await fetch(backend.endpoint+'/proxy',{headers})).status,502);assert.deepEqual(protocols,['h3']);
 }finally{await backend.close();}
 const second=new NativeTransport({...config,httpVersion:'h2'},{resolver,dial:async()=>({protocol:'h2',alive:()=>true,close(){},async request(){requests++;throw new Error('POST failed after write');}})});
 try{await assert.rejects(second.request(new URL('https://api.tmdb.org/'), 'POST',{},Buffer.from('body'),new AbortController().signal,{}));assert.equal(requests,1);}finally{second.close();}
});

test('cancelling one coalesced waiter does not cancel the other request',async()=>{
 let dials=0;const resolver={async resolve(){await delay(40);return {ips:['8.8.8.8'],h3:false};},close(){}};
 const transport=new NativeTransport(config,{resolver,dial:async()=>{dials++;return {protocol:'h2',alive:()=>true,close(){}};}});
 const first=new AbortController();
 try {
  const a=transport.acquire('api.tmdb.org',first.signal),b=transport.acquire('api.tmdb.org',new AbortController().signal);
  first.abort(new Error('cancel only first'));await assert.rejects(a,/cancel only first/);
  const lease=await b;assert.equal(dials,1);lease.release();
 }finally{transport.close();}
});

test('slow encrypted DNS and source handshake get separate bounded budgets',async()=>{
 const cfg={...config,connectTimeoutMs:200};
 const resolver={async resolve(host,signal){await waitFor(delay(130),signal);return {ips:['8.8.8.8'],h3:false};},close(){}};
 const transport=new NativeTransport(cfg,{resolver,dial:async(host,ip,records,protocol,signal)=>{
  await waitFor(delay(130),signal);
  return {protocol:'h2',alive:()=>true,close(){}};
 }});
 try {
  const lease=await transport.acquire('api.tmdb.org',AbortSignal.timeout(1000));
  assert.equal(lease.session.protocol,'h2');lease.release();
 }finally{transport.close();}
});

test('authenticated adapter rejects unrelated hosts and enforces response-body cancellation',async()=>{
 let cancelled=false,calls=0;
 const transport={close(){},async request(target,method,headers,body,signal,res){
  calls++;res.writeHead(200);res.write('begin');
  try{await waitFor(new Promise(()=>{}),signal);}finally{cancelled=signal.aborted;}
 }};
 const backend=await startNativeOutbound(config,{transport});
 try {
  const headers={'X-Danmu-Outbound-Token':backend.token,'X-Danmu-Outbound-Timeout':'1000','X-Danmu-Outbound-Target':'https://github.com/'};
  assert.equal((await fetch(backend.endpoint+'/proxy',{headers})).status,403);
  headers['X-Danmu-Outbound-Target']='https://api.tmdb.org/';headers['X-Danmu-Outbound-Token']='wrong';
  assert.equal((await fetch(backend.endpoint+'/proxy',{headers})).status,401);assert.equal(calls,0);
  headers['X-Danmu-Outbound-Token']=backend.token;
  const controller=new AbortController();const response=await fetch(backend.endpoint+'/proxy',{headers,signal:controller.signal});
  controller.abort();await response.body.cancel().catch(()=>{});
  for(let i=0;i<30 && !cancelled;i++)await delay(10);
  assert.equal(cancelled,true);
 }finally{await backend.close();}
});

test('replacing a GOAWAY session waits for its active response to finish',async()=>{
 const made=[];const resolver={async resolve(){return {ips:['8.8.8.8'],h3:false};},close(){}};
 const transport=new NativeTransport(config,{resolver,dial:async()=>{
  const session={protocol:'h2',usable:true,closed:false,alive(){return this.usable;},close(){this.closed=true;}};made.push(session);return session;
 }});
 try {
  const first=await transport.acquire('api.tmdb.org',new AbortController().signal);
  first.session.usable=false;
  const second=await transport.acquire('api.tmdb.org',new AbortController().signal);
  assert.equal(made.length,2);assert.equal(first.session.closed,false);
  first.release();assert.equal(first.session.closed,true);assert.equal(second.session.closed,false);
  second.release();
 }finally{transport.close();}
});
