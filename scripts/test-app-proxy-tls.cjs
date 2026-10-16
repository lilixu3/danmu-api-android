#!/usr/bin/env node
'use strict';
// End-to-end encrypted proxy and target verification, with a test-only CA.
const fs=require('node:fs'),path=require('node:path'),os=require('node:os');
const {spawn,execFileSync}=require('node:child_process');
const {once}=require('node:events');
const assert=require('node:assert/strict');
async function main(){
 if(process.argv[2]!=='--child') {
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'app-proxy-tls-'));
  try {
   const config='[req]\nprompt=no\ndistinguished_name=dn\nx509_extensions=ext\n[dn]\nCN=source.test\n[ext]\nsubjectAltName=DNS:source.test,DNS:other.test,IP:127.0.0.1\nbasicConstraints=critical,CA:TRUE\nkeyUsage=critical,digitalSignature,keyCertSign\n';
   fs.writeFileSync(path.join(dir,'openssl.cnf'),config);
   execFileSync(process.env.ECH_OPENSSL_BIN || 'openssl',['req','-x509','-newkey','ec','-pkeyopt','ec_paramgen_curve:P-256','-noenc','-config',path.join(dir,'openssl.cnf'),'-out',path.join(dir,'cert.pem'),'-keyout',path.join(dir,'key.pem'),'-days','1'],{stdio:'pipe'});
   const child=spawn(process.execPath,[__filename,'--child',dir],{stdio:'inherit',env:{...process.env,NODE_EXTRA_CA_CERTS:path.join(dir,'cert.pem')}});
   const [code]=await once(child,'exit');assert.equal(code,0);
  }finally{fs.rmSync(dir,{recursive:true,force:true});}
  return;
 }
 const http=require('node:http'),https=require('node:https'),tls=require('node:tls'),net=require('node:net');
 const {installBridge}=require('../app/src/main/assets/nodejs-project/app-outbound-bridge.js');
 const {withSource}=require('../app/src/main/assets/nodejs-project/app-proxy-routing.js');
 const dir=process.argv[3],options={key:fs.readFileSync(path.join(dir,'key.pem')),cert:fs.readFileSync(path.join(dir,'cert.pem'))};
 const sockets=new Set(),seen=[];function track(socket){sockets.add(socket);socket.on('error',()=>{});socket.once('close',()=>sockets.delete(socket));}
 const origin=https.createServer(options,(req,res)=>{const chunks=[];req.on('data',x=>chunks.push(x));req.on('end',()=>{seen.push(req.headers);res.end('verified:'+Buffer.concat(chunks));});});
 origin.on('connection',track);origin.listen(0,'127.0.0.1');await once(origin,'listening');
 const plainSeen=[];const plain=http.createServer((req,res)=>{let body='';req.on('data',x=>body+=x);req.on('end',()=>{plainSeen.push(req.headers);res.end('forward:'+body);});});
 plain.on('connection',track);plain.listen(0,'127.0.0.1');await once(plain,'listening');
 const proxy=tls.createServer(options,socket=>{
  track(socket);let header='';
  const read=chunk=>{
   header+=chunk.toString();if(!header.includes('\r\n\r\n'))return;
   socket.removeListener('data',read);
   const [method,target]=header.split(' ');const forward=method!=='CONNECT';
   if(forward)assert.equal(new URL(target).hostname,'source.test');else assert.match(header,/CONNECT (source|wrong)\.test:443/);
   assert.match(header,/Proxy-Authorization: Basic dXNlcjpwYXNz/);
   const upstream=net.connect(forward?plain.address().port:origin.address().port,'127.0.0.1');track(upstream);
   upstream.on('connect',()=>{if(forward){const url=new URL(target);upstream.write(header.replace(target,url.pathname+url.search).replace(/^Proxy-Authorization:.*\r\n/im,''));}else socket.write('HTTP/1.1 200 OK\r\n\r\n');});
   socket.pipe(upstream);upstream.pipe(socket);socket.on('close',()=>upstream.destroy());upstream.on('close',()=>socket.destroy());
  };socket.on('data',read);
 });
 proxy.on('tlsClientError',()=>{});proxy.listen(0,'127.0.0.1');await once(proxy,'listening');
 const config={proxy:{enabled:true,rules:{aiyifan:'node:one'},nodes:[{id:'one',name:'tls',type:'https',host:'127.0.0.1',port:proxy.address().port,username:'user',password:'pass'}]}};
 const bridge=installBridge({snapshot:{config}});
 try {
  const response=await withSource('aiyifan',()=>fetch('https://source.test/verify',{method:'POST',body:'POST'}));
  assert.equal(await response.text(),'verified:POST');assert.equal(seen.length,1);assert.equal(seen[0]['proxy-authorization'],undefined);
  const forwarded=await withSource('aiyifan',()=>fetch('http://source.test/verify',{method:'POST',body:'HTTP-over-TLS'}));
  assert.equal(await forwarded.text(),'forward:HTTP-over-TLS');assert.equal(plainSeen.length,1);assert.equal(plainSeen[0]['proxy-authorization'],undefined);
  await assert.rejects(withSource('aiyifan',()=>fetch('https://wrong.test/verify')),/Hostname|altname|certificate/i);
  assert.equal(seen.length,1);
  config.proxy.nodes[0].tlsServerName='source.test';
  const named=await withSource('aiyifan',()=>fetch('https://source.test/verify'));await named.text();assert.equal(seen.length,2);
  config.proxy.nodes[0].tlsServerName='wrong.test';
  await assert.rejects(withSource('aiyifan',()=>fetch('https://source.test/verify')),/Hostname|altname|certificate/i);assert.equal(seen.length,2);
  config.proxy.nodes[0].skipCertVerify=true;
  await (await withSource('aiyifan',()=>fetch('https://source.test/verify'))).text();assert.equal(seen.length,3);
  await assert.rejects(withSource('aiyifan',()=>fetch('https://wrong.test/verify')),/Hostname|altname|certificate/i);assert.equal(seen.length,3);
  console.log('HTTPS_PROXY_AND_TARGET_VERIFIED authenticated=true httpAbsoluteForm=true explicitSni=true proxyInsecureDoesNotDisableTargetVerification=true requestCount=3 wrongHostnameRejected=true');
 }finally{bridge.stop();for(const socket of sockets)socket.destroy();origin.closeAllConnections();plain.closeAllConnections();await Promise.all([new Promise(r=>plain.close(r)),new Promise(r=>proxy.close(r)),new Promise(r=>origin.close(r))]);}
}
main().catch(error=>{console.error(error.stack);process.exitCode=1;});
