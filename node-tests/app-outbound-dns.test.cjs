'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const {wireQuery,parseDNS,publicAddress,NativeResolver,serviceRecord,addressHints,interleaveAddresses,echPublicNames,waitFor}=require('../app/src/main/assets/nodejs-project/app-outbound-dns.js');
function answer(query,records){const header=Buffer.from(query);header.writeUInt16BE(0x8180,2);header.writeUInt16BE(records.length,6);return Buffer.concat([header,...records]);}
function rr(type,payload,ttl=60){const header=Buffer.alloc(12);header.writeUInt16BE(0xc00c);header.writeUInt16BE(type,2);header.writeUInt16BE(1,4);header.writeUInt32BE(ttl,6);header.writeUInt16BE(payload.length,10);return Buffer.concat([header,payload]);}

test('DNS codec validates question, bounds, compression and preserves TTL/ECH/ALPN',()=>{
 const query=wireQuery('api.gamer.com.tw',65,1234);
 const ech=Buffer.from([0,1,2]);
 const payload=Buffer.concat([Buffer.from([0,1,0,0,1,0,3,2,104,51,0,5,0,ech.length]),ech]);
 const wire=answer(query,[rr(65,payload,17)]);const result=parseDNS(wire,query);
 assert.equal(result.ttl,17);assert.equal(result.records[0].params.get(1).toString('hex'),'026833');assert(result.records[0].params.get(5).equals(ech));
 assert.equal(serviceRecord(result.records[0]),true);
 const bad=Buffer.from(wire);bad.writeUInt16BE(1235);assert.throws(()=>parseDNS(bad,query),/Mismatched/);
 assert.throws(()=>parseDNS(wire.subarray(0,-1),query),/Truncated/);
 const cycle=Buffer.from(query);cycle[12]=0xc0;cycle[13]=12;cycle.writeUInt16BE(0x8180,2);assert.throws(()=>parseDNS(cycle),/cycle/);
 const mandatory=new Map([[0,Buffer.from([0,99])],[99,Buffer.alloc(0)]]);assert.equal(serviceRecord({type:65,priority:1,params:mandatory}),false);
});
test('public-address filter rejects local, mapped, multicast, reserved and documentation ranges',()=>{
 for(const ip of ['127.0.0.1','10.0.0.1','192.168.1.2','100.64.0.1','169.254.0.1','224.0.0.1','0.0.0.0','::1','fc00::1','fe80::1','::ffff:127.0.0.1','2001:db8::1'])assert.equal(publicAddress(ip),false,ip);
 for(const ip of ['1.1.1.1','8.8.8.8','2606:4700:4700::1111'])assert.equal(publicAddress(ip),true,ip);
});
test('DNS flights coalesce, TTL zero does not cache, and aliases retain the target ECH policy',async()=>{
 let calls=0;
 const config={dohUrl:'https://resolver.invalid/dns-query',connectTimeoutMs:500};
 const resolver=new NativeResolver(config,{exchange:async(endpoint,query)=>{calls++;await new Promise(resolve=>setTimeout(resolve,5));return parseDNS(answer(query,[rr(1,Buffer.from([8,8,8,8]),0)]),query);}});
 try {
  const [a,b]=await Promise.all([resolver.query('api.tmdb.org',1),resolver.query('api.tmdb.org',1)]);assert.equal(calls,1);assert.deepEqual(a,b);
  await resolver.query('api.tmdb.org',1);assert.equal(calls,2);
  resolver.records=async(host,type)=>type===1?[{address:'8.8.8.8'}]:[];
  await assert.rejects(resolver.resolve('api.danmaku.weeblify.app'),/own ECH/);
  assert.equal((await resolver.resolve('api.tmdb.org')).ech,undefined);
  await assert.rejects(resolver.resolve('github.com'),/not allowed/);
 }finally{resolver.close();}
});
test('service alternatives cannot borrow another record ECH or ALPN',async()=>{
 const resolver=new NativeResolver({connectTimeoutMs:500});
 resolver.records=async(host,type)=>type===1?[{address:'8.8.8.8'}]:type===65?[
  {type:65,priority:1,params:new Map([[5,Buffer.from('ech')]])},
  {type:65,priority:2,params:new Map([[1,Buffer.from([2,104,51])]])},
 ]:[];
 try{const result=await resolver.resolve('api.danmaku.weeblify.app');assert.equal(result.ech.toString(),'ech');assert.equal(result.h3,false);}finally{resolver.close();}
});

test('encrypted DoH escapes blackholed IPv4 through DNS-published IPv6 with ECH still required',async()=>{
 const seen=[],config={dohUrl:'https://cloudflare-dns.com/dns-query',connectTimeoutMs:1200};
 const ipv6=Buffer.from('26064700000000000000000068120a76','hex');
 const resolver=new NativeResolver(config,{exchange:async(endpoint,query,signal,ech,ip)=>{
  seen.push({endpoint,ech,ip});
  if(new URL(endpoint).hostname!=='cloudflare-dns.com')return {ttl:60,records:[{name:'cloudflare-ech.com',type:65,priority:1,ttl:60,
   params:new Map([[5,Buffer.from('own resolver ECH')],[4,Buffer.from([127,0,0,1])],[6,ipv6]])}]};
  if(ip==='2606:4700:0:0:0:0:6812:a76') {
   assert.equal(ech.toString(),'own resolver ECH');
   return {ttl:30,host:'api.tmdb.org',type:1,records:[{name:'api.tmdb.org',type:1,address:'8.8.8.8'}]};
  }
  return new Promise((resolve,reject)=>{signal.throwIfAborted();signal.addEventListener('abort',()=>reject(signal.reason),{once:true});});
 }});
 try {
  const result=await resolver.query('api.tmdb.org',1,AbortSignal.timeout(1200));
  assert.equal(result.records[0].address,'8.8.8.8');
  const cloudflare=seen.filter(call=>new URL(call.endpoint).hostname==='cloudflare-dns.com');
  assert(cloudflare.some(call=>call.ip.includes(':')));
  assert(cloudflare.every(call=>call.ech?.length && call.ip!=='127.0.0.1'));
 }finally{resolver.close();}
});

test('DoH ECH rejection refreshes once, including nested failures, without plaintext downgrade',async()=>{
 const resolver=new NativeResolver({dohUrl:'https://cloudflare-dns.com/dns-query',connectTimeoutMs:1000});
 let generation=0,exchanges=0,invalidated=0;
 resolver.sharedECH=async()=>Buffer.from([++generation]);
 resolver.invalidate=host=>{assert.equal(host,'cloudflare-ech.com');invalidated++;};
 resolver.wire=async(host,type,endpoint,ech)=>{
  exchanges++;assert.equal(ech[0],generation);
  if(generation===1)throw new AggregateError([Object.assign(new Error('ech required'),{code:'ERR_SSL_ECH_REQUIRED'})]);
  return {records:[{address:'8.8.8.8'}]};
 };
 try {
  assert.equal((await resolver.query('api.tmdb.org',1)).records[0].address,'8.8.8.8');
  assert.equal(exchanges,2);assert.equal(invalidated,1);
  resolver.wire=async()=>{throw new AggregateError([Object.assign(new Error('ech required'),{code:'ERR_SSL_ECH_REQUIRED'})]);};
  await assert.rejects(resolver.query('api.tmdb.org',1));assert.equal(generation,4);assert.equal(invalidated,2);
 }finally{resolver.close();}
});

test('resolver hints stay public, family-interleaved and tolerate malformed hint lengths',()=>{
 const hints=addressHints({params:new Map([[4,Buffer.from([8,8,8,8,10,0,0,1])],[6,Buffer.from('26064700000000000000000000001111','hex')]])});
 assert.deepEqual(interleaveAddresses([...hints,'8.8.8.8','1.1.1.1']),['8.8.8.8','2606:4700:0:0:0:0:0:1111','1.1.1.1']);
 assert.deepEqual(addressHints({params:new Map([[4,Buffer.alloc(3)],[6,Buffer.alloc(15)]])}),[]);
});

test('a blackholed DNS family cannot hide a usable family and the target own ECH record',async()=>{
 const resolver=new NativeResolver({connectTimeoutMs:1000});
 const cancel=new AbortController();
 resolver.records=async(host,type,signal)=>type===1?[{address:'8.8.8.8'}]:type===65?[
  {type:65,priority:1,params:new Map([[5,Buffer.from('target own key')]])},
 ]:waitFor(new Promise(()=>{}),signal);
 try {
  const result=await resolver.resolve('api.danmaku.weeblify.app',AbortSignal.any([cancel.signal,AbortSignal.timeout(600)]));
  assert.deepEqual(result.ips,['8.8.8.8']);assert.equal(result.ech.toString(),'target own key');
 }finally{cancel.abort();resolver.close();}
});

test('Cloudflare frontends share addresses but never replace a target own ECH config',async()=>{
 const publicName=Buffer.from('cloudflare-ech.com');
 const content=Buffer.concat([Buffer.from([1,0,32,0,32]),Buffer.alloc(32),Buffer.from([0,4,0,1,0,1,0,publicName.length]),publicName,Buffer.from([0,0])]);
 const ech=Buffer.concat([Buffer.from([0,content.length+4,0xfe,0x0d,0,content.length]),content]);
 assert.deepEqual(echPublicNames(ech),['cloudflare-ech.com']);assert.deepEqual(echPublicNames(ech.subarray(0,-1)),[]);
 const resolver=new NativeResolver({connectTimeoutMs:1000});
 resolver.cloudflareFrontend={ips:['8.8.4.4'],expires:Date.now()+60000};
 let advertised=true;
 resolver.records=async(host,type)=>type===1?[{address:'8.8.8.8'}]:type===65 && advertised?[
  {type:65,priority:1,params:new Map([[5,ech]])},
 ]:[];
 try {
  const result=await resolver.resolve('api.danmaku.weeblify.app');
  assert.deepEqual(result.ips,['8.8.8.8','8.8.4.4']);assert(result.ech.equals(ech));
  advertised=false;await assert.rejects(resolver.resolve('api.danmaku.weeblify.app'),/own ECH/);
 }finally{resolver.close();}
});

test('Douban uses its own TLS policy and unapproved hosts stay outside the allowlist',async()=>{
 const resolver=new NativeResolver({connectTimeoutMs:1000});
 resolver.records=async(host,type)=>type===1?[{address:'8.8.8.8'}]:[];
 resolver.sharedECH=async()=>{throw new Error('must not share ECH with Douban');};
 try {
  for(const host of ['m.douban.com','api.douban.com'])assert.equal((await resolver.resolve(host)).ech,undefined);
  for(const host of ['api.tripdata.app','rankv21.tripdata.app','accounts.douban.com','img3.doubanio.com'])await assert.rejects(resolver.resolve(host),/not allowed/);
 }finally{resolver.close();}
});
