'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const helperModule = require('../app/src/main/assets/nodejs-project/app-proxy-helper.js');

function fixture(options = {}) {
  const servers = [];
  const closed = [];
  const logs = [];
  let listening = true;
  let shuttingDown = false;
  let proxyUrl = 'http://user:pass@proxy.test:8080';
  const httpModule = {
    createServer(handler) {
      const server = {
        handler,
        listening: false,
        listenCalls: [],
        listen(listenOptions, callback) {
          this.listenCalls.push(listenOptions);
          this.listening = true;
          setImmediate(callback);
        },
        once(event, callback) { this.errorHandler = callback; },
      };
      servers.push(server);
      return server;
    },
  };
  const helper = helperModule.createProxyHelper({
    http: httpModule,
    readClientIp: () => '127.0.0.1',
    stripTokenPrefix: value => value,
    isAdminAuthorized: () => options.authorized !== false,
    accessControl: {
      evaluate: () => ({ allowed: options.allowed !== false, clientIp: '127.0.0.1', reason: 'ok' }),
      recordDeviceAccess: () => {},
      config: () => ({ mode: 'off' }),
    },
    readRequestBody: async () => Buffer.from('body'),
    coreProxyConfig: value => ({ forward: value ? proxyUrl : '' }),
    requestThroughProxy: (...args) => options.proxyRequest(...args),
    getEnv: () => ({ PROXY_URL: proxyUrl }),
    getListenOptions: port => ({ port, host: '127.0.0.1' }),
    getListenUrl: (port, suffix) => `http://127.0.0.1:${port}${suffix}`,
    getHost: () => '127.0.0.1',
    getPort: () => 5321,
    isMainServerListening: () => listening,
    isShuttingDown: () => shuttingDown,
    closeServer: async server => { closed.push(server); server.listening = false; },
    log: (...args) => logs.push(args.join(' ')),
  });
  return {
    helper,
    servers,
    closed,
    logs,
    setListening: value => { listening = value; },
    setShuttingDown: value => { shuttingDown = value; },
    setProxyUrl: value => { proxyUrl = value; },
  };
}

function mockResponse() {
  return {
    statusCode: 0,
    headers: {},
    body: '',
    writeHead(statusCode, headers) { this.statusCode = statusCode; this.headers = headers || {}; },
    write(chunk) { this.body += chunk.toString(); },
    end(chunk) { if (chunk) this.body += chunk.toString(); },
    setHeader() {},
  };
}

test('proxy address parsing decodes credentials and defaults ports', () => {
  assert.deepEqual(helperModule.parseForwardProxy('http://u%40ser:p%3Aass@proxy.test'), {
    type: 'http',
    host: 'proxy.test',
    port: 80,
    username: 'u@ser',
    password: 'p:ass',
  });
  assert.equal(helperModule.parseForwardProxy('https://proxy.test').port, 443);
});

test('sync opens one listener, closes it when demand disappears, and respects shutdown', async () => {
  const { helper, servers, closed, setListening, setShuttingDown, setProxyUrl } = fixture();
  await helper.sync();
  await helper.sync();
  assert.equal(servers.length, 1);
  assert.deepEqual(servers[0].listenCalls, [{ port: 5321, host: '127.0.0.1' }]);

  setProxyUrl('');
  await helper.sync();
  assert.equal(helper.server, null);
  assert.deepEqual(closed, [servers[0]]);

  setProxyUrl('http://proxy.test:8080');
  setListening(false);
  await helper.sync();
  assert.equal(servers.length, 1);

  setListening(true);
  setShuttingDown(true);
  await helper.sync();
  assert.equal(servers.length, 1);
});

test('proxy requests keep access checks, authorization and upstream request options', async () => {
  let requestOptions;
  let proxyRequest;
  const outgoing = new EventEmitter();
  Object.assign(outgoing, {
    write(value) { this.body = value.toString(); },
    end() { this.ended = true; },
  });
  const { helper } = fixture({
    proxyRequest: (target, options, node, onResponse) => {
      requestOptions = options;
      proxyRequest = outgoing;
      const response = new EventEmitter();
      response.statusCode = 201;
      response.headers = { 'x-upstream': 'yes' };
      setImmediate(() => {
        onResponse(response);
        response.emit('data', Buffer.from('forwarded'));
        response.emit('end');
      });
      return outgoing;
    },
  });
  const server = helper.createServer();
  const response = mockResponse();
  const finished = new Promise(resolve => {
    const originalEnd = response.end;
    response.end = function (chunk) {
      originalEnd.call(this, chunk);
      resolve();
    };
  });

  await server.handler({
    url: '/proxy?url=' + encodeURIComponent('https://target.test/path?q=1'),
    method: 'POST',
    headers: { host: '127.0.0.1:5321', 'user-agent': 'fixture' },
  }, response);
  await finished;

  assert.equal(response.statusCode, 201);
  assert.equal(response.body, 'forwarded');
  assert.equal(requestOptions.hostname, 'target.test');
  assert.equal(requestOptions.path, '/path?q=1');
  assert.equal(requestOptions.headers.host, 'target.test');
  assert.equal(proxyRequest.body, 'body');
  assert.equal(proxyRequest.ended, true);
});

test('proxy requests reject missing targets and forbidden callers', async () => {
  const { helper } = fixture();
  const server = helper.createServer();

  const missing = mockResponse();
  await server.handler({ url: '/proxy', method: 'GET', headers: {} }, missing);
  assert.equal(missing.statusCode, 400);

  const deniedFixture = fixture({ authorized: false });
  const denied = mockResponse();
  await deniedFixture.helper.createServer().handler({
    url: '/proxy?url=' + encodeURIComponent('https://target.test'),
    method: 'GET',
    headers: {},
  }, denied);
  assert.equal(denied.statusCode, 403);
});

