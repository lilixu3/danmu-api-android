import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const _MAX_DETAIL_CHARS = 12000;

function _homeDir() {
  const raw = String(process.env.DANMU_API_HOME || '').trim();
  return raw || __dirname;
}

function _failureFile() {
  return path.join(_homeDir(), 'logs', 'startup-failure.json');
}

function _ensureDir(file) {
  try {
    fs.mkdirSync(path.dirname(file), { recursive: true });
  } catch {}
}

function _normalizeText(value) {
  return String(value ?? '').replace(/\u0000/g, '').trim();
}

function _buildSummary(error) {
  if (error == null) return '未知启动错误';
  if (typeof error === 'string') {
    return _normalizeText(error) || '未知启动错误';
  }

  const name = _normalizeText(error.name);
  const message = _normalizeText(error.message);
  if (name && message) return `${name}: ${message}`;
  if (message) return message;

  const firstStackLine = _normalizeText(String(error.stack || '').split(/\r?\n/)[0]);
  if (firstStackLine) return firstStackLine;

  return _normalizeText(error) || '未知启动错误';
}

function _buildDetail(error) {
  if (error == null) return '未知启动错误';
  const detail = typeof error === 'string'
    ? error
    : error.stack || error.message || String(error);
  return _normalizeText(detail).slice(0, _MAX_DETAIL_CHARS) || '未知启动错误';
}

export function clearStartupFailure() {
  const file = _failureFile();
  try {
    fs.unlinkSync(file);
  } catch {}
}

export function recordStartupFailure(error, options = {}) {
  const file = _failureFile();
  _ensureDir(file);

  const payload = {
    ts: Date.now(),
    stage: _normalizeText(options.stage || 'startup'),
    summary: _buildSummary(error),
    detail: _buildDetail(error),
    exitCode: Number.isFinite(options.exitCode) ? Number(options.exitCode) : null,
  };

  try {
    fs.writeFileSync(file, JSON.stringify(payload, null, 2), 'utf8');
  } catch {}

  return payload;
}
