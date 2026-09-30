'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {createAppOutboundRuntime}=require('../app/src/main/assets/nodejs-project/app-outbound-runtime.js');
const helper=path.resolve('runtime/outbound/arm64-v8a/libdanmu_outbound.so');
const realHelper=process.platform==='android' && fs.existsSync(helper);

test('App manager launches real Android helper, shares endpoint, reconfigures and closes on stdin EOF',{skip:!realHelper},async()=>{
 const directory=fs.mkdtempSync(path.join(os.tmpdir(),'app-outbound-runtime-'));
 const configPath=path.join(directory,'settings.json');
 const write=config=>{fs.writeFileSync(configPath+'.pending',JSON.stringify(config));fs.renameSync(configPath+'.pending',configPath);};
 const config={enabled:true,sources:['bahamut','tmdb'],httpVersion:'h2'};
 write(config);
 const nativeFetch=globalThis.fetch;
 const snapshots=[];
 const runtime=createAppOutboundRuntime({configPath,helperPath:helper,onSnapshot:s=>snapshots.push(s),log:()=>{}});
 try {
  await runtime.start();
  assert.equal(runtime.snapshot().status,'ready');
  const first=runtime.snapshot();
  const rejected=await nativeFetch(first.endpoint+'/proxy',{headers:{'X-Danmu-Outbound-Target':'https://example.com'}});
  assert.equal(rejected.status,401);
  assert.equal(JSON.parse(fs.readFileSync(path.join(directory,'status.json'))).status,'ready');
  write({...config,httpVersion:'auto'});await runtime.refresh();
  assert.equal(runtime.snapshot().status,'ready');assert.notEqual(runtime.snapshot().endpoint,first.endpoint);
  write({enabled:false});await runtime.refresh();assert.equal(runtime.snapshot().status,'off');
  assert(snapshots.some(s=>s.status==='starting'));
 }finally{
  await runtime.stop();assert.equal(globalThis.fetch,nativeFetch);fs.rmSync(directory,{recursive:true,force:true});
 }
});
test('missing helper produces explicit failure, disabling recovers without affecting ordinary fetch',async()=>{
 const directory=fs.mkdtempSync(path.join(os.tmpdir(),'app-outbound-missing-'));
 const configPath=path.join(directory,'settings.json');fs.writeFileSync(configPath,JSON.stringify({enabled:true}));
 const runtime=createAppOutboundRuntime({configPath,helperPath:path.join(directory,'missing'),log:()=>{}});
 try{
  await runtime.start();assert.equal(runtime.snapshot().status,'failed');
  await assert.rejects(fetch('https://api.gamer.com.tw/no-network'),/不可用/);
  fs.writeFileSync(configPath,JSON.stringify({enabled:false}));await runtime.refresh();assert.equal(runtime.snapshot().status,'off');
 }finally{await runtime.stop();fs.rmSync(directory,{recursive:true,force:true});}
});
test('crashed helper fails closed and is restarted by the host owner',{timeout:8000},async()=>{
 const directory=fs.mkdtempSync(path.join(os.tmpdir(),'app-outbound-restart-'));
 const configPath=path.join(directory,'settings.json');
 const marker=path.join(directory,'started');
 const executable=path.join(directory,'helper');
 fs.writeFileSync(configPath,JSON.stringify({enabled:true}));
 fs.writeFileSync(executable,`#!${process.execPath}
const fs=require('node:fs'),http=require('node:http');
const first=!fs.existsSync(${JSON.stringify(marker)});fs.writeFileSync(${JSON.stringify(marker)},'1');
const server=http.createServer((req,res)=>res.end('ok'));
server.listen(0,'127.0.0.1',()=>console.log(JSON.stringify({ready:true,appProtocol:1,url:'http://127.0.0.1:'+server.address().port})));
if(first)setTimeout(()=>process.exit(2),150);
process.stdin.resume();process.stdin.on('end',()=>server.close());process.on('SIGTERM',()=>server.close());
`);fs.chmodSync(executable,0o755);
 const states=[];
 const runtime=createAppOutboundRuntime({configPath,helperPath:executable,onSnapshot:s=>states.push(s),log:()=>{}});
 try{
  await runtime.start();const initial=runtime.snapshot().token;
  const deadline=Date.now()+5500;
  while(Date.now()<deadline && !(runtime.snapshot().status==='ready' && runtime.snapshot().token!==initial))await new Promise(resolve=>setTimeout(resolve,50));
  assert(states.some(s=>s.status==='failed'));
  assert.equal(runtime.snapshot().status,'ready');assert.notEqual(runtime.snapshot().token,initial);
 }finally{await runtime.stop();fs.rmSync(directory,{recursive:true,force:true});}
});
