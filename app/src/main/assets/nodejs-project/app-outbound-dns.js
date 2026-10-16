'use strict';
const { randomInt } = require('node:crypto');
const { isIP, SocketAddress } = require('node:net');
const { request: httpsRequest } = require('node:https');

const POLICIES = new Map([
  ['api.gamer.com.tw', { source:'bahamut', ech:true, shared:true }],
  ['api.tmdb.org', { source:'tmdb' }],
  ['api.themoviedb.org', { source:'tmdb' }],
  ['api.danmaku.weeblify.app', { source:'dandan', ech:true }],
  ['nipaplay.aimes-soft.com', { source:'dandan' }],
  ['api.animeko.org', { source:'animeko' }],
  ['danmaku-global.myani.org', { source:'animeko', ech:true }],
  ['danmaku-cn.myani.org', { source:'animeko' }],
  ['s1.animeko.openani.org', { source:'animeko' }],
  ['api.bangumi.vip', { source:'animeko', ech:true }],
  ['m.douban.com', { source:'douban' }],
  ['api.douban.com', { source:'douban' }],
]);
// Resolver infrastructure only. Business addresses and ECH keys come from DNS.
const BOOTSTRAP = new Map([
  ['dns.alidns.com', ['223.5.5.5','223.6.6.6']],
  ['doh.pub', ['1.12.12.12','120.53.53.53']],
  ['cloudflare-dns.com', ['104.16.248.249','2606:4700::6810:f8f9','104.16.249.249','2606:4700::6810:f9f9']],
  ['dns.google', ['8.8.8.8','2001:4860:4860::8888','8.8.4.4','2001:4860:4860::8844']],
]);
function publicAddress(address) {
  if (isIP(address) === 4) {
    const [a,b,c] = address.split('.').map(Number);
    return a > 0 && a < 224 && a !== 10 && a !== 127 &&
      !(a === 100 && b >= 64 && b <= 127) && !(a === 169 && b === 254) &&
      !(a === 172 && b >= 16 && b <= 31) && !(a === 192 && (b === 168 || b === 0 || (b === 0 && c === 2))) &&
      !(a === 198 && (b === 18 || b === 19 || b === 51)) && !(a === 203 && b === 0 && c === 113);
  }
  if (isIP(address) !== 6) return false;
  // Only IPv6 global unicast; excludes ULA, link-local, mapped IPv4 and multicast.
  const normalized = new SocketAddress({ address, family:'ipv6' }).address;
  return /^[23]/.test(normalized) && !/^2001:db8(?=:|$)/i.test(normalized);
}
function wireQuery(host, type, id = randomInt(65536)) {
  const labels = host.toLowerCase().replace(/\.$/, '').split('.');
  if (labels.some(label => !/^[a-z0-9_-]{1,63}$/.test(label)) || host.length > 253) throw new Error('Invalid DNS name');
  const name = Buffer.concat([...labels.flatMap(label => [Buffer.from([label.length]),Buffer.from(label)]), Buffer.from([0])]);
  const header = Buffer.alloc(12); header.writeUInt16BE(id); header.writeUInt16BE(0x100,2); header.writeUInt16BE(1,4);
  const question = Buffer.alloc(4); question.writeUInt16BE(type); question.writeUInt16BE(1,2);
  return Buffer.concat([header,name,question]);
}
function parseDNS(input, expected) {
  const data = Buffer.from(input);
  function need(offset, count) { if (offset < 0 || offset + count > data.length) throw new Error('Truncated DNS response'); }
  function name(start) {
    let offset=start, end, bytes=0; const labels=[], seen=new Set();
    for (let n=0;n<128;n++) {
      need(offset,1); if (seen.has(offset)) throw new Error('DNS compression cycle'); seen.add(offset);
      const size=data[offset++];
      if ((size & 0xc0) === 0xc0) {
        need(offset,1); if (end === undefined) end=offset+1;
        offset=((size & 63)<<8)|data[offset]; continue;
      }
      if (size & 0xc0) throw new Error('Invalid DNS label');
      if (!size) return { value:labels.join('.').toLowerCase(), end:end ?? offset };
      need(offset,size); bytes+=size+1; if (bytes>254) throw new Error('DNS name too long');
      labels.push(data.toString('ascii',offset,offset+size)); offset+=size;
    }
    throw new Error('DNS name too deep');
  }
  need(0,12);
  const flags=data.readUInt16BE(2);
  if (!(flags&0x8000) || flags&0x7800 || flags&0x200 || flags&15 || data.readUInt16BE(4)!==1) throw new Error('Invalid DNS response');
  const question=name(12); need(question.end,4);
  const type=data.readUInt16BE(question.end);
  if (data.readUInt16BE(question.end+2)!==1 || (expected && (data.readUInt16BE(0)!==expected.readUInt16BE(0) || !data.subarray(12,question.end+4).equals(expected.subarray(12))))) throw new Error('Mismatched DNS question');
  let offset=question.end+4;
  const records=[]; let ttl=15;
  const count=data.readUInt16BE(6)+data.readUInt16BE(8)+data.readUInt16BE(10);
  if (count>2048) throw new Error('Too many DNS records');
  for (let i=0;i<count;i++) {
    const owner=name(offset); offset=owner.end; need(offset,10);
    const kind=data.readUInt16BE(offset), klass=data.readUInt16BE(offset+2), age=data.readUInt32BE(offset+4), size=data.readUInt16BE(offset+8);
    offset+=10; need(offset,size); const end=offset+size;
    const rr={ name:owner.value, type:kind, ttl:age };
    if (klass===1 && i<data.readUInt16BE(6)) {
      if (kind===1 && size===4) rr.address=[...data.subarray(offset,end)].join('.');
      if (kind===28 && size===16) rr.address=Array.from({length:8},(_,j)=>data.readUInt16BE(offset+j*2).toString(16)).join(':');
      if (kind===5) { const target=name(offset); if (target.end!==end) throw new Error('Invalid CNAME'); rr.target=target.value; }
      if (kind===65) {
        need(offset,3); if (size<3) throw new Error('Invalid HTTPS RR');
        rr.priority=data.readUInt16BE(offset); const target=name(offset+2); rr.target=target.value;
        if (target.end>end) throw new Error('Invalid HTTPS target');
        rr.params=new Map(); let cursor=target.end, previous=-1;
        while(cursor<end) {
          if(cursor+4>end) throw new Error('Truncated SvcParam');
          const key=data.readUInt16BE(cursor), length=data.readUInt16BE(cursor+2); cursor+=4;
          if(key<=previous || cursor+length>end) throw new Error('Invalid SvcParams'); previous=key;
          rr.params.set(key,Buffer.from(data.subarray(cursor,cursor+length))); cursor+=length;
        }
      }
      records.push(rr);
      ttl=records.length===1 ? age : Math.min(ttl,age);
    }
    offset=end;
  }
  return { host:question.value,type,records,ttl:Math.min(ttl,86400) };
}
function serviceRecord(rr) {
  if (rr.type!==65 || !rr.priority) return false;
  // Never mix ECH/ALPN from incompatible alternatives or unknown mandatory keys.
  const mandatory=rr.params.get(0);
  if(mandatory) {
    if(mandatory.length%2) return false;
    for(let i=0;i<mandatory.length;i+=2) {
      const key=mandatory.readUInt16BE(i);
      if(![1,2,3,4,5,6].includes(key) || !rr.params.has(key)) return false;
    }
  }
  const port=rr.params.get(3);
  return !port || (port.length===2 && port.readUInt16BE()===443);
}
function alpnList(data) {
  if(!data) return [];
  const protocols=[];
  for(let i=0;i<data.length;) {
    const size=data[i++]; if(!size || i+size>data.length) throw new Error('Invalid HTTPS ALPN');
    protocols.push(data.toString('ascii',i,i+size)); i+=size;
  }
  return protocols;
}
function addressHints(record) {
  const ips=[];
  for(const [key,size]of [[4,4],[6,16]]) {
    const bytes=record.params.get(key);
    if(!bytes || bytes.length%size)continue;
    for(let i=0;i<bytes.length;i+=size) {
      const ip=size===4 ? [...bytes.subarray(i,i+size)].join('.')
        : Array.from({length:8},(_,j)=>bytes.readUInt16BE(i+j*2).toString(16)).join(':');
      if(publicAddress(ip))ips.push(ip);
    }
  }
  return ips;
}
function interleaveAddresses(ips) {
  const v4=ips.filter(ip=>isIP(ip)===4),v6=ips.filter(ip=>isIP(ip)===6),result=[];
  for(let i=0;i<Math.max(v4.length,v6.length);i++)for(const ip of [v4[i],v6[i]])if(ip)result.push(ip);
  return [...new Set(result)];
}
function isECHRejection(error) {
  return Boolean(error && (/ECH_REQUIRED|ECH_REJECTED|ech required|ech rejected/i.test(`${error.code||''} ${error.message||''}`)
    || error.errors?.some(isECHRejection) || isECHRejection(error.cause)));
}
function echPublicNames(config) {
  const names=[];
  if(!Buffer.isBuffer(config) || config.length<2 || config.readUInt16BE()!==config.length-2)return names;
  for(let offset=2;offset+4<=config.length;) {
    const version=config.readUInt16BE(offset),length=config.readUInt16BE(offset+2),end=offset+4+length;
    if(end>config.length)return [];
    if(version===0xfe0d) {
      let cursor=offset+4+3;
      if(cursor+2>end)return [];
      cursor+=2+config.readUInt16BE(cursor);
      if(cursor+2>end)return [];
      cursor+=2+config.readUInt16BE(cursor);
      if(cursor+2>end)return [];
      const size=config[cursor+1];cursor+=2;
      if(!size || cursor+size+2>end)return [];
      const name=config.toString('ascii',cursor,cursor+size).toLowerCase();cursor+=size;
      if(cursor+2+config.readUInt16BE(cursor)!==end)return [];
      names.push(name);
    }
    offset=end;
  }
  return names;
}
function waitFor(promise, signal) {
  signal?.throwIfAborted();
  if(!signal) return promise;
  return new Promise((resolve,reject)=>{
    const abort=()=>reject(signal.reason);
    signal.addEventListener('abort',abort,{once:true});
    promise.then(resolve,reject).finally(()=>signal.removeEventListener('abort',abort));
  });
}
function postDNS(endpoint, query, signal, echConfigList, address) {
  return new Promise((resolve,reject)=>{
    const url=new URL(endpoint);
    const request=httpsRequest(url,{
      method:'POST',agent:false,signal,servername:url.hostname,minVersion:'TLSv1.3',
      ...(echConfigList ? {echConfigList} : {}),
      ...(address ? {hostname:address,servername:url.hostname,headers:{host:url.host,'content-type':'application/dns-message',accept:'application/dns-message'}} : {headers:{'content-type':'application/dns-message',accept:'application/dns-message'}}),
    },response=>{
      if(response.statusCode!==200) {response.destroy(); reject(new Error('DoH HTTP failure')); return;}
      const parts=[]; let size=0;
      response.on('data',part=>{size+=part.length;if(size>65535)response.destroy(new Error('DoH response too large'));else parts.push(part);});
      response.once('error',reject);
      response.once('end',()=>{try{resolve(parseDNS(Buffer.concat(parts),query));}catch(error){reject(error);}});
    });
    request.once('error',reject); request.end(query);
  });
}
class NativeResolver {
  constructor(config, { exchange=postDNS }={}) {
    this.config=config; this.exchange=exchange; this.cache=new Map(); this.flights=new Map(); this.chains=new Map(); this.stopper=new AbortController();
  }
  async wire(host,type,endpoint,echConfigList) {
    const key=`${endpoint}|${host}|${type}|${echConfigList?.toString('base64')||''}`;
    const cached=this.cache.get(key); if(cached && cached.expires>Date.now())return cached.answer;
    if(this.flights.has(key))return this.flights.get(key);
    const task=(async()=>{
      const signal=AbortSignal.any([this.stopper.signal,AbortSignal.timeout(this.config.connectTimeoutMs)]);
      const resolverHost=new URL(endpoint).hostname;
      // Cloudflare publishes public frontend addresses with its shared ECH
      // record. Try those alongside the resolver's own IPv4/IPv6 addresses;
      // every connection still verifies cloudflare-dns.com and accepted ECH.
      const frontend=resolverHost==='cloudflare-dns.com' && echConfigList && this.cloudflareFrontend?.expires>Date.now()
        && this.cloudflareFrontend.ech.equals(echConfigList)
        ? this.cloudflareFrontend.ips : [];
      const addresses=interleaveAddresses([...frontend,...(BOOTSTRAP.get(resolverHost)||[])]);
      if(!addresses.length)addresses.push(undefined);
      const query=wireQuery(host,type); const controllers=addresses.map(()=>new AbortController());
      try {
        const answer=await Promise.any(addresses.map(async(address,i)=>{
          if(i) await waitFor(new Promise(resolve=>setTimeout(resolve,Math.min(i*100,300))),signal);
          return this.exchange(endpoint,query,AbortSignal.any([signal,controllers[i].signal]),echConfigList,address);
        }));
        if(this.cache.size>=512)this.cache.delete(this.cache.keys().next().value);
        if(answer.ttl>0)this.cache.set(key,{answer,expires:Date.now()+answer.ttl*1000});
        return answer;
      } finally {controllers.forEach(controller=>controller.abort());}
    })();
    this.flights.set(key,task);
    try{return await task;}finally{this.flights.delete(key);}
  }
  async sharedECH(signal) {
    const {answer,record}=await waitFor(Promise.any(['https://dns.alidns.com/dns-query','https://doh.pub/dns-query'].map(async endpoint=>{
      const answer=await this.wire('cloudflare-ech.com',65,endpoint);
      const record=answer.records.find(rr=>rr.name==='cloudflare-ech.com' && serviceRecord(rr) && rr.params.get(5)?.length);
      if(!record)throw new Error('DNS has no shared ECHConfigList');
      return {answer,record};
    })),signal);
    this.cloudflareFrontend={ech:record.params.get(5),ips:addressHints(record),expires:Date.now()+Math.min(answer.ttl,record.ttl)*1000};
    return record.params.get(5);
  }
  async query(host,type,signal) {
    if(this.config.dohUrl) {
      const endpoint=this.config.dohUrl;
      if(new URL(endpoint).hostname==='cloudflare-dns.com')return this.encryptedQuery(host,type,endpoint,signal);
      return waitFor(this.wire(host,type,endpoint),signal);
    }
    const attempts=[
      async()=>this.encryptedQuery(host,type,'https://cloudflare-dns.com/dns-query',signal),
      async()=>this.wire(host,type,'https://dns.google/dns-query'),
      async()=>this.wire(host,type,'https://cloudflare-dns.com/dns-query'),
    ];
    return waitFor(Promise.any(attempts.map(async(run,i)=>{
      if(i)await waitFor(new Promise(resolve=>setTimeout(resolve,150)),signal);
      return run();
    })),signal);
  }
  async encryptedQuery(host,type,endpoint,signal) {
    for(let retry=0;retry<2;retry++) {
      const ech=await this.sharedECH(signal);
      try{return await waitFor(this.wire(host,type,endpoint,ech),signal);}
      catch(error) {
        if(retry || !isECHRejection(error))throw error;
        this.invalidate('cloudflare-ech.com');
      }
    }
  }
  async records(host,type,signal) {
    const seen=new Set(); const chain=[];
    for(let hop=0;hop<6;hop++) {
      if(seen.has(host))throw new Error('DNS alias cycle'); seen.add(host); chain.push(host);
      const answer=await this.query(host,type,signal); const allowed=new Set([host]);
      for(let n=0;n<6;n++)for(const rr of answer.records)if(rr.type===5 && allowed.has(rr.name))allowed.add(rr.target);
      const records=answer.records.filter(rr=>allowed.has(rr.name) && rr.type===type && (type!==65 || serviceRecord(rr)));
      if(records.length){if(type===65)this.chains.set(chain[0],chain);return records;}
      const alias=answer.records.find(rr=>allowed.has(rr.name) && (rr.type===5 || (type===65 && rr.type===65 && rr.priority===0)));
      if(!alias?.target){if(type===65)this.chains.set(chain[0],chain);return [];}
      host=alias.target;
    }
    throw new Error('DNS alias chain too long');
  }
  async resolve(host,signal) {
    const policy=POLICIES.get(host); if(!policy)throw new Error('Target not allowed');
    const families=[[],[]];
    const addressQueries=[1,28].map((type,index)=>this.records(host,type,signal).then(records=>{
      families[index]=records;
      if(!records.some(rr=>rr.address && publicAddress(rr.address)))throw new Error('DNS family has no public address');
      return records;
    }));
    const httpsQuery=this.records(host,65,signal).catch(()=>[]);
    // A working family is sufficient. Give the other a short opportunity to
    // join the handshake race instead of waiting out a blackholed AAAA/A query.
    try {await waitFor(Promise.any(addressQueries),signal);}
    catch(error){signal?.throwIfAborted();throw new Error('DNS returned no public target address',{cause:error});}
    const grace=()=>new Promise(resolve=>setTimeout(resolve,150));
    const [,https]=await Promise.all([
      waitFor(Promise.race([Promise.allSettled(addressQueries),grace()]),signal),
      waitFor(policy.ech && !policy.shared ? httpsQuery : Promise.race([httpsQuery,grace().then(()=>[])]),signal),
    ]);
    signal?.throwIfAborted();
    const [a,aaaa]=families;
    const ips=[];
    for(let i=0;i<Math.max(a.length,aaaa.length);i++)for(const rr of [a[i],aaaa[i]])if(rr?.address && publicAddress(rr.address))ips.push(rr.address);
    if(!ips.length)throw new Error('DNS returned no public target address');
    const record=https.sort((x,y)=>x.priority-y.priority).find(rr=>!policy.ech || rr.params.get(5)?.length);
    let ech=policy.ech ? record?.params.get(5) : undefined;
    if(policy.shared && !ech)ech=await this.sharedECH(signal);
    if(policy.ech && !ech?.length)throw new Error('Target requires its own ECHConfigList');
    if(record)ips.push(...addressHints(record));
    // Cloudflare ECH public-name frontends can carry the target's own ECH
    // config when a target anycast range is unreachable. Only addresses are
    // shared: the target still needs its own DNS ECH key and certificate.
    if(echPublicNames(ech).includes('cloudflare-ech.com') && this.cloudflareFrontend?.expires>Date.now())ips.push(...this.cloudflareFrontend.ips);
    return {ips:interleaveAddresses(ips),ech,h3:record ? alpnList(record.params.get(1)).includes('h3') : false};
  }
  invalidate(host) {
    const names=new Set([host,...(this.chains.get(host)||[]),'cloudflare-ech.com']);
    for(const [key,entry]of this.cache)if(names.has(entry.answer.host) && entry.answer.type===65)this.cache.delete(key);
    this.cloudflareFrontend=null;
  }
  close() {this.stopper.abort();this.cache.clear();this.chains.clear();this.cloudflareFrontend=null;}
}
module.exports={NativeResolver,POLICIES,publicAddress,wireQuery,parseDNS,serviceRecord,alpnList,addressHints,interleaveAddresses,isECHRejection,echPublicNames,waitFor};
