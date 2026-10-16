'use strict';
// Read-only live verification of Aiyifan through an explicit native proxy route.
// The loopback proxy records CONNECT destinations even while the system VPN is on.
const assert=require('node:assert/strict'),net=require('node:net');
const {once}=require('node:events'),path=require('node:path'),{pathToFileURL}=require('node:url');
const routing=require('../app/src/main/assets/nodejs-project/app-proxy-routing.js');
const {installBridge}=require('../app/src/main/assets/nodejs-project/app-outbound-bridge.js');
const core=process.env.AIYIFAN_TEST_CORE;
if(!core)throw new Error('Set AIYIFAN_TEST_CORE to an isolated installed core source copy');
const sockets=new Set();const seen=[];
function track(socket){sockets.add(socket);socket.on('error',()=>{});socket.once('close',()=>sockets.delete(socket));return socket;}
async function read(socket,size){for(;;){const value=socket.read(size);if(value)return value;if(socket.destroyed||socket.readableEnded)throw new Error('closed');await once(socket,'readable');}}
async function fixture(type,action){
 let reject=false;
 const start=seen.length;
 const server=net.createServer(socket=>{track(socket);(async()=>{
  let host,port;
  if(type==='socks5'){
   const hello=await read(socket,2);await read(socket,hello[1]);socket.write(Buffer.from([5,2]));
   const head=await read(socket,2),user=(await read(socket,head[1])).toString();const size=(await read(socket,1))[0],password=(await read(socket,size)).toString();
   assert.equal(user,'test');assert.equal(password,'test');socket.write(Buffer.from([1,reject?1:0]));if(reject){socket.end();return;}
   const request=await read(socket,5);assert.equal(request[3],3);host=(await read(socket,request[4])).toString();port=(await read(socket,2)).readUInt16BE();
  }else{
   let headers='';while(!headers.endsWith('\r\n\r\n')){assert(headers.length<16384);headers+=(await read(socket,1)).toString();}
   assert(headers.includes('Proxy-Authorization: Basic dGVzdDp0ZXN0'));
   if(reject){socket.end('HTTP/1.1 407 Proxy Authentication Required\r\n\r\n');return;}
   const authority=headers.split(' ')[1];host=authority.split(':')[0];port=Number(authority.split(':')[1]);
  }
  assert(['api.tripdata.app','rankv21.tripdata.app'].includes(host));assert.equal(port,443);
  seen.push({type,host,port});
  const upstream=track(net.connect({host,port}));await once(upstream,'connect');
  socket.write(type==='socks5'?Buffer.from([5,0,0,1,127,0,0,1,0,0]):'HTTP/1.1 200 OK\r\n\r\n');
  socket.pipe(upstream);upstream.pipe(socket);socket.on('close',()=>upstream.destroy());upstream.on('close',()=>socket.destroy());
 })().catch(error=>socket.destroy(error));});
 server.listen(0,'127.0.0.1');await once(server,'listening');
 const config={enabled:false,proxy:{enabled:true,nodes:[{id:'local',name:'local verification',type,host:'127.0.0.1',port:server.address().port,username:'test',password:'test'}],rules:{aiyifan:'node:local'}}};
 const bridge=installBridge({snapshot:{config}});
 try {
  await routing.bindCoreSourceContext(core);
  await action();
  const prior=seen.length;reject=true;
  await assert.rejects(fetch('https://api.tripdata.app/api/home/config',{signal:AbortSignal.timeout(4000)}));
  assert.equal(seen.length,prior);
  console.log(JSON.stringify({type,connections:seen.length-start,targets:[...new Set(seen.slice(start).map(x=>x.host))],rejectedProxyDoesNotFallBack:true}));
 }finally{bridge.stop();for(const socket of sockets)socket.destroy();await new Promise(r=>server.close(r));}
}
(async()=>{
 const originalLog=console.log,originalWarn=console.warn,originalError=console.error;
 // Core logs include URLs/signature query strings. Only emit test result summaries.
 for(const type of ['http','socks5'])await fixture(type,async()=>{
  console.log=console.warn=console.error=()=>{};
  let count;
  try{
   const {AiyifanAppSigningProvider}=await import(pathToFileURL(path.join(core,'utils/aiyifan-util.js')));
   const provider=new AiyifanAppSigningProvider({timeoutMs:10000});
   const result=await provider.signedPostJson('https://api.tripdata.app/api/List/GetTitleGetData',{SearchCriteria:'海贼王'},'native proxy verification');
   count=result?.data?.data?.list?.length;
   assert(Number.isInteger(count) && count>0,'Aiyifan search must return real results');
  }finally{console.log=originalLog;console.warn=originalWarn;console.error=originalError;}
  console.log(JSON.stringify({type,aiyifanSearchResults:count,actualCoreSigningProvider:true}));
 });
})().catch(error=>{console.error(JSON.stringify({failed:true,errorType:error.name,code:error.code || null}));process.exitCode=1;});
