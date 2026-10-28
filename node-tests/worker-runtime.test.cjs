'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const runtimeModule = require('../app/src/main/assets/nodejs-project/server-worker-runtime.js');

class FakeWorker extends EventEmitter {
  constructor(script, options) {
    super();
    this.script = script;
    this.options = options;
    this.messages = [];
    this.terminated = false;
  }

  postMessage(message) {
    this.messages.push(message);
    if (message.type === 'request') {
      setImmediate(() => this.emit('message', {
        type: 'response',
        id: message.id,
        status: 200,
        headers: [['content-type', 'application/json'], ['set-cookie', 'a=1'], ['set-cookie', 'b=2']],
        setCookie: ['a=1', 'b=2'],
        body: Buffer.from(JSON.stringify({ url: message.url, method: message.method })),
      }));
    }
  }

  async terminate() {
    this.terminated = true;
    return 0;
  }
}

function fixture(options = {}) {
  const workers = [];
  const logs = [];
  class Worker extends FakeWorker {
    constructor(script, workerOptions) {
      super(script, workerOptions);
      workers.push(this);
      setImmediate(() => this.emit('message', { type: 'ready' }));
    }
  }
  const runtime = runtimeModule.createServerWorkerRuntime({
    projectDir: '/project',
    WorkerFactory: Worker,
    getEnvSnapshot: () => ({ TOKEN: 'fixture' }),
    getOutboundSnapshot: () => ({ enabled: false }),
    emitLog: (level, line) => logs.push([level, line]),
    log: (...args) => logs.push(['host', ...args]),
    isEnabled: () => options.enabled !== false,
    ...options,
  });
  return { runtime, workers, logs, Worker };
}

function request(pathname = '/api/v2/search/anime', method = 'GET') {
  return {
    url: pathname,
    method,
    headers: new Headers([['x-test', '1']]),
    async arrayBuffer() { return Buffer.from('body'); },
  };
}

test('worker requests preserve headers, cookies, body and worker data', async () => {
  const { runtime, workers } = fixture();
  const info = { dir: 'danmu_api_stable', label: 'stable' };
  const response = await runtime.handleRequestViaWorker(request(), '127.0.0.1', 'stable', info);
  const body = await response.json();

  assert.equal(response.status, 200);
  assert.equal(body.url, '/api/v2/search/anime');
  assert.equal(body.method, 'GET');
  assert.deepEqual(response.headers.getSetCookie().slice(0, 2), ['a=1', 'b=2']);
  assert.equal(workers.length, 1);
  assert.equal(workers[0].options.workerData.variantDir, 'danmu_api_stable');
  assert.equal(workers[0].options.workerData.projectDir, '/project');
  assert.deepEqual(workers[0].options.workerData.env, { TOKEN: 'fixture' });
  assert.deepEqual(workers[0].options.workerData.outbound, { enabled: false });
  assert.equal(workers[0].messages.length, 1);
  assert.equal(workers[0].messages[0].type, 'request');
  assert.equal(workers[0].messages[0].clientIp, '127.0.0.1');
  assert.equal(Buffer.from(workers[0].messages[0].body).toString(), 'body');
});

test('worker exit clears active state and the next request respawns once', async () => {
  const { runtime, workers } = fixture();
  const info = { dir: 'danmu_api_stable', label: 'stable' };
  await runtime.ensureActiveWorkerForVariant('stable', info);
  const worker = workers[0];
  worker.emit('exit', 1);
  assert.equal(runtime.activeState, null);

  const response = await runtime.handleRequestViaWorker(request(), '127.0.0.1', 'stable', info);
  assert.equal(response.status, 200);
  assert.equal(workers.length, 2);
});

test('retryable worker failures respawn the worker exactly once', async () => {
  const { runtime, workers } = fixture();
  const info = { dir: 'danmu_api_stable', label: 'stable' };
  await runtime.ensureActiveWorkerForVariant('stable', info);
  const first = workers[0];
  first.postMessage = message => {
    first.messages.push(message);
    setImmediate(() => first.emit('message', { type: 'error', id: message.id, error: 'worker exit 1' }));
  };

  const response = await runtime.handleRequestViaWorker(request(), '127.0.0.1', 'stable', info);
  assert.equal(response.status, 200);
  assert.equal(workers.length, 2);
});

test('disabled workers fall back to direct import without spawning', async () => {
  const { runtime, workers } = fixture({ enabled: false });
  const loaded = await runtime.tryLoadViaWorker('stable', { dir: 'danmu_api_stable' });
  assert.equal(loaded, null);
  assert.equal(workers.length, 0);
});

test('environment sync reaches only the active worker', async () => {
  const { runtime, workers } = fixture();
  await runtime.ensureActiveWorkerForVariant('stable', { dir: 'danmu_api_stable' });
  const first = workers[0];
  await runtime.ensureActiveWorkerForVariant('dev', { dir: 'danmu_api_dev' }, true);
  const second = workers[1];
  assert.equal(runtime.syncEnvToWorker(), true);
  assert.equal(second.messages.at(-1).type, 'setEnv');
  assert.equal(first.messages.some(message => message.type === 'setEnv'), false);
});

test('source probe cancellation emits the worker cancel message', async () => {
  const { runtime, workers } = fixture();
  await runtime.ensureActiveWorkerForVariant('stable', { dir: 'danmu_api_stable' });
  const worker = workers[0];
  worker.postMessage = message => {
    worker.messages.push(message);
    if (message.type === 'request') {
      setImmediate(() => worker.emit('message', { type: 'response', id: message.id, result: { connected: true } }));
    }
  };
  const controller = new AbortController();
  const pending = runtime.sendWorkerRequest(runtime.activeState, { type: 'sourceProbe', source: 'douban' }, 20000, controller.signal);
  controller.abort();
  await assert.rejects(pending, /aborted/);
  assert.equal(worker.messages.at(-1).type, 'cancelSourceProbe');
});

test('request timeout rejects and source probes send cancellation', async () => {
  const { runtime, workers } = fixture();
  await runtime.ensureActiveWorkerForVariant('stable', { dir: 'danmu_api_stable' });
  const worker = workers[0];
  worker.postMessage = message => worker.messages.push(message);
  await assert.rejects(
    runtime.sendWorkerRequest(runtime.activeState, { type: 'sourceProbe', source: 'douban' }, 10),
    /timeout/
  );
  assert.equal(worker.messages.at(-1).type, 'cancelSourceProbe');
});

test('worker shutdown waits for pending work then terminates', async () => {
  const { runtime, workers } = fixture();
  await runtime.ensureActiveWorkerForVariant('stable', { dir: 'danmu_api_stable' });
  const worker = workers[0];
  const state = runtime.activeState;
  runtime.shutdownWorker(state);
  assert.equal(runtime.activeState, null);
  await new Promise(resolve => setTimeout(resolve, 250));
  assert.equal(worker.terminated, true);
});
