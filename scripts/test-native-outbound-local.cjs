#!/usr/bin/env node
'use strict';
// Offline integration with an actual OpenSSL 4 ECH server and Node H3 server.
// ECH_OPENSSL_BIN=/path/to/openssl node --experimental-quic scripts/test-native-outbound-local.cjs
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const os=require('node:os');
const net=require('node:net');
const tls=require('node:tls');
const quic=require('node:quic');
const {createPrivateKey}=require('node:crypto');
const {spawn,execFileSync}=require('node:child_process');
const {once}=require('node:events');
const {NativeTransport,startNativeOutbound,dialH3}=require('../app/src/main/assets/nodejs-project/app-outbound-native.js');
const {waitFor}=require('../app/src/main/assets/nodejs-project/app-outbound-dns.js');

async function main() {
  assert.equal(tls.ECH_SUPPORTED,true);
  const executable=process.env.ECH_OPENSSL_BIN;
  assert(executable && path.isAbsolute(executable),'Set ECH_OPENSSL_BIN to an OpenSSL 4 executable');
  const directory=fs.mkdtempSync(path.join(os.tmpdir(),'node-ech-h3-local-'));
  const file=name=>path.join(directory,name);
  let child,endpoint,backend;
  const serverSessions=[],clientSessions=[];
  try {
    fs.writeFileSync(file('openssl.cnf'),'[req]\ndistinguished_name=dn\nx509_extensions=ext\nprompt=no\n[dn]\nCN=api.tmdb.org\n[ext]\nsubjectAltName=DNS:api.tmdb.org,DNS:cover.test\nbasicConstraints=critical,CA:TRUE\nkeyUsage=critical,digitalSignature,keyCertSign\n');
    const run=args=>execFileSync(executable,args,{cwd:directory,stdio:['ignore','pipe','pipe']});
    run(['req','-x509','-newkey','ec','-pkeyopt','ec_paramgen_curve:P-256','-noenc','-config','openssl.cnf','-keyout','key.pem','-out','cert.pem','-days','1']);
    run(['ech','-public_name','cover.test','-out','ech.pem']);
    const cert=fs.readFileSync(file('cert.pem'));
    const key=fs.readFileSync(file('key.pem'));
    const pem=fs.readFileSync(file('ech.pem'),'utf8');
    const ech=Buffer.from(pem.match(/-----BEGIN ECHCONFIG-----\s*([\s\S]*?)\s*-----END ECHCONFIG-----/)[1].replace(/\s/g,''),'base64');
    const reserved=net.createServer();reserved.listen(0,'127.0.0.1');await once(reserved,'listening');
    const port=reserved.address().port;await new Promise(resolve=>reserved.close(resolve));
    child=spawn(executable,['s_server','-accept',`127.0.0.1:${port}`,'-cert',file('cert.pem'),'-key',file('key.pem'),'-ech_key',file('ech.pem'),'-www'],{stdio:['ignore','pipe','pipe']});
    await new Promise((resolve,reject)=>{
      const timer=setTimeout(()=>reject(new Error('OpenSSL server startup timeout')),5000);
      let output='';child.stdout.on('data',part=>{output+=part;if(output.includes('ACCEPT')){clearTimeout(timer);resolve();}});
      child.once('exit',()=>{clearTimeout(timer);reject(new Error('OpenSSL server exited'));});
      child.once('error',reject);child.stderr.resume();
    });
    const socket=tls.connect({host:'127.0.0.1',port,servername:'api.tmdb.org',ca:cert,echConfigList:ech});
    socket.on('error',()=>{});
    await once(socket,'secureConnect',{signal:AbortSignal.timeout(5000)});
    assert.equal(socket.authorized,true);assert.equal(socket.echAccepted,true);assert.equal(socket.getECHStatus(),1);
    socket.end('GET / HTTP/1.0\r\n\r\n');socket.resume();await once(socket,'close');
    console.log('TLS_ECH_ACCEPTED certificate=verified status=1');

    endpoint=await quic.listen(session=>{serverSessions.push(session);session.closed.catch(()=>{});session.onstream=stream=>stream.closed.catch(()=>{});},{
      sni:{'*':{keys:[createPrivateKey(key)],certs:[cert]}},
      async onheaders(headers) {
        assert.equal(headers[':method'],'POST');assert.equal(headers[':authority'],'api.tmdb.org');
        const chunks=[];for await(const batch of this)for(const part of Array.isArray(batch)?batch:[batch])chunks.push(Buffer.from(part));
        this.sendHeaders({':status':'201','content-type':'text/plain','set-cookie':['a=1','b=2']});
        this.writer.writeSync(Buffer.from('h3:'+Buffer.concat(chunks).toString()));this.writer.endSync();
      },
    });
    const adapter={QuicEndpoint:quic.QuicEndpoint,async connect(address,options){
      assert.equal(options.verifyPeer,'strict');assert.equal(options.alpn,'h3');
      const session=await quic.connect({...address,port:endpoint.address.port},{...options,ca:cert});
      clientSessions.push(session);return session;
    }};
    const config={enabled:true,sources:['tmdb'],httpVersion:'h3',connectTimeoutMs:5000};
    const resolver={async resolve(){return {ips:['127.0.0.1'],h3:true};},close(){}};
    const transport=new NativeTransport(config,{resolver,capabilities:{ech:true,quic:adapter},dial:(host,ip,records,protocol,signal)=>dialH3(adapter,host,ip,records,signal)});
    backend=await startNativeOutbound(config,{transport});
    const response=await fetch(backend.endpoint+'/proxy',{method:'POST',body:'sent once',signal:AbortSignal.timeout(8000),headers:{
      'X-Danmu-Outbound-Token':backend.token,'X-Danmu-Outbound-Target':'https://api.tmdb.org/echo','X-Danmu-Outbound-Timeout':'8000',
    }});
    assert.equal(response.status,201);assert.equal(await response.text(),'h3:sent once');assert.equal(response.headers.getSetCookie().length,2);
    assert.equal(transport.states.get('api.tmdb.org').session.protocol,'h3');
    console.log('HTTP3_POST_STREAM_OK certificate=verified cookies=2 requests=1');
    for(let round=0;round<5;round++) {
      const connection=round===0?transport.states.get('api.tmdb.org').session:
        await dialH3(adapter,'api.tmdb.org','127.0.0.1',{},AbortSignal.timeout(5000));
      const session=clientSessions.at(-1),clientEndpoint=session.endpoint;
      await waitFor(Promise.all([serverSessions.at(-1).close(),session.closed,clientEndpoint.closed]),AbortSignal.timeout(5000));
      assert.equal(connection.alive(),false);assert.equal(session.endpoint,null);assert.equal(clientEndpoint.destroyed,true);
      connection.close();connection.close();
    }
    console.log('HTTP3_REMOTE_CLOSE_ENDPOINT_RELEASED rounds=5');
  } finally {
    await backend?.close();for(const session of clientSessions)session.destroy();for(const session of serverSessions)session.destroy();await endpoint?.close();
    if(child && child.exitCode===null){child.kill();await once(child,'exit');}
    fs.rmSync(directory,{recursive:true,force:true});
  }
}
main().catch(error=>{console.error(error.stack||error);process.exitCode=1;});
