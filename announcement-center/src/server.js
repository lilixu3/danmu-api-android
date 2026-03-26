const path = require('path');
const crypto = require('crypto');
const express = require('express');
const session = require('express-session');
const MarkdownIt = require('markdown-it');

const { ACTION_ROUTE_OPTIONS } = require('./action-routes');
const { createPoolFromEnv, ensureSchema } = require('./db');
const { hashPassword, verifyPassword, requireAdmin } = require('./auth');
const {
  buildAnnouncementPayload,
  buildContentPreview,
  normalizeActionMode,
  normalizeAnnouncementType,
  normalizeBoolean,
  normalizePopupMode,
  normalizeSeverity,
  normalizeStatus,
  normalizeVariants,
} = require('./validation');

const PORT = Number(process.env.PORT || 18086);
const SESSION_SECRET = process.env.SESSION_SECRET || 'danmu-announcement-center';
const ADMIN_USERNAME = String(process.env.ADMIN_USERNAME || 'admin').trim();
const ADMIN_PASSWORD = String(process.env.ADMIN_PASSWORD || '').trim();

if (!process.env.MYSQL_DATABASE || !process.env.MYSQL_USER || !process.env.MYSQL_PASSWORD) {
  throw new Error('缺少 MySQL 环境变量，请检查 .env');
}
if (!ADMIN_PASSWORD) {
  throw new Error('缺少 ADMIN_PASSWORD，请检查 .env');
}

const app = express();
const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
});
const pool = createPoolFromEnv();

app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, '..', 'views'));

app.use(express.urlencoded({ extended: true }));
app.use(express.json());
app.use('/public', express.static(path.join(__dirname, '..', 'public')));
app.use(
  session({
    secret: SESSION_SECRET,
    resave: false,
    saveUninitialized: false,
    cookie: {
      httpOnly: true,
      sameSite: 'lax',
      maxAge: 7 * 24 * 60 * 60 * 1000,
    },
  })
);

app.use((req, res, next) => {
  res.locals.flash = req.session.flash || null;
  res.locals.currentUser = req.session.username || null;
  delete req.session.flash;
  next();
});

function setFlash(req, type, message) {
  req.session.flash = { type, message };
}

async function offlineOtherPublishedAnnouncements(currentId) {
  if (currentId == null) {
    await pool.query('UPDATE announcements SET status = ? WHERE status = ?', ['offline', 'published']);
    return;
  }
  await pool.query('UPDATE announcements SET status = ? WHERE status = ? AND id <> ?', [
    'offline',
    'published',
    currentId,
  ]);
}

async function ensureAdminAccount() {
  const [rows] = await pool.query('SELECT id, username FROM admins WHERE username = ? LIMIT 1', [
    ADMIN_USERNAME,
  ]);
  const passwordHash = await hashPassword(ADMIN_PASSWORD);
  if (rows.length === 0) {
    await pool.query(
      'INSERT INTO admins (username, password_hash) VALUES (?, ?)',
      [ADMIN_USERNAME, passwordHash]
    );
    return;
  }
  await pool.query('UPDATE admins SET password_hash = ? WHERE username = ?', [
    passwordHash,
    ADMIN_USERNAME,
  ]);
}

function renderEditor(res, payload) {
  res.render('editor', {
    actionRouteOptions: ACTION_ROUTE_OPTIONS,
    announcement: null,
    errorMessage: null,
    form: toEditorValues(),
    mode: 'create',
    previewHtml: '',
    ...payload,
  });
}

function toEditorValues(row = null) {
  const variants = safeJsonArray(row?.target_variants_json, ['all']);
  const primaryMode = normalizeActionMode(row?.primary_button_mode);
  const secondaryMode = normalizeActionMode(row?.secondary_button_mode);
  return {
    title: row?.title || '',
    summary: row?.summary || '',
    content_markdown: row?.content_markdown || '',
    cover_image_url: row?.cover_image_url || '',
    announcement_type: normalizeAnnouncementType(row?.announcement_type),
    severity: normalizeSeverity(row?.severity),
    status: normalizeStatus(row?.status),
    popup_mode: row && Boolean(row.force_popup) ? 'force' : 'normal',
    target_variants: variants.includes('all') ? 'all' : variants[0] || 'all',
    min_app_version: row?.min_app_version || '',
    max_app_version: row?.max_app_version || '',
    start_at: toDatetimeLocal(row?.start_at),
    end_at: toDatetimeLocal(row?.end_at),
    primary_button_text: row?.primary_button_text || '',
    primary_button_mode: primaryMode,
    primary_button_route: primaryMode === 'app_route' ? row?.primary_button_url || '' : '',
    primary_button_url: primaryMode === 'link' ? row?.primary_button_url || '' : '',
    secondary_button_text: row?.secondary_button_text || '',
    secondary_button_mode: secondaryMode,
    secondary_button_route: secondaryMode === 'app_route' ? row?.secondary_button_url || '' : '',
    secondary_button_url: secondaryMode === 'link' ? row?.secondary_button_url || '' : '',
  };
}

function toEditorFormFromBody(body = {}) {
  return {
    title: String(body.title || ''),
    summary: String(body.summary || ''),
    content_markdown: String(body.content_markdown || ''),
    cover_image_url: String(body.cover_image_url || ''),
    announcement_type: normalizeAnnouncementType(body.announcement_type),
    severity: normalizeSeverity(body.severity),
    status: normalizeStatus(body.status),
    popup_mode:
      body.popup_mode !== undefined
        ? normalizePopupMode(body.popup_mode)
        : normalizeBoolean(body.force_popup)
          ? 'force'
          : 'normal',
    target_variants: normalizeVariants(body.target_variants)[0] || 'all',
    min_app_version: String(body.min_app_version || ''),
    max_app_version: String(body.max_app_version || ''),
    start_at: String(body.start_at || ''),
    end_at: String(body.end_at || ''),
    primary_button_text: String(body.primary_button_text || ''),
    primary_button_mode: normalizeActionMode(body.primary_button_mode),
    primary_button_route: String(body.primary_button_route || ''),
    primary_button_url: String(body.primary_button_url || ''),
    secondary_button_text: String(body.secondary_button_text || ''),
    secondary_button_mode: normalizeActionMode(body.secondary_button_mode),
    secondary_button_route: String(body.secondary_button_route || ''),
    secondary_button_url: String(body.secondary_button_url || ''),
  };
}

function toDatetimeLocal(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const pad = (num) => String(num).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}`;
}

function safeJsonArray(raw, fallback = []) {
  try {
    const parsed = JSON.parse(raw || '[]');
    return Array.isArray(parsed) && parsed.length > 0 ? parsed : fallback;
  } catch (_error) {
    return fallback;
  }
}

function versionCompare(a, b) {
  const left = String(a || '')
    .split('.')
    .map((item) => Number.parseInt(item, 10) || 0);
  const right = String(b || '')
    .split('.')
    .map((item) => Number.parseInt(item, 10) || 0);
  const length = Math.max(left.length, right.length);
  for (let index = 0; index < length; index += 1) {
    const l = left[index] || 0;
    const r = right[index] || 0;
    if (l > r) return 1;
    if (l < r) return -1;
  }
  return 0;
}

function matchesVersion(row, version) {
  const current = String(version || '').trim();
  if (!current) return true;
  if (row.min_app_version && versionCompare(current, row.min_app_version) < 0) return false;
  if (row.max_app_version && versionCompare(current, row.max_app_version) > 0) return false;
  return true;
}

function matchesVariant(row, variant) {
  const normalizedVariant = String(variant || 'stable').trim().toLowerCase();
  const variants = safeJsonArray(row.target_variants_json, ['all']);
  return variants.includes('all') || variants.includes(normalizedVariant);
}

function deriveState(row) {
  if (row.status === 'draft') return '草稿';
  if (row.status === 'offline') return '已下线';
  const now = Date.now();
  if (row.start_at && new Date(row.start_at).getTime() > now) return '待生效';
  if (row.end_at && new Date(row.end_at).getTime() < now) return '已过期';
  return '已发布';
}

function severityLabel(severity) {
  return {
    info: '普通',
    success: '成功',
    warning: '提醒',
    danger: '重要',
  }[severity] || '普通';
}

function announcementTypeLabel(type) {
  return {
    short: '短期公告',
    long: '长期公告',
  }[normalizeAnnouncementType(type)] || '长期公告';
}

function variantLabels(rawVariants) {
  const labels = {
    all: '全部版本',
    stable: '正式版',
    dev: '开发版',
    custom: '自定义版',
  };
  return rawVariants.map((item) => labels[item] || item).join(' / ');
}

function actionModeLabel(mode) {
  return {
    none: '未启用',
    link: '自定义链接',
    app_route: 'App内跳转',
  }[normalizeActionMode(mode)] || '未启用';
}

function serializeAnnouncement(row) {
  const buildAction = (text, value, mode) => {
    if (!text || !value || normalizeActionMode(mode) === 'none') return null;
    return {
      text,
      url: value,
      type: normalizeActionMode(mode),
    };
  };

  return {
    id: `${row.announcement_key}:${Number(row.push_version) || 1}`,
    title: row.title,
    summary: row.summary,
    content_preview: row.content_preview,
    content_markdown: row.content_markdown,
    cover_image_url: row.cover_image_url,
    announcement_type: normalizeAnnouncementType(row.announcement_type),
    severity: row.severity,
    force_popup: Boolean(row.force_popup),
    allow_snooze_today: Boolean(row.allow_snooze_today),
    primary_action: buildAction(row.primary_button_text, row.primary_button_url, row.primary_button_mode),
    secondary_action: buildAction(
      row.secondary_button_text,
      row.secondary_button_url,
      row.secondary_button_mode
    ),
    published_at: row.published_at,
  };
}

app.get('/', (_req, res) => {
  res.redirect('/admin');
});

app.get('/health', (_req, res) => {
  res.json({ ok: true });
});

app.get('/admin/login', (req, res) => {
  if (req.session.adminId) {
    return res.redirect('/admin');
  }
  return res.render('login', {
    title: '公告后台登录',
  });
});

app.post('/admin/login', async (req, res) => {
  const username = String(req.body.username || '').trim();
  const password = String(req.body.password || '');
  const [rows] = await pool.query(
    'SELECT id, username, password_hash FROM admins WHERE username = ? LIMIT 1',
    [username]
  );
  const admin = rows[0];
  if (!admin) {
    setFlash(req, 'error', '账号或密码错误');
    return res.redirect('/admin/login');
  }
  const verified = await verifyPassword(password, admin.password_hash);
  if (!verified) {
    setFlash(req, 'error', '账号或密码错误');
    return res.redirect('/admin/login');
  }
  req.session.adminId = admin.id;
  req.session.username = admin.username;
  await pool.query('UPDATE admins SET last_login_at = NOW() WHERE id = ?', [admin.id]);
  return res.redirect('/admin');
});

app.post('/admin/logout', requireAdmin, (req, res) => {
  req.session.destroy(() => {
    res.redirect('/admin/login');
  });
});

app.get('/admin', requireAdmin, async (req, res) => {
  const filter = String(req.query.status || 'all').trim();
  const [rows] = await pool.query(`
    SELECT *
    FROM announcements
    ORDER BY
      CASE status
        WHEN 'published' THEN 0
        WHEN 'draft' THEN 1
        ELSE 2
      END,
      COALESCE(published_at, updated_at) DESC,
      id DESC
  `);
  const items = rows
    .map((row) => ({
      ...row,
      targetVariants: safeJsonArray(row.target_variants_json, ['all']),
      displayState: deriveState(row),
      announcementTypeLabel: announcementTypeLabel(row.announcement_type),
      pushVersion: Number(row.push_version) || 1,
      severityLabel: severityLabel(row.severity),
      targetVariantLabel: variantLabels(safeJsonArray(row.target_variants_json, ['all'])),
      primaryActionModeLabel: actionModeLabel(row.primary_button_mode),
      secondaryActionModeLabel: actionModeLabel(row.secondary_button_mode),
    }))
    .filter((row) => filter === 'all' || row.status === filter || row.displayState === filter);

  res.render('dashboard', {
    title: '公告中心',
    items,
    filter,
  });
});

app.get('/admin/announcements/new', requireAdmin, (req, res) => {
  renderEditor(res, {
    title: '新建公告',
    mode: 'create',
    form: toEditorValues(),
    announcement: null,
    previewHtml: '',
    errorMessage: null,
  });
});

app.get('/admin/announcements/:id/edit', requireAdmin, async (req, res) => {
  const [rows] = await pool.query('SELECT * FROM announcements WHERE id = ? LIMIT 1', [req.params.id]);
  const announcement = rows[0];
  if (!announcement) {
    setFlash(req, 'error', '公告不存在');
    return res.redirect('/admin');
  }
  renderEditor(res, {
    title: `编辑公告 #${announcement.id}`,
    mode: 'edit',
    form: toEditorValues(announcement),
    announcement,
    previewHtml: md.render(announcement.content_markdown || ''),
    errorMessage: null,
  });
});

app.post('/admin/api/preview-markdown', requireAdmin, (req, res) => {
  const content = String(req.body.content || '').trim();
  res.json({
    html: md.render(content),
    preview: buildContentPreview(content),
  });
});

app.post('/admin/announcements', requireAdmin, async (req, res) => {
  try {
    const payload = buildAnnouncementPayload(req.body);
    const announcementKey = crypto.randomUUID().slice(0, 16);
    const [result] = await pool.query(
      `
        INSERT INTO announcements (
          announcement_key, title, summary, content_markdown, content_preview, cover_image_url,
          announcement_type, push_version,
          severity, status, popup_enabled, force_popup, allow_snooze_today, target_variants_json,
          min_app_version, max_app_version, start_at, end_at,
          primary_button_text, primary_button_url, primary_button_mode,
          secondary_button_text, secondary_button_url, secondary_button_mode,
          published_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
          CASE WHEN ? = 'published' THEN NOW() ELSE NULL END
        )
      `,
      [
        announcementKey,
        payload.title,
        payload.summary,
        payload.content_markdown,
        payload.content_preview,
        payload.cover_image_url,
        payload.announcement_type,
        1,
        payload.severity,
        payload.status,
        payload.popup_enabled ? 1 : 0,
        payload.force_popup ? 1 : 0,
        payload.allow_snooze_today ? 1 : 0,
        payload.target_variants_json,
        payload.min_app_version,
        payload.max_app_version,
        payload.start_at,
        payload.end_at,
        payload.primary_button_text,
        payload.primary_button_url,
        payload.primary_button_mode,
        payload.secondary_button_text,
        payload.secondary_button_url,
        payload.secondary_button_mode,
        payload.status,
      ]
    );
    if (payload.status === 'published') {
      await offlineOtherPublishedAnnouncements(result.insertId);
    }
    setFlash(req, 'success', '公告已创建');
    res.redirect('/admin');
  } catch (error) {
    renderEditor(res.status(422), {
      title: '新建公告',
      mode: 'create',
      form: toEditorFormFromBody(req.body),
      announcement: null,
      previewHtml: md.render(String(req.body.content_markdown || '')),
      errorMessage: error.message || '保存失败',
    });
  }
});

app.post('/admin/announcements/:id', requireAdmin, async (req, res) => {
  try {
    const payload = buildAnnouncementPayload(req.body);
    await pool.query(
      `
        UPDATE announcements
        SET
          title = ?,
          summary = ?,
          content_markdown = ?,
          content_preview = ?,
          cover_image_url = ?,
          announcement_type = ?,
          severity = ?,
          status = ?,
          popup_enabled = ?,
          force_popup = ?,
          allow_snooze_today = ?,
          target_variants_json = ?,
          min_app_version = ?,
          max_app_version = ?,
          start_at = ?,
          end_at = ?,
          primary_button_text = ?,
          primary_button_url = ?,
          primary_button_mode = ?,
          secondary_button_text = ?,
          secondary_button_url = ?,
          secondary_button_mode = ?,
          published_at = CASE
            WHEN ? = 'published' AND published_at IS NULL THEN NOW()
            WHEN ? <> 'published' THEN NULL
            ELSE published_at
          END
        WHERE id = ?
      `,
      [
        payload.title,
        payload.summary,
        payload.content_markdown,
        payload.content_preview,
        payload.cover_image_url,
        payload.announcement_type,
        payload.severity,
        payload.status,
        payload.popup_enabled ? 1 : 0,
        payload.force_popup ? 1 : 0,
        payload.allow_snooze_today ? 1 : 0,
        payload.target_variants_json,
        payload.min_app_version,
        payload.max_app_version,
        payload.start_at,
        payload.end_at,
        payload.primary_button_text,
        payload.primary_button_url,
        payload.primary_button_mode,
        payload.secondary_button_text,
        payload.secondary_button_url,
        payload.secondary_button_mode,
        payload.status,
        payload.status,
        req.params.id,
      ]
    );
    if (payload.status === 'published') {
      await offlineOtherPublishedAnnouncements(Number(req.params.id));
    }
    setFlash(req, 'success', '公告已更新');
    res.redirect('/admin');
  } catch (error) {
    renderEditor(res.status(422), {
      title: `编辑公告 #${req.params.id}`,
      mode: 'edit',
      form: toEditorFormFromBody(req.body),
      announcement: { id: req.params.id },
      previewHtml: md.render(String(req.body.content_markdown || '')),
      errorMessage: error.message || '保存失败',
    });
  }
});

app.post('/admin/announcements/:id/delete', requireAdmin, async (req, res) => {
  await pool.query('DELETE FROM announcements WHERE id = ? LIMIT 1', [req.params.id]);
  setFlash(req, 'success', '公告已删除');
  res.redirect('/admin');
});

app.post('/admin/announcements/:id/repush', requireAdmin, async (req, res) => {
  const [rows] = await pool.query('SELECT id, status, push_version, title FROM announcements WHERE id = ? LIMIT 1', [
    req.params.id,
  ]);
  const announcement = rows[0];
  if (!announcement) {
    setFlash(req, 'error', '公告不存在');
    return res.redirect('/admin');
  }
  if (announcement.status !== 'published') {
    setFlash(req, 'error', '只有已发布公告才能再次推送');
    return res.redirect('/admin');
  }

  await pool.query(
    'UPDATE announcements SET push_version = COALESCE(push_version, 1) + 1 WHERE id = ?',
    [req.params.id]
  );
  setFlash(
    req,
    'success',
    `已再次推送《${announcement.title}》，当前推送版本 ${Number(announcement.push_version || 1) + 1}`
  );
  return res.redirect('/admin');
});

app.post('/admin/announcements/:id/publish', requireAdmin, async (req, res) => {
  await pool.query(
    'UPDATE announcements SET status = ?, published_at = COALESCE(published_at, NOW()) WHERE id = ?',
    ['published', req.params.id]
  );
  await offlineOtherPublishedAnnouncements(Number(req.params.id));
  setFlash(req, 'success', '公告已发布');
  res.redirect('/admin');
});

app.post('/admin/announcements/:id/offline', requireAdmin, async (req, res) => {
  await pool.query('UPDATE announcements SET status = ? WHERE id = ?', ['offline', req.params.id]);
  setFlash(req, 'success', '公告已下线');
  res.redirect('/admin');
});

app.get('/api/app/announcements/active', async (req, res) => {
  const version = String(req.query.version || '').trim();
  const variant = String(req.query.variant || 'stable').trim().toLowerCase();
  const [rows] = await pool.query(
    `
      SELECT *
      FROM announcements
      WHERE status = 'published'
        AND popup_enabled = 1
        AND (start_at IS NULL OR start_at <= NOW())
        AND (end_at IS NULL OR end_at >= NOW())
      ORDER BY
        force_popup DESC,
        GREATEST(
          COALESCE(updated_at, '1970-01-01 00:00:00'),
          COALESCE(published_at, '1970-01-01 00:00:00')
        ) DESC,
        id DESC
      LIMIT 50
    `
  );

  const active = rows.find((row) => matchesVariant(row, variant) && matchesVersion(row, version));
  if (!active) {
    return res.json({ announcement: null });
  }
  return res.json({
    announcement: serializeAnnouncement(active),
  });
});

async function main() {
  await ensureSchema(pool);
  await ensureAdminAccount();
  app.listen(PORT, '0.0.0.0', () => {
    console.log(`[announcement-center] listening on ${PORT}`);
  });
}

main().catch((error) => {
  console.error('[announcement-center] startup failed:', error);
  process.exit(1);
});
