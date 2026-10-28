'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { EventEmitter } = require('node:events');
const loggingModule = require('../app/src/main/assets/nodejs-project/app-server-logging.js');

function fixture(env = {}) {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), 'danmu-server-logging-'));
  const output = [];
  const consoleRef = {
    log: (...args) => output.push(['log', args]),
    info: (...args) => output.push(['info', args]),
    warn: (...args) => output.push(['warn', args]),
    error: (...args) => output.push(['error', args]),
  };
  const processRef = new EventEmitter();
  const logging = loggingModule.createServerLogging({ home, env, fs, console: consoleRef, process: processRef });
  return { home, output, consoleRef, processRef, logging };
}

async function flush(logging) {
  const stream = logging.takeLogStream();
  await logging.closeStreamAsync(stream);
}

async function waitForFile(file) {
  for (let attempt = 0; attempt < 100; attempt++) {
    if (fs.existsSync(file) && fs.statSync(file).size > 0) return;
    await new Promise(resolve => setTimeout(resolve, 5));
  }
  throw new Error(`Timed out waiting for ${file}`);
}

test('file logging stays disabled by default and creates no log file', async () => {
  const { home, consoleRef, logging } = fixture();
  try {
    logging.setupFileLogging();
    consoleRef.log('not persisted');
    await flush(logging);
    assert.equal(fs.existsSync(logging.logFile), false);
    assert.equal(fs.existsSync(logging.logDir), false);
  } finally {
    fs.rmSync(home, { recursive: true, force: true });
  }
});

test('LOG_LEVEL filters file output without suppressing console output', async () => {
  const { home, output, consoleRef, logging } = fixture({
    LOG_LEVEL: 'warn',
    DANMU_API_LOG_TO_FILE: '1',
  });
  try {
    logging.setupFileLogging();
    consoleRef.log('debug-line');
    consoleRef.info('info-line');
    consoleRef.warn('warn-line');
    consoleRef.error('error-line');
    await flush(logging);
    const content = fs.readFileSync(logging.logFile, 'utf8');
    assert.doesNotMatch(content, /debug-line|info-line/);
    assert.match(content, /\[WARN\] warn-line/);
    assert.match(content, /\[ERROR\] error-line/);
    assert.deepEqual(output.map(entry => entry[1][0]), ['debug-line', 'info-line', 'warn-line', 'error-line']);
  } finally {
    fs.rmSync(home, { recursive: true, force: true });
  }
});

test('console capture is idempotent and worker logs are written exactly once', async () => {
  const { home, output, consoleRef, logging } = fixture({ DANMU_API_LOG_TO_FILE: '1' });
  try {
    logging.setupFileLogging();
    logging.setupFileLogging();
    consoleRef.log('console-line');
    logging.emitWorkerLog('error', 'worker-line');
    await flush(logging);
    assert.deepEqual(output.map(entry => entry[1][0]), ['console-line', 'worker-line']);
    const content = fs.readFileSync(logging.logFile, 'utf8');
    assert.equal(content.match(/console-line/g)?.length, 1);
    assert.equal(content.match(/worker-line/g)?.length, 1);
    assert.equal(content.match(/log file:/g)?.length, 2);
  } finally {
    fs.rmSync(home, { recursive: true, force: true });
  }
});

test('rotation keeps two backups and rotates before the next oversized line', async () => {
  const { home, logging } = fixture({
    DANMU_API_LOG_TO_FILE: '1',
    DANMU_API_LOG_MAX_BYTES: String(64 * 1024),
  });
  try {
    logging.setupFileLogging();
    for (const marker of ['first', 'second', 'third', 'fourth']) {
      logging.writeLogLine('error', [`${marker}-${marker[0].repeat(70 * 1024)}`]);
      await waitForFile(logging.logFile);
    }
    const stream = logging.takeLogStream();
    await logging.closeStreamAsync(stream);

    assert.equal(fs.existsSync(`${logging.logFile}.1`), true);
    assert.equal(fs.existsSync(`${logging.logFile}.2`), true);
    assert.equal(fs.existsSync(`${logging.logFile}.3`), false);
    assert.match(fs.readFileSync(`${logging.logFile}.2`, 'utf8'), /second-/);
    assert.match(fs.readFileSync(`${logging.logFile}.1`, 'utf8'), /third-/);
    assert.match(fs.readFileSync(logging.logFile, 'utf8'), /fourth-/);
  } finally {
    fs.rmSync(home, { recursive: true, force: true });
  }
});

test('non-file log path is renamed and logging recovers with a regular file', async () => {
  const { home, logging } = fixture({ DANMU_API_LOG_TO_FILE: '1' });
  try {
    fs.mkdirSync(logging.logFile, { recursive: true });
    logging.setupFileLogging();
    logging.writeLogLine('info', ['recovered-line']);
    await flush(logging);
    assert.equal(fs.statSync(logging.logFile).isFile(), true);
    assert.match(fs.readFileSync(logging.logFile, 'utf8'), /recovered-line/);
    assert.equal(fs.readdirSync(logging.logDir).some(name => name.startsWith('danmuapi.log.bad.')), true);
  } finally {
    fs.rmSync(home, { recursive: true, force: true });
  }
});

test('taking and closing the log stream is idempotent', async () => {
  const { home, logging } = fixture({ DANMU_API_LOG_TO_FILE: '1' });
  try {
    logging.setupFileLogging();
    logging.writeLogLine('info', ['close-line']);
    const stream = logging.takeLogStream();
    assert.ok(stream);
    assert.equal(logging.takeLogStream(), null);
    await logging.closeStreamAsync(stream);
    await logging.closeStreamAsync(null);
    assert.equal(logging.takeLogStream(), null);
  } finally {
    fs.rmSync(home, { recursive: true, force: true });
  }
});
