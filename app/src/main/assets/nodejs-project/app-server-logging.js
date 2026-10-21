'use strict';

const fs = require('fs');
const path = require('path');

const DEFAULT_LOG_MAX_BYTES = 1 * 1024 * 1024;
const LOG_MAX_BACKUPS = 2;
const LOG_STREAM_CLOSE_TIMEOUT_MS = 120;
const LOG_MAX_BYTES_MIN = 64 * 1024;
const LOG_MAX_BYTES_MAX = 200 * 1024 * 1024;

function readBoolEnv(env, key, def = false) {
  const raw = env[key];
  if (raw === undefined || raw === null) return def;
  const value = String(raw).trim().toLowerCase();
  if (!value) return def;
  return value === '1' || value === 'true' || value === 'yes' || value === 'on';
}

function readIntEnv(env, key, def, min, max) {
  const raw = env[key];
  if (raw === undefined || raw === null) return def;
  const number = Number(raw);
  if (!Number.isFinite(number)) return def;
  const value = Math.trunc(number);
  if (min !== undefined && value < min) return min;
  if (max !== undefined && value > max) return max;
  return value;
}

function formatArg(value) {
  if (value === null) return 'null';
  if (value === undefined) return 'undefined';
  if (value instanceof Error) return value.stack || value.message || String(value);
  const type = typeof value;
  if (type === 'string') return value;
  if (type === 'bigint') return value.toString();
  if (type === 'object') {
    try {
      return JSON.stringify(value);
    } catch {
      try { return String(value); } catch { return '[object]'; }
    }
  }
  try {
    return String(value);
  } catch {
    return '[unprintable]';
  }
}

function timestamp() {
  return new Date().toISOString().replace(/\.\d{3}Z$/, 'Z');
}

function createServerLogging(options = {}) {
  const env = options.env || process.env;
  const fileSystem = options.fs || fs;
  const consoleRef = options.console || console;
  const processRef = options.process || process;
  const home = String(options.home || env.DANMU_API_HOME || __dirname);
  const logDir = path.join(home, 'logs');
  const logFile = path.join(logDir, 'danmuapi.log');

  let stream = null;
  let originalConsole = null;
  let logLevelValue = 2;
  let fileLogEnabled = false;
  let logMaxBytes = DEFAULT_LOG_MAX_BYTES;
  let logCurrentBytes = 0;
  let processListenersRegistered = false;

  function refreshLogLevel() {
    const raw = String(env.LOG_LEVEL || 'info').trim().toLowerCase();
    if (raw === 'error') logLevelValue = 0;
    else if (raw === 'warn' || raw === 'warning') logLevelValue = 1;
    else logLevelValue = 2;
  }

  function shouldWrite(level) {
    const value = level === 'error' ? 0 : level === 'warn' ? 1 : 2;
    return value <= logLevelValue;
  }

  function closeStream() {
    try { stream && stream.end(); } catch {}
    try { stream && stream.destroy(); } catch {}
    stream = null;
    logCurrentBytes = 0;
  }

  function rotateLogsOnce() {
    try {
      closeStream();

      for (let index = LOG_MAX_BACKUPS; index >= 1; index--) {
        const source = `${logFile}.${index}`;
        const destination = `${logFile}.${index + 1}`;
        if (!fileSystem.existsSync(source)) continue;
        if (index === LOG_MAX_BACKUPS) {
          try { fileSystem.rmSync(source, { force: true }); } catch {}
        } else {
          try { fileSystem.renameSync(source, destination); } catch {}
        }
      }
      if (fileSystem.existsSync(logFile)) {
        try { fileSystem.renameSync(logFile, `${logFile}.1`); } catch {}
      }
    } catch {}
  }

  function ensureWritableLogFile() {
    try {
      if (!fileSystem.existsSync(logFile)) return;
    } catch {
      return;
    }

    try {
      const stats = fileSystem.statSync(logFile);
      if (!stats.isFile()) {
        try { fileSystem.renameSync(logFile, `${logFile}.bad.${Date.now()}`); } catch {}
        return;
      }
    } catch {}

    try {
      fileSystem.accessSync(logFile, fileSystem.constants.W_OK);
      return;
    } catch {}

    const suffix = new Date().toISOString().replace(/[:.]/g, '').replace('Z', '');
    try {
      fileSystem.renameSync(logFile, `${logFile}.readonly.${suffix}`);
      return;
    } catch {}

    try { fileSystem.unlinkSync(logFile); } catch {}
  }

  function ensureLogStream() {
    if (!fileLogEnabled || stream) return;
    try {
      try { fileSystem.mkdirSync(logDir, { recursive: true }); } catch {}
      ensureWritableLogFile();

      try {
        logCurrentBytes = fileSystem.statSync(logFile)?.size || 0;
      } catch {
        logCurrentBytes = 0;
      }

      if (logCurrentBytes >= logMaxBytes) {
        rotateLogsOnce();
        logCurrentBytes = 0;
      }

      const nextStream = fileSystem.createWriteStream(logFile, { flags: 'a' });
      nextStream.on('error', () => {
        try { nextStream.destroy(); } catch {}
        if (stream === nextStream) {
          stream = null;
          logCurrentBytes = 0;
        }
      });
      stream = nextStream;
    } catch {
      stream = null;
    }
  }

  function writeLogLine(level, args) {
    if (!shouldWrite(level) || !fileLogEnabled) return;

    try {
      ensureLogStream();
      if (!stream) return;

      const line = `${timestamp()} [${level.toUpperCase()}] ${args.map(formatArg).join(' ')}\n`;
      const lineBytes = Buffer.byteLength(line, 'utf8');

      if (logCurrentBytes > 0 && (logCurrentBytes + lineBytes) > logMaxBytes) {
        rotateLogsOnce();
        ensureLogStream();
        if (!stream) return;
      }

      stream.write(line);
      logCurrentBytes += lineBytes;
    } catch {}
  }

  function refreshLogConfig() {
    const enabled = readBoolEnv(env, 'DANMU_API_LOG_TO_FILE', false);
    const nextMaxBytes = readIntEnv(
      env,
      'DANMU_API_LOG_MAX_BYTES',
      DEFAULT_LOG_MAX_BYTES,
      LOG_MAX_BYTES_MIN,
      LOG_MAX_BYTES_MAX
    );

    const enabledChanged = enabled !== fileLogEnabled;
    const maxChanged = nextMaxBytes !== logMaxBytes;
    fileLogEnabled = enabled;
    logMaxBytes = nextMaxBytes;

    if (!fileLogEnabled) {
      closeStream();
      return;
    }

    try { fileSystem.mkdirSync(logDir, { recursive: true }); } catch {}

    if (stream && (enabledChanged || maxChanged)) {
      try {
        logCurrentBytes = fileSystem.statSync(logFile)?.size || logCurrentBytes || 0;
      } catch {}
      if (logCurrentBytes >= logMaxBytes) {
        rotateLogsOnce();
      }
    }
  }

  function takeLogStream() {
    const current = stream;
    stream = null;
    logCurrentBytes = 0;
    return current;
  }

  function closeStreamAsync(target) {
    if (!target) return Promise.resolve();
    return new Promise((resolve) => {
      let settled = false;
      const finish = () => {
        if (settled) return;
        settled = true;
        resolve();
      };
      const fallback = setTimeout(finish, LOG_STREAM_CLOSE_TIMEOUT_MS);
      try {
        target.end(() => {
          clearTimeout(fallback);
          finish();
        });
      } catch {
        clearTimeout(fallback);
        finish();
      }
    });
  }

  function emitWorkerLog(level, line) {
    const text = String(line || '').replace(/\r$/, '');
    if (!text) return;
    const normalizedLevel = level === 'error' ? 'error' : level === 'warn' ? 'warn' : 'info';

    if (originalConsole) {
      const write = normalizedLevel === 'error'
        ? (originalConsole.error || originalConsole.log)
        : normalizedLevel === 'warn'
          ? (originalConsole.warn || originalConsole.log)
          : (originalConsole.log || consoleRef.log);
      try { write(text); } catch {}
      writeLogLine(normalizedLevel, [text]);
      return;
    }

    if (normalizedLevel === 'error') consoleRef.error(text);
    else if (normalizedLevel === 'warn') consoleRef.warn(text);
    else consoleRef.log(text);
  }

  function setupFileLogging() {
    refreshLogLevel();
    refreshLogConfig();

    if (!originalConsole) {
      const baseLog = typeof consoleRef.log === 'function' ? consoleRef.log : () => {};
      originalConsole = {
        log: baseLog.bind(consoleRef),
        info: (typeof consoleRef.info === 'function' ? consoleRef.info : baseLog).bind(consoleRef),
        warn: (typeof consoleRef.warn === 'function' ? consoleRef.warn : baseLog).bind(consoleRef),
        error: (typeof consoleRef.error === 'function' ? consoleRef.error : baseLog).bind(consoleRef),
      };

      consoleRef.log = (...args) => { originalConsole.log(...args); writeLogLine('info', args); };
      consoleRef.info = (...args) => { originalConsole.info(...args); writeLogLine('info', args); };
      consoleRef.warn = (...args) => { originalConsole.warn(...args); writeLogLine('warn', args); };
      consoleRef.error = (...args) => { originalConsole.error(...args); writeLogLine('error', args); };
    }

    if (!processListenersRegistered) {
      processListenersRegistered = true;
      processRef.on('unhandledRejection', (reason) => {
        consoleRef.error('[unhandledRejection]', reason && (reason.stack || reason));
      });
      processRef.on('uncaughtException', (error) => {
        consoleRef.error('[uncaughtException]', error && (error.stack || error));
      });
      processRef.on('exit', () => {
        closeStream();
      });
    }

    writeLogLine('info', ['[danmu_api]', `log file: ${logFile}`, `LOG_LEVEL=${env.LOG_LEVEL || 'info'}`]);
  }

  return {
    logDir,
    logFile,
    refreshLogLevel,
    refreshLogConfig,
    writeLogLine,
    emitWorkerLog,
    takeLogStream,
    closeStreamAsync,
    setupFileLogging,
  };
}

module.exports = {
  createServerLogging,
  DEFAULT_LOG_MAX_BYTES,
  LOG_MAX_BACKUPS,
  LOG_STREAM_CLOSE_TIMEOUT_MS,
};
