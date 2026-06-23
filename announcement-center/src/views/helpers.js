function escapeHtml(value) {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function raw(value) {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value);
}

function selected(condition) {
  return condition ? ' selected' : '';
}

function active(condition) {
  return condition ? ' active' : '';
}

function renderFlash(flash) {
  if (!flash) {
    return '';
  }
  return `<div class="flash ${escapeHtml(flash.type)}">${escapeHtml(flash.message)}</div>`;
}

function renderPage({ title, bodyClass = '', body }) {
  const classAttr = bodyClass ? ` class="${escapeHtml(bodyClass)}"` : '';
  return `<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>${escapeHtml(title)}</title>
    <link rel="stylesheet" href="/public/style.css" />
  </head>
  <body${classAttr}>
${body}
  </body>
</html>`;
}

module.exports = {
  active,
  escapeHtml,
  raw,
  renderFlash,
  renderPage,
  selected,
};
