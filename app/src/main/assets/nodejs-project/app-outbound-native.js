'use strict';
const http = require('node:http');
const http2 = require('node:http2');
const tls = require('node:tls');
const { isIP } = require('node:net');
const { timingSafeEqual, randomBytes } = require('node:crypto');
const { once } = require('node:events');
const { pipeline } = require('node:stream/promises');
const { NativeResolver, POLICIES, waitFor, isECHRejection } = require('./app-outbound-dns.js');

function nativeCapabilities() {
  let quic;
  try { quic = require('node:quic'); } catch {}
  return { ech:tls.ECH_SUPPORTED === true, quic };
}
function hopHeaders(headers) {
  const skip=new Set(['connection','proxy-connection','proxy-authorization','keep-alive','transfer-encoding','upgrade','te','trailer','host']);
  for(const value of String(headers.connection||'').split(','))skip.add(value.trim().toLowerCase());
  return skip;
}
function cleanHeaders(headers) {
  const skip=hopHeaders(headers), result=Object.create(null);
  for(const [name,value]of Object.entries(headers))if(!name.startsWith(':') && !name.toLowerCase().startsWith('x-danmu-outbound-') && !skip.has(name.toLowerCase()))result[name]=value;
  return result;
}
function pseudoHeaders(target,method,headers) {
  return {':method':method,':scheme':'https',':authority':target.host,':path':target.pathname+target.search,...cleanHeaders(headers)};
}
async function dialH2(host,ip,records,signal) {
  const socket=tls.connect({host:ip,port:443,servername:host,rejectUnauthorized:true,
    minVersion:records.ech?'TLSv1.3':'TLSv1.2',ALPNProtocols:['h2'],
    ...(records.ech?{echConfigList:records.ech}:{})});
  socket.on('error',()=>{});
  const abort=()=>socket.destroy(signal.reason);
  signal.addEventListener('abort',abort,{once:true});
  let client,draining=false;
  try {
    await once(socket,'secureConnect',{signal});
    if(socket.alpnProtocol!=='h2' || (records.ech && !socket.echAccepted))throw new Error('Required H2/ECH was not negotiated');
    // Dial cancellation belongs to the handshake. Detach it from the winning
    // socket before cancelling race losers; requests have their own signal.
    signal?.throwIfAborted();
    client=http2.connect(`https://${host}`,{createConnection:()=>socket});
    client.on('error',()=>{});
    client.on('goaway',()=>{draining=true;});
    await once(client,'connect',{signal});
    return {
      protocol:'h2',ech:socket.echAccepted===true,
      alive:()=>!draining && !client.closed && !client.destroyed && !socket.destroyed,
      close:()=>client.destroy(),
      async request(target,method,headers,body,requestSignal,downstream) {
        const stream=client.request(pseudoHeaders(target,method,headers),{endStream:!body.length,signal:requestSignal});
        stream.on('error',()=>{});
        const response=once(stream,'response',{signal:requestSignal});
        if(body.length)stream.end(body);
        const [received]=await response;
        downstream.writeHead(Number(received[':status']),cleanHeaders(received));
        await pipeline(stream,downstream,{signal:requestSignal});
      },
    };
  } catch(error) {client?.destroy();socket.destroy();throw error;}
  finally {signal.removeEventListener('abort',abort);}
}
async function dialH3(quic,host,ip,records,signal) {
  signal.throwIfAborted();
  // Node's default endpoint binds loopback. Outbound connections need a
  // wildcard bind of the same family as the resolved target address.
  const endpoint=new quic.QuicEndpoint({address:{address:isIP(ip)===6?'::':'0.0.0.0',port:0,family:isIP(ip)===6?'ipv6':'ipv4'}});
  let session,draining=false,endpointClosed=false;
  const closeEndpoint=()=>{if(endpointClosed)return;endpointClosed=true;endpoint.close().catch(()=>{});};
  const closeSession=error=>{try{session?.destroy(error);}finally{closeEndpoint();}};
  const abort=()=>closeSession(signal.reason);
  signal.addEventListener('abort',abort,{once:true});
  try {
    session=await quic.connect({address:ip,port:443,family:isIP(ip)===6?'ipv6':'ipv4'}, {
      endpoint,
      servername:host,alpn:'h3',verifyPeer:'strict',keepAlive:0,reuseEndpoint:false,
      enableEarlyData:false,handshakeTimeout:3000,
      transportParams:{maxIdleTimeout:60},
      ongoaway(){draining=true;},
      ...(records.ech?{echConfigList:records.ech}:{}),
    });
    session.closed.then(closeEndpoint,closeEndpoint);
    signal.throwIfAborted();
    const info=await waitFor(session.opened,signal);
    if(info.protocol!=='h3' || (records.ech && !info.echAccepted))throw new Error('Required H3/ECH was not negotiated');
    return {
      protocol:'h3',ech:info.echAccepted===true,
      alive:()=>!draining && !session.destroyed && !session.closing,
      close:()=>closeSession(),
      async request(target,method,headers,body,requestSignal,downstream) {
        let stream,finished=false; let resolveHeaders,rejectHeaders;
        const received=new Promise((resolve,reject)=>{resolveHeaders=resolve;rejectHeaders=reject;});
        received.catch(()=>{});
        const abortRequest=()=>{stream?.destroy(requestSignal.reason);rejectHeaders(requestSignal.reason);};
        requestSignal.addEventListener('abort',abortRequest,{once:true});
        try {
          requestSignal.throwIfAborted();
          const creating=session.createBidirectionalStream({
            headers:pseudoHeaders(target,method,headers),body:body.length?body:undefined,
            onheaders(headers) { if(Number(headers[':status'])>=200)resolveHeaders(headers); },
            onerror(error) {rejectHeaders(error);},
          }).then(created=>{
            stream=created;
            stream.closed.then(()=>rejectHeaders(new Error('H3 stream closed before headers')),rejectHeaders);
            if(finished || requestSignal.aborted)stream.destroy(requestSignal.reason);
          });
          await waitFor(creating,requestSignal);
          requestSignal.throwIfAborted();
          const response=await waitFor(received,requestSignal);
          downstream.writeHead(Number(response[':status']),cleanHeaders(response));
          for await(const batch of stream)for(const part of Array.isArray(batch)?batch:[batch]) {
            requestSignal.throwIfAborted();
            if(!downstream.write(Buffer.from(part)))await once(downstream,'drain',{signal:requestSignal});
          }
          requestSignal.throwIfAborted();downstream.end();
        } finally {finished=true;requestSignal.removeEventListener('abort',abortRequest);stream?.destroy();}
      },
    };
  } catch(error) {closeSession(error);throw error;}
  finally {signal.removeEventListener('abort',abort);}
}

class NativeTransport {
  constructor(config,{resolver=new NativeResolver(config),capabilities=nativeCapabilities(),dial}={}) {
    this.config=config;this.resolver=resolver;this.capabilities=capabilities;this.states=new Map();this.sessions=new Set();this.stopper=new AbortController();
    this.dial=dial||((host,ip,records,protocol,signal)=>protocol==='h3'?dialH3(capabilities.quic,host,ip,records,signal):dialH2(host,ip,records,signal));
  }
  async connect(host,records,state,signal) {
    const protocols=this.config.httpVersion==='h2' ? ['h2'] : this.config.httpVersion==='h3' ? ['h3'] : records.h3 && Date.now()>state.cooldown ? ['h3','h2'] : ['h2'];
    const attempts=[];const controllers=[];let winner;
    for(const [index,protocol]of protocols.entries())for(const [i,ip]of records.ips.slice(0,6).entries()) {
      const controller=new AbortController();controllers.push(controller);
      const attemptSignal=AbortSignal.any([signal,controller.signal]);
      attempts.push((async()=>{
        if(index || i)await waitFor(new Promise(resolve=>setTimeout(resolve,index*200+i*150)),attemptSignal);
        let session;
        try {
          session=await this.dial(host,ip,records,protocol,attemptSignal);
          if(winner || attemptSignal.aborted) {session.close();throw new Error('Handshake lost');}
          winner={controller,session};return session;
        } catch(error) {session?.close();throw error;}
      })());
    }
    try {
      const session=await Promise.any(attempts);
      if(session.protocol==='h2' && protocols.includes('h3')){state.failures++;if(state.failures>=2)state.cooldown=Date.now()+300000;}
      if(session.protocol==='h3'){state.failures=0;state.cooldown=0;}
      return session;
    } finally {controllers.forEach(controller=>{if(controller!==winner?.controller)controller.abort();});}
  }
  async acquire(host,signal) {
    let state=this.states.get(host);
    if(!state){state={session:null,pending:null,active:0,retired:false,timer:null,failures:0,cooldown:0};this.states.set(host,state);}
    clearTimeout(state.timer);
    if(!state.session?.alive()) {
      if(state.session) {
        state.session.retired=true;
        if(!state.session.active){state.session.close();this.sessions.delete(state.session);}
      }
      state.session=null;
      if(!state.pending) {
        // Shared connection setup is independent of any one request's abort.
        // DNS and TLS/QUIC each receive the configured connection budget,
        // matching the previous transport. A slow encrypted DNS lookup must
        // not leave only a few milliseconds for the source's handshake.
        const setupSignal=AbortSignal.any([this.stopper.signal,AbortSignal.timeout(Math.min(60000,this.config.connectTimeoutMs*4))]);
        const phaseSignal=()=>AbortSignal.any([setupSignal,AbortSignal.timeout(this.config.connectTimeoutMs)]);
        state.pending=(async()=>{
          for(let retry=0;retry<2;retry++) {
            const records=await this.resolver.resolve(host,phaseSignal());
            try {
              state.session=await this.connect(host,records,state,phaseSignal());
              state.session.active=0;state.session.retired=false;this.sessions.add(state.session);
              return state.session;
            }
            catch(error) {if(!records.ech || retry || setupSignal.aborted || !isECHRejection(error))throw error;this.resolver.invalidate(host);}
          }
        })().finally(()=>{state.pending=null;this.scheduleIdle(state);});
      }
      await waitFor(state.pending,signal);
    }
    signal.throwIfAborted();clearTimeout(state.timer);state.active++;
    const session=state.session;session.active++;
    let released=false;
    return {session,release:()=>{
      if(released)return;released=true;state.active--;session.active--;
      if(session.retired && !session.active){session.close();this.sessions.delete(session);}
      this.scheduleIdle(state);
    }};
  }
  scheduleIdle(state) {
    if(this.stopper.signal.aborted || state.active || state.pending)return;
    clearTimeout(state.timer);
    state.timer=setTimeout(()=>{state.session?.close();this.sessions.delete(state.session);state.session=null;},60000);state.timer.unref?.();
  }
  async request(target,method,headers,body,signal,downstream) {
    const {session,release}=await this.acquire(target.hostname,signal);
    // A failed business request is never replayed on another protocol.
    try {await session.request(target,method,headers,body,signal,downstream);}
    finally {release();}
  }
  close() {this.stopper.abort();this.resolver.close();for(const state of this.states.values())clearTimeout(state.timer);for(const session of this.sessions)session.close();this.sessions.clear();this.states.clear();}
}
async function startNativeOutbound(config,{transport,capabilities=nativeCapabilities()}={}) {
  if(!transport && (!capabilities.ech || !capabilities.quic))throw new Error('增强直连需要支持 ECH/H3 的 Node 26 + OpenSSL 4 运行时');
  transport??=new NativeTransport(config,{capabilities});
  const token=randomBytes(32).toString('hex'),expected=Buffer.from(token),controllers=new Set();
  const server=http.createServer(async(local,response)=>{
    const supplied=Buffer.from(String(local.headers['x-danmu-outbound-token']||''));
    const fail=(status,message)=>{if(response.headersSent){response.destroy();return;}response.writeHead(status,{'X-Danmu-Outbound-Error':'1'});response.end(message);};
    if(supplied.length!==expected.length || !timingSafeEqual(supplied,expected)){fail(401,'unauthorized');return;}
    let target;
    try {target=new URL(local.headers['x-danmu-outbound-target']);}catch{fail(400,'invalid target');return;}
    const policy=POLICIES.get(target.hostname);
    if(local.url!=='/proxy' || !policy || !config.sources.includes(policy.source) || target.protocol!=='https:' || target.port || target.username || target.password){fail(403,'target not allowed');return;}
    if(!['GET','HEAD','POST','PUT','PATCH','DELETE','OPTIONS'].includes(local.method)){fail(405,'method not allowed');return;}
    const budget=Number(local.headers['x-danmu-outbound-timeout']);
    if(!Number.isInteger(budget) || budget<1 || budget>3600000){fail(400,'invalid deadline');return;}
    const controller=new AbortController();controllers.add(controller);
    const timer=setTimeout(()=>controller.abort(new Error('Enhanced request timeout')),budget);timer.unref?.();
    const cancel=()=>{if(!response.writableFinished)controller.abort(new Error('Downstream closed'));};
    response.once('close',cancel);local.once('aborted',cancel);
    const abort=()=>{if(!local.complete)local.destroy();};controller.signal.addEventListener('abort',abort,{once:true});
    try {
      const chunks=[];let size=0;
      for await(const chunk of local){controller.signal.throwIfAborted();size+=chunk.length;if(size>32*1024*1024)throw new RangeError('Request body exceeds 32 MiB');chunks.push(chunk);}
      controller.signal.throwIfAborted();
      await transport.request(target,local.method,cleanHeaders(local.headers),Buffer.concat(chunks,size),controller.signal,response);
    } catch(error) {fail(error instanceof RangeError?413:502,'enhanced outbound request failed');}
    finally {clearTimeout(timer);controllers.delete(controller);response.removeListener('close',cancel);local.removeListener('aborted',cancel);}
  });
  server.headersTimeout=5000;server.requestTimeout=60000;server.maxHeadersCount=200;
  try {await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'127.0.0.1',resolve);});}
  catch(error){transport.close();throw error;}
  return {endpoint:`http://127.0.0.1:${server.address().port}`,token,backend:'node',
    async close(){for(const controller of controllers)controller.abort();transport.close();server.closeAllConnections();await new Promise(resolve=>server.close(resolve));},
  };
}
module.exports={startNativeOutbound,NativeTransport,nativeCapabilities,cleanHeaders,pseudoHeaders,dialH2,dialH3};
