'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const {NativeTransport,startNativeOutbound,dialH3}=require('../app/src/main/assets/nodejs-project/app-outbound-native.js');
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

function h3Fixture({connectError,connectGate,opened=Promise.resolve({protocol:'h3',echAccepted:true})}={}) {
 const endpoints=[],sessions=[];
 class QuicEndpoint {
  constructor(options){this.options=options;this.closeCalls=0;endpoints.push(this);}
  async close(){this.closeCalls++;}
 }
 const quic={QuicEndpoint,async connect(address,options){
  const endpoint=options.endpoint instanceof QuicEndpoint?options.endpoint:new QuicEndpoint(options.endpoint);
  if(connectError)throw connectError;
  const closed=Promise.withResolvers();
  const session={opened,closed:closed.promise,destroyed:false,closing:false,
   get endpoint(){return this.destroyed?null:endpoint;},
   destroy(error){if(this.destroyed)return;this.destroyed=true;if(error)closed.reject(error);else closed.resolve();},
  };
  sessions.push(session);
  await connectGate;
  return session;
 }};
 return {quic,endpoints,sessions,dial:signal=>dialH3(quic,'api.tmdb.org','8.8.8.8',{},signal||new AbortController().signal)};
}
const nextTurn=()=>new Promise(resolve=>setImmediate(resolve));

for(const failed of [false,true])test(`H3 releases its endpoint when the peer closes ${failed?'with an error':'normally'}`,async()=>{
 const fixture=h3Fixture();const connection=await fixture.dial();
 fixture.sessions[0].destroy(failed?new Error('peer disconnected'):undefined);
 await nextTurn();
 assert.equal(connection.alive(),false);
 assert.equal(fixture.sessions[0].endpoint,null);
 assert.equal(fixture.endpoints[0].closeCalls,1);
 connection.close();connection.close();
 assert.equal(fixture.endpoints[0].closeCalls,1);
});

test('H3 releases its endpoint when connect fails before returning a session',async()=>{
 const error=new Error('connect failed');const fixture=h3Fixture({connectError:error});
 await assert.rejects(fixture.dial(),error);
 assert.equal(fixture.sessions.length,0);
 assert.equal(fixture.endpoints[0].closeCalls,1);
});

test('H3 releases its endpoint when the handshake destroys the session',async()=>{
 const handshake=Promise.withResolvers();const fixture=h3Fixture({opened:handshake.promise});
 const pending=fixture.dial();await nextTurn();
 const error=new Error('handshake timed out');
 fixture.sessions[0].destroy(error);handshake.reject(error);
 await assert.rejects(pending,error);
 assert.equal(fixture.endpoints[0].closeCalls,1);
});

test('H3 cancellation before dial does not allocate an endpoint',async()=>{
 const fixture=h3Fixture();const controller=new AbortController();controller.abort();
 await assert.rejects(fixture.dial(controller.signal),{name:'AbortError'});
 assert.equal(fixture.endpoints.length,0);
});

test('H3 cancellation while connect is pending closes the endpoint and the late session',async()=>{
 const gate=Promise.withResolvers();const fixture=h3Fixture({connectGate:gate.promise});
 const controller=new AbortController();const pending=fixture.dial(controller.signal);
 await nextTurn();controller.abort(new Error('dial cancelled'));gate.resolve();
 await assert.rejects(pending,/dial cancelled/);
 assert.equal(fixture.endpoints[0].closeCalls,1);
 assert.equal(fixture.sessions[0].destroyed,true);
});

test('H3 handshake cancellation releases the endpoint once',async()=>{
 const fixture=h3Fixture({opened:new Promise(()=>{})});const controller=new AbortController();
 const pending=fixture.dial(controller.signal);await nextTurn();
 controller.abort(new Error('handshake cancelled'));
 await assert.rejects(pending,/handshake cancelled/);
 assert.equal(fixture.sessions[0].destroyed,true);
 assert.equal(fixture.endpoints[0].closeCalls,1);
});

test('H3 detaches the dial signal after opening and closes explicitly only once',async()=>{
 const fixture=h3Fixture();const controller=new AbortController();const connection=await fixture.dial(controller.signal);
 controller.abort();assert.equal(connection.alive(),true);
 assert.equal(fixture.endpoints[0].closeCalls,0);
 connection.close();connection.close();await nextTurn();
 assert.equal(fixture.endpoints[0].closeCalls,1);
 assert.equal(fixture.sessions[0].destroyed,true);
});

function h3Stream() {
 const closed=Promise.withResolvers();
 return {closed:closed.promise,destroyed:false,destroyError:undefined,
  destroy(error){if(this.destroyed)return;this.destroyed=true;this.destroyError=error;if(error)closed.reject(error);else closed.resolve();},
 };
}

test('H3 destroys a stream delivered after request cancellation without unhandled rejections',async()=>{
 const fixture=h3Fixture();const connection=await fixture.dial();const creation=Promise.withResolvers();
 fixture.sessions[0].createBidirectionalStream=()=>creation.promise;
 const controller=new AbortController();const error=new Error('request cancelled during stream creation');
 const pending=connection.request(new URL('https://api.tmdb.org/'),'GET',{},Buffer.alloc(0),controller.signal,{});
 controller.abort(error);await assert.rejects(pending,error);
 const stream=h3Stream();creation.resolve(stream);await nextTurn();
 assert.equal(stream.destroyed,true);assert.equal(stream.destroyError,error);
 assert.equal(connection.alive(),true);connection.close();
});

test('H3 handles stream creation failure delivered after request cancellation',async()=>{
 const fixture=h3Fixture();const connection=await fixture.dial();const creation=Promise.withResolvers();
 fixture.sessions[0].createBidirectionalStream=()=>creation.promise;
 const controller=new AbortController();
 const pending=connection.request(new URL('https://api.tmdb.org/'),'GET',{},Buffer.alloc(0),controller.signal,{});
 controller.abort(new Error('cancel before creation fails'));await assert.rejects(pending,/cancel before creation fails/);
 creation.reject(new Error('late stream creation failure'));await nextTurn();
 assert.equal(connection.alive(),true);connection.close();
});

test('H3 cancellation before headers destroys only the request stream',async()=>{
 const fixture=h3Fixture();const connection=await fixture.dial();const stream=h3Stream();
 fixture.sessions[0].createBidirectionalStream=async()=>stream;
 const controller=new AbortController();
 const pending=connection.request(new URL('https://api.tmdb.org/'),'GET',{},Buffer.alloc(0),controller.signal,{});
 await nextTurn();controller.abort(new Error('cancel before headers'));
 await assert.rejects(pending,/cancel before headers/);
 assert.equal(stream.destroyed,true);assert.equal(connection.alive(),true);connection.close();
});

test('H3 handles a stream closing before response headers',async()=>{
 const fixture=h3Fixture();const connection=await fixture.dial();const stream=h3Stream();
 fixture.sessions[0].createBidirectionalStream=async()=>stream;
 const pending=connection.request(new URL('https://api.tmdb.org/'),'GET',{},Buffer.alloc(0),new AbortController().signal,{});
 await nextTurn();stream.destroy(new Error('stream failed before headers'));
 await assert.rejects(pending,/stream failed before headers/);
 assert.equal(connection.alive(),true);connection.close();
});

test('H3 sends one request and keeps the session available after a successful response',async()=>{
 const fixture=h3Fixture();const connection=await fixture.dial();let requests=0;
 fixture.sessions[0].createBidirectionalStream=async options=>{
  requests++;assert.equal(options.headers[':method'],'POST');assert.equal(options.body.toString(),'one body');
  const stream=h3Stream();stream[Symbol.asyncIterator]=async function*(){yield [Buffer.from('reply')];};
  options.onheaders({':status':'201','content-type':'text/plain'});return stream;
 };
 let body='',ended=false;
 const downstream={writeHead(status,headers){assert.equal(status,201);assert.equal(headers['content-type'],'text/plain');},
  write(part){body+=part;return true;},end(){ended=true;}};
 await connection.request(new URL('https://api.tmdb.org/'),'POST',{},Buffer.from('one body'),new AbortController().signal,downstream);
 assert.equal(body,'reply');assert.equal(ended,true);assert.equal(requests,1);
 assert.equal(connection.alive(),true);assert.equal(fixture.endpoints[0].closeCalls,0);connection.close();
});
