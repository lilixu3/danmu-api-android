'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

function routeForAdmin(pathname, adminToken) {
  if (!adminToken) return null;
  const prefix = `/${encodeURIComponent(adminToken)}/api/env/`;
  if (!pathname.startsWith(prefix)) return null;
  const action = pathname.slice(prefix.length);
  return ['set', 'add', 'del'].includes(action) ? action : null;
}

function isManagementOriginAllowed(origin, requestHost) {
  if (!origin) return true; // Native clients do not send Origin.
  try {
    const supplied = new URL(origin);
    if (!['http:', 'https:'].includes(supplied.protocol) || supplied.username || supplied.password ||
        supplied.pathname !== '/' || supplied.search || supplied.hash) return false;
    // Compare the browser-facing Host using its scheme: TLS may terminate at a
    // reverse proxy before the request reaches this HTTP listener.
    const expected = new URL(`${supplied.protocol}//${requestHost}`);
    return !expected.username && !expected.password && supplied.origin === expected.origin;
  } catch { return false; }
}

function updateEnvContent(content, key, value, remove = false) {
  if (!/^[A-Z][A-Z0-9_]{0,127}$/.test(key)) throw new Error('Invalid configuration key');
  if (!remove && typeof value !== 'string') throw new Error('Configuration value must be a string');
  if (!remove && Buffer.byteLength(value, 'utf8') > 128 * 1024) throw new Error('Configuration value too large');
  const lines = content ? content.replace(/\r\n/g, '\n').split('\n') : [];
  const result = [];
  let found = false;
  for (const line of lines) {
    const match = line.match(/^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=/);
    if (match?.[1] === key) {
      if (!remove && !found) result.push(`${key}=${JSON.stringify(value)}`);
      found = true;
    } else result.push(line);
  }
  if (!remove && !found) result.push(`${key}=${JSON.stringify(value)}`);
  return result.join('\n').replace(/\n+$/, '') + '\n';
}

function saveEnvValue(envFile, key, value, remove) {
  const content = fs.existsSync(envFile) ? fs.readFileSync(envFile, 'utf8') : '';
  const updated = updateEnvContent(content, key, value, remove);
  fs.mkdirSync(path.dirname(envFile), { recursive: true });
  const temporary = `${envFile}.app-${crypto.randomBytes(8).toString('hex')}`;
  try {
    fs.writeFileSync(temporary, updated, { mode: 0o600, flag: 'wx' });
    fs.renameSync(temporary, envFile);
  } finally {
    try { fs.unlinkSync(temporary); } catch {}
  }
}

async function readManagementBody(req) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > 256 * 1024) throw Object.assign(new Error('Configuration request too large'), { statusCode: 413 });
    chunks.push(chunk);
  }
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

function injectWebSection(html, section) {
  if (!['env', 'logs'].includes(section) || typeof html !== 'string' || !/<\/body\s*>/i.test(html)) return html;
  // The core's frontend has no deep-link contract. Select its existing section after initialization.
  // No credentials or user strings are inserted into this script.
  const script = `<script data-danmu-app-section>(function(){let tries=0;const timer=setInterval(function(){if(++tries>100){clearInterval(timer);return;}if(typeof switchSection==='function' && typeof currentVersion!=='undefined' && currentVersion){clearInterval(timer);switchSection('${section}');}},100);})();</script>`;
  return html.replace(/<\/body\s*>/i, script + '</body>');
}

module.exports = { routeForAdmin, isManagementOriginAllowed, updateEnvContent, saveEnvValue, readManagementBody, injectWebSection };
