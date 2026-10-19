'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {createAppOutboundRuntime}=require('../app/src/main/assets/nodejs-project/app-outbound-runtime.js');
const {startNativeOutbound}=require('../app/src/main/assets/nodejs-project/app-outbound-native.js');
const nativeFetch=globalThis.fetch;
function directory(config){const dir=fs.mkdtempSync(path.join(os.tmpdir(),'app-outbound-native-'));const configPath=path.join(dir,'settings.json');fs.writeFileSync(configPath,JSON.stringify(config));return {dir,configPath};}
const fakeTransport=()=>({async request(target,method,headers,body,signal,response){response.writeHead(200,{'content-type':'application/json'});response.end(JSON.stringify({host:target.hostname,pid:process.pid}));},close(){}});

test('in-process backend shares endpoint, reconfigures and stops without executable helper',async()=>{
 const {dir,configPath}=directory({enabled:true,httpVersion:'h2'});const snapshots=[];
 const runtime=createAppOutboundRuntime({configPath,startBackend:config=>startNativeOutbound(config,{transport:fakeTransport()}),onSnapshot:s=>snapshots.push(s)});
 try {
  await runtime.start();const first=runtime.snapshot();assert.equal(first.status,'ready');assert.equal(first.backend,'node');
  assert.equal((await nativeFetch(first.endpoint+'/proxy')).status,401);
  assert.equal((await (await fetch('https://api.gamer.com.tw/probe')).json()).pid,process.pid);
  assert.equal(JSON.parse(fs.readFileSync(path.join(dir,'status.json'))).status,'ready');
  await runtime.refresh();assert.equal(runtime.snapshot().endpoint,first.endpoint);
  fs.writeFileSync(configPath,JSON.stringify({enabled:true,httpVersion:'h2',proxy:{enabled:true,routeVersion:2,rules:{aiyifan:'direct'}}}));
  await runtime.refresh();assert.equal(runtime.snapshot().endpoint,first.endpoint);
  assert.equal(runtime.snapshot().config.proxy.rules.aiyifan,'direct');
  fs.writeFileSync(configPath,JSON.stringify({enabled:true,httpVersion:'auto'}));await runtime.refresh();
  assert.equal(runtime.snapshot().status,'ready');assert.notEqual(runtime.snapshot().token,first.token);
  await assert.rejects(nativeFetch(first.endpoint+'/proxy'));
  fs.writeFileSync(configPath,JSON.stringify({enabled:false}));await runtime.refresh();assert.equal(runtime.snapshot().status,'off');
  assert(snapshots.some(s=>s.status==='starting'));
 }finally{await runtime.stop();fs.rmSync(dir,{recursive:true,force:true});}
});

test('unsupported runtime fails closed and disabling restores ordinary fetch',async()=>{
 const {dir,configPath}=directory({enabled:true});
 const runtime=createAppOutboundRuntime({configPath,startBackend:config=>startNativeOutbound(config,{capabilities:{ech:false}})});
 try {
  await runtime.start();assert.equal(runtime.snapshot().status,'failed');
  await assert.rejects(fetch('https://api.gamer.com.tw/no-network'),/Node 26.*OpenSSL 4/);
  fs.writeFileSync(configPath,JSON.stringify({enabled:false}));await runtime.refresh();assert.equal(runtime.snapshot().status,'off');
 }finally{await runtime.stop();fs.rmSync(dir,{recursive:true,force:true});}
});

test('failed native startup can retry and config parse failure closes previous backend',async()=>{
 const {dir,configPath}=directory({enabled:true});let count=0,closed=0;
 const runtime=createAppOutboundRuntime({configPath,startBackend:async config=>{
  if(count++===0)throw new Error('fixture startup failure');
  const transport=fakeTransport();transport.close=()=>closed++;
  return startNativeOutbound(config,{transport});
 }});
 try {
  await runtime.start();assert.equal(runtime.snapshot().status,'failed');
  await runtime.refresh();assert.equal(runtime.snapshot().status,'ready');
  fs.writeFileSync(configPath,'{');await runtime.refresh();assert.equal(runtime.snapshot().status,'failed');assert.equal(closed,1);
 }finally{await runtime.stop();fs.rmSync(dir,{recursive:true,force:true});}
});

test('old extended nodes do not disable enhanced settings and a removed binding never becomes direct',async()=>{
 const {dir,configPath}=directory({enabled:true,sources:['douban'],httpVersion:'h2',proxy:{enabled:true,
  subscriptions:[{url:'https://private.test/subscription'}],
  nodes:[{id:'old',name:'old',type:'trojan',host:'node.test',port:443,password:'private'},
   {id:'native',name:'native',type:'http',host:'127.0.0.1',port:12345,subscriptionId:'old',protocolOptions:{}}],rules:{aiyifan:'node:old'}}});
 const runtime=createAppOutboundRuntime({configPath,startBackend:config=>startNativeOutbound(config,{transport:fakeTransport()})});
 try{
  await runtime.start();const state=runtime.snapshot();assert.equal(state.status,'ready');assert.deepEqual(state.config.sources,['douban']);
  assert.deepEqual(state.config.proxy.nodes.map(x=>x.id),['native']);assert.equal(state.config.proxy.rules.aiyifan,'node:old');
  assert.equal(state.config.proxy.subscriptions,undefined);assert(!JSON.stringify(state).includes('private.test'));
  await assert.rejects(fetch('https://api.tripdata.app/api/home/config'),/节点已失效/);
  const status=JSON.parse(fs.readFileSync(path.join(dir,'status.json')));assert.match(status.networkRuntimeFingerprint,/^[a-f0-9]{64}$/);
 }finally{await runtime.stop();fs.rmSync(dir,{recursive:true,force:true});}
});

const crypto=require('node:crypto');
test('runtime fingerprint covers every shipped host script and changes for DNS native and refresh updates',()=>{
 const directory=path.resolve('app/src/main/assets/nodejs-project');
 const scripts=JSON.parse(fs.readFileSync(path.join(directory,'app-runtime-assets.json'),'utf8'));
 assert.deepEqual(scripts,fs.readdirSync(directory).filter(x=>x.endsWith('.js')).sort());
 const names=['app-runtime-assets.json',...scripts];
 const fingerprint=changed=>{const hash=crypto.createHash('sha256');for(const name of names){hash.update(name+'\0');hash.update(fs.readFileSync(path.join(directory,name)));if(name===changed)hash.update('changed');}return hash.digest('hex');};
 assert.equal(fingerprint(),require('../app/src/main/assets/nodejs-project/app-outbound-runtime.js').networkRuntimeFingerprint);
 for(const name of ['android-server.js','worker-proxy.js','app-outbound-dns.js','app-outbound-native.js','app-series-refresh.js'])assert.notEqual(fingerprint(name),fingerprint());
});
