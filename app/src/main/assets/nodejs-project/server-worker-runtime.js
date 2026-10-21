'use strict';

const path = require('path');

const READY_TIMEOUT_MS = 10000;
const SHUTDOWN_DELAY_MS = 200;
const SHUTDOWN_DEADLINE_MS = 10000;
const TERMINATE_TIMEOUT_MS = 350;
const REQUEST_TIMEOUT_MS = 60000;

function withTimeout(promise, timeoutMs, message) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(message || 'timeout')), timeoutMs);
    promise.then(
      value => {
        clearTimeout(timer);
        resolve(value);
      },
      error => {
        clearTimeout(timer);
        reject(error);
      }
    );
  });
}

function createServerWorkerRuntime(options) {
  const projectDir = String(options.projectDir || '');
  const getEnvSnapshot = options.getEnvSnapshot;
  const getOutboundSnapshot = options.getOutboundSnapshot || (() => undefined);
  const emitLog = options.emitLog;
  const log = options.log || (() => {});
  const isEnabled = options.isEnabled || (() => true);
  const WorkerFactory = options.WorkerFactory || null;
  const setTimer = options.setTimeout || setTimeout;
  const now = options.now || Date.now;

  let activeState = null;
  let requestSeq = 1;
  let workerSupport = null;
  let WorkerCtor = null;

  async function ensureWorkerSupport() {
    if (WorkerFactory) {
      WorkerCtor = WorkerFactory;
      workerSupport = true;
      return true;
    }
    if (workerSupport !== null) return workerSupport;
    try {
      const moduleRef = await import('node:worker_threads');
      WorkerCtor = moduleRef?.Worker || null;
      workerSupport = Boolean(WorkerCtor);
      return workerSupport;
    } catch {
      WorkerCtor = null;
      workerSupport = false;
      return false;
    }
  }

  function newWorkerState(worker, variantKey) {
    return {
      worker,
      variantKey,
      pending: new Map(),
      ready: false,
      readyPromise: null,
      readyResolve: null,
      readyReject: null,
      createdAt: now(),
    };
  }

  function onWorkerMessage(state, message) {
    if (!message || typeof message !== 'object') return;

    if (message.type === 'ready') {
      state.ready = true;
      if (state.readyResolve) {
        state.readyResolve();
        state.readyResolve = null;
        state.readyReject = null;
      }
      return;
    }

    if (message.type === 'log') {
      emitLog(message.level, message.message);
      return;
    }

    if (message.type !== 'response' && message.type !== 'error') return;
    const pending = state.pending.get(message.id);
    if (!pending) return;
    clearTimeout(pending.timer);
    state.pending.delete(message.id);
    if (message.type === 'error') pending.reject(new Error(message.error || 'worker error'));
    else pending.resolve(message);
  }

  function onWorkerExit(state, error) {
    if (state.readyResolve) {
      state.readyReject && state.readyReject(error || new Error('worker exit'));
      state.readyResolve = null;
      state.readyReject = null;
    }

    for (const pending of state.pending.values()) {
      clearTimeout(pending.timer);
      pending.reject(error || new Error('worker exit'));
    }
    state.pending.clear();

    if (activeState === state) activeState = null;
  }

  function postToWorker(state, message) {
    if (!state || !state.worker) return false;
    try {
      state.worker.postMessage(message);
      return true;
    } catch {
      return false;
    }
  }

  async function spawnWorker(variantKey, info) {
    const supported = await ensureWorkerSupport();
    if (!supported || !WorkerCtor) throw new Error('worker_threads unavailable');

    const worker = new WorkerCtor(path.join(projectDir, 'worker-proxy.js'), {
      workerData: {
        variantDir: info.dir,
        projectDir,
        env: getEnvSnapshot(),
        outbound: getOutboundSnapshot(),
      },
    });

    const state = newWorkerState(worker, variantKey);
    state.readyPromise = new Promise((resolve, reject) => {
      state.readyResolve = resolve;
      state.readyReject = reject;
    });

    worker.on('message', message => onWorkerMessage(state, message));
    worker.on('error', error => {
      log('Worker error:', error?.stack || error);
      onWorkerExit(state, error);
    });
    worker.on('exit', code => {
      if (code !== 0) log('Worker exit:', code);
      onWorkerExit(state, new Error(`worker exit ${code}`));
    });

    await withTimeout(state.readyPromise, READY_TIMEOUT_MS, 'worker ready timeout');
    state.ready = true;
    return state;
  }

  function scheduleWorkerShutdown(state) {
    const deadline = now() + SHUTDOWN_DEADLINE_MS;
    const tick = () => {
      if (!state || !state.worker) return;
      if (state.pending.size === 0 || now() > deadline) {
        try { state.worker.terminate(); } catch {}
        return;
      }
      setTimer(tick, SHUTDOWN_DELAY_MS);
    };
    setTimer(tick, SHUTDOWN_DELAY_MS);
  }

  async function ensureActiveWorkerForVariant(variantKey, info, forceReload = false) {
    if (!forceReload && activeState && activeState.variantKey === variantKey && activeState.ready) return activeState;
    const next = await spawnWorker(variantKey, info);
    const previous = activeState;
    activeState = next;
    if (previous) scheduleWorkerShutdown(previous);
    return next;
  }

  function shutdownWorker(state) {
    if (state) scheduleWorkerShutdown(state);
    if (activeState === state) activeState = null;
  }

  function syncEnvToWorker() {
    if (!activeState) return false;
    return postToWorker(activeState, {
      type: 'setEnv',
      env: getEnvSnapshot(),
      outbound: getOutboundSnapshot(),
    });
  }

  function sendWorkerRequest(state, payload, timeoutMs = REQUEST_TIMEOUT_MS, signal) {
    return new Promise((resolve, reject) => {
      if (!state || !state.worker || !state.ready) {
        reject(new Error('worker not ready'));
        return;
      }

      const id = requestSeq++;
      const cancel = () => {
        if (!state.pending.delete(id)) return;
        cleanup();
        if (payload.type === 'sourceProbe') postToWorker(state, { type: 'cancelSourceProbe', id });
        reject(signal?.reason || new Error('worker request cancelled'));
      };
      const cleanup = () => {
        clearTimeout(timer);
        signal?.removeEventListener('abort', cancel);
      };
      const timer = setTimeout(() => {
        state.pending.delete(id);
        cleanup();
        if (payload.type === 'sourceProbe') postToWorker(state, { type: 'cancelSourceProbe', id });
        reject(new Error('worker request timeout'));
      }, timeoutMs);

      state.pending.set(id, {
        resolve: value => { cleanup(); resolve(value); },
        reject: error => { cleanup(); reject(error); },
        timer,
      });
      signal?.addEventListener('abort', cancel, { once: true });
      if (signal?.aborted) {
        cancel();
        return;
      }
      try {
        state.worker.postMessage({ type: 'request', id, ...payload });
      } catch (error) {
        clearTimeout(timer);
        cleanup();
        state.pending.delete(id);
        reject(error);
      }
    });
  }

  async function handleRequestViaWorker(req, clientIp, variantKey = '', info = null) {
    let state = activeState;
    if ((!state || !state.ready || (variantKey && state.variantKey !== variantKey)) && variantKey && info) {
      state = await ensureActiveWorkerForVariant(variantKey, info, false);
    }
    if (!state || !state.ready) throw new Error('worker not ready');

    const headers = [];
    try {
      req.headers.forEach((value, key) => headers.push([key, value]));
    } catch {}

    let body = null;
    try {
      const buffer = await req.arrayBuffer();
      if (buffer && buffer.byteLength) body = Buffer.from(buffer);
    } catch {}

    const payload = {
      url: req.url,
      method: req.method,
      headers,
      body,
      clientIp: clientIp || '',
    };

    let responseMessage;
    try {
      responseMessage = await sendWorkerRequest(state, payload);
    } catch (error) {
      const message = String(error?.message || '');
      const retryable = Boolean(variantKey && info) && (
        message.includes('worker not ready') ||
        message.includes('worker exit') ||
        message.includes('worker request timeout')
      );
      if (!retryable) throw error;

      log('Worker request failed, retry with respawn:', message);
      const next = await ensureActiveWorkerForVariant(variantKey, info, true);
      if (!next || !next.ready) throw error;
      responseMessage = await sendWorkerRequest(next, payload);
    }

    const responseHeaders = new Headers(responseMessage.headers || []);
    if (Array.isArray(responseMessage.setCookie)) {
      for (const cookie of responseMessage.setCookie) {
        if (!cookie) continue;
        try { responseHeaders.append('set-cookie', cookie); } catch {}
      }
      if (typeof responseHeaders.getSetCookie !== 'function') {
        responseHeaders.getSetCookie = () => responseMessage.setCookie;
      }
    }

    const responseBody = responseMessage.body ? Buffer.from(responseMessage.body) : undefined;
    return new Response(responseBody, { status: responseMessage.status || 500, headers: responseHeaders });
  }

  async function tryLoadViaWorker(variantKey, info, forceReload = false) {
    if (!isEnabled()) return null;
    const supported = await ensureWorkerSupport();
    if (!supported || !WorkerCtor) return null;

    try {
      await ensureActiveWorkerForVariant(variantKey, info, forceReload);
    } catch (error) {
      log('Worker init failed, fallback to direct import:', error?.stack || error);
      if (activeState) {
        scheduleWorkerShutdown(activeState);
        activeState = null;
      }
      return null;
    }

    const handler = async (req, env, deployPlatform, clientIp) => {
      return handleRequestViaWorker(req, clientIp, variantKey, info);
    };
    return { handler, info, variantKey, viaWorker: true };
  }

  async function terminateWorker(state) {
    if (!state || !state.worker) return;
    const worker = state.worker;
    state.worker = null;
    state.pending.clear();
    try {
      await Promise.race([
        worker.terminate(),
        new Promise(resolve => setTimeout(resolve, TERMINATE_TIMEOUT_MS)),
      ]);
    } catch {}
  }

  function takeActiveState() {
    const state = activeState;
    activeState = null;
    return state;
  }

  return {
    ensureActiveWorkerForVariant,
    ensureWorkerSupport,
    get activeState() { return activeState; },
    handleRequestViaWorker,
    postToWorker,
    scheduleWorkerShutdown,
    sendWorkerRequest,
    shutdownWorker,
    syncEnvToWorker,
    takeActiveState,
    terminateWorker,
    tryLoadViaWorker,
  };
}

module.exports = {
  READY_TIMEOUT_MS,
  REQUEST_TIMEOUT_MS,
  SHUTDOWN_DEADLINE_MS,
  SHUTDOWN_DELAY_MS,
  TERMINATE_TIMEOUT_MS,
  createServerWorkerRuntime,
  withTimeout,
};
