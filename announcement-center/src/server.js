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
  isValidVersionText,
  normalizeActionMode,
  normalizeAnnouncementType,
  normalizeBoolean,
  normalizePopupMode,
  normalizeSeverity,
  normalizeStatus,
  normalizeVariants,
  versionCompare,
} = require('./validation');

const PORT = Number(process.env.PORT || 18086);
const SESSION_SECRET = String(process.env.SESSION_SECRET || '').trim();
const ADMIN_USERNAME = String(process.env.ADMIN_USERNAME || 'admin').trim();
const ADMIN_PASSWORD = String(process.env.ADMIN_PASSWORD || '').trim();
const MIN_SESSION_SECRET_LENGTH = 16;
const ANNOUNCEMENT_NOT_FOUND_CODE = 'ANNOUNCEMENT_NOT_FOUND';
const INVALID_PUBLISH_STATE_CODE = 'INVALID_PUBLISH_STATE';
const PUBLISH_LOCK_NAME = 'announcement-center:publish';
const PUBLISH_LOCK_TIMEOUT_SECONDS = 10;

if (!process.env.MYSQL_DATABASE || !process.env.MYSQL_USER || !process.env.MYSQL_PASSWORD) {
  throw new Error('缺少 MySQL 环境变量，请检查 .env');
}
if (!ADMIN_PASSWORD) {
  throw new Error('缺少 ADMIN_PASSWORD，请检查 .env');
}
if (
  !SESSION_SECRET ||
  SESSION_SECRET === 'danmu-announcement-center' ||
  SESSION_SECRET === 'change-me-session-secret' ||
  SESSION_SECRET.length < MIN_SESSION_SECRET_LENGTH
) {
  throw new Error('缺少安全的 SESSION_SECRET，请检查 .env，并使用至少 16 位随机字符串');
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

async function ensureAdminAccount() {
  const [rows] = await pool.query('SELECT id, username FROM admins WHERE username = ? LIMIT 1', [ADMIN_USERNAME]);
  if (rows.length === 0) {
    const passwordHash = await hashPassword(ADMIN_PASSWORD);
    await pool.query(
      'INSERT INTO admins (username, password_hash) VALUES (?, ?)',
      [ADMIN_USERNAME, passwordHash]
    );
  }
}

async function acquireNamedLock(connection, lockName, timeoutSeconds = PUBLISH_LOCK_TIMEOUT_SECONDS) {
  const [rows] = await connection.query('SELECT GET_LOCK(?, ?) AS acquired', [lockName, timeoutSeconds]);
  if (!rows[0] || rows[0].acquired !== 1) {
    throw new Error('当前有其他发布操作正在进行，请稍后重试');
  }
}

async function releaseNamedLock(connection, lockName) {
  try {
    await connection.query('SELECT RELEASE_LOCK(?)', [lockName]);
  } catch (_error) {
    // ignore release errors to preserve the original failure
  }
}

async function withTransaction(task, { lockName = null } = {}) {
  const connection = await pool.getConnection();
  let transactionStarted = false;
  try {
    if (lockName) {
      await acquireNamedLock(connection, lockName);
    }
    await connection.beginTransaction();
    transactionStarted = true;
    const result = await task(connection);
    await connection.commit();
    transactionStarted = false;
    return result;
  } catch (error) {
    if (transactionStarted) {
      await connection.rollback();
    }
    throw error;
  } finally {
    if (lockName) {
      await releaseNamedLock(connection, lockName);
    }
    connection.release();
  }
}

function createAnnouncementNotFoundError() {
  const error = new Error('公告不存在');
  error.code = ANNOUNCEMENT_NOT_FOUND_CODE;
  return error;
}

function isAnnouncementNotFoundError(error) {
  return error?.code === ANNOUNCEMENT_NOT_FOUND_CODE;
}

function createInvalidPublishStateError(message) {
  const error = new Error(message);
  error.code = INVALID_PUBLISH_STATE_CODE;
  return error;
}

function isInvalidPublishStateError(error) {
  return error?.code === INVALID_PUBLISH_STATE_CODE;
}

function normalizePushVersion(value) {
  const pushVersion = Number(value) || 1;
  return pushVersion >= 1 ? pushVersion : 1;
}

function nextPushVersionForPublish(status, pushVersion) {
  const currentPushVersion = normalizePushVersion(pushVersion);
  return String(status) === 'offline' ? currentPushVersion + 1 : currentPushVersion;
}

async function findAnnouncementById(executor, id, { lockForUpdate = false } = {}) {
  const sql = `SELECT * FROM announcements WHERE id = ? LIMIT 1${lockForUpdate ? ' FOR UPDATE' : ''}`;
  const [rows] = await executor.query(sql, [id]);
  return rows[0] || null;
}

async function replacePublishedAnnouncement(executor, keepId = null) {
  let sql = 'UPDATE announcements SET status = ? WHERE status = ?';
  const params = ['offline', 'published'];
  if (keepId != null) {
    sql += ' AND id <> ?';
    params.push(keepId);
  }
  await executor.query(sql, params);
}

async function regenerateSession(req) {
  await new Promise((resolve, reject) => {
    req.session.regenerate((error) => {
      if (error) {
        reject(error);
        return;
      }
      resolve();
    });
  });
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

function toDbDateTime(value) {
  if (!value) return null;
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  const pad = (num) => String(num).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function hasFutureStartAt(value) {
  const date = value instanceof Date ? value : new Date(value);
  return !Number.isNaN(date.getTime()) && date.getTime() > Date.now();
}

function assertPublishableNow(row) {
  if (hasFutureStartAt(row?.start_at)) {
    throw createInvalidPublishStateError('立即发布时，开始时间不能晚于当前时间');
  }
}

function normalizeStoredVariants(value) {
  return JSON.stringify(normalizeVariants(safeJsonArray(value, ['all'])));
}

function hasClientVisibleChanges(current, payload) {
  const currentComparable = {
    title: String(current.title || ''),
    summary: String(current.summary || ''),
    content_markdown: String(current.content_markdown || ''),
    content_preview: String(current.content_preview || ''),
    cover_image_url: current.cover_image_url || null,
    announcement_type: normalizeAnnouncementType(current.announcement_type),
    severity: normalizeSeverity(current.severity),
    popup_enabled: Boolean(current.popup_enabled),
    force_popup: Boolean(current.force_popup),
    allow_snooze_today: Boolean(current.allow_snooze_today),
    target_variants_json: normalizeStoredVariants(current.target_variants_json),
    min_app_version: current.min_app_version || null,
    max_app_version: current.max_app_version || null,
    start_at: toDbDateTime(current.start_at),
    end_at: toDbDateTime(current.end_at),
    primary_button_text: current.primary_button_text || null,
    primary_button_url: current.primary_button_url || null,
    primary_button_mode: normalizeActionMode(current.primary_button_mode),
    secondary_button_text: current.secondary_button_text || null,
    secondary_button_url: current.secondary_button_url || null,
    secondary_button_mode: normalizeActionMode(current.secondary_button_mode),
  };

  const nextComparable = {
    title: payload.title,
    summary: payload.summary,
    content_markdown: payload.content_markdown,
    content_preview: payload.content_preview,
    cover_image_url: payload.cover_image_url,
    announcement_type: normalizeAnnouncementType(payload.announcement_type),
    severity: normalizeSeverity(payload.severity),
    popup_enabled: Boolean(payload.popup_enabled),
    force_popup: Boolean(payload.force_popup),
    allow_snooze_today: Boolean(payload.allow_snooze_today),
    target_variants_json: JSON.stringify(normalizeVariants(JSON.parse(payload.target_variants_json || '[]'))),
    min_app_version: payload.min_app_version,
    max_app_version: payload.max_app_version,
    start_at: payload.start_at,
    end_at: payload.end_at,
    primary_button_text: payload.primary_button_text,
    primary_button_url: payload.primary_button_url,
    primary_button_mode: normalizeActionMode(payload.primary_button_mode),
    secondary_button_text: payload.secondary_button_text,
    secondary_button_url: payload.secondary_button_url,
    secondary_button_mode: normalizeActionMode(payload.secondary_button_mode),
  };

  return Object.keys(nextComparable).some((key) => currentComparable[key] !== nextComparable[key]);
}

function matchesVersion(row, version) {
  const current = String(version || '').trim();
  if (!current) return true;
  if (!isValidVersionText(current)) return false;
  if (row.min_app_version && !isValidVersionText(row.min_app_version)) return false;
  if (row.max_app_version && !isValidVersionText(row.max_app_version)) return false;
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
  const contentPreview = buildContentPreview(row.content_markdown || row.content_preview || '');

  return {
    id: `${row.announcement_key}:${Number(row.push_version) || 1}`,
    title: row.title,
    summary: row.summary,
    content_preview: contentPreview,
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
  await regenerateSession(req);
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
    await withTransaction(async (connection) => {
      if (payload.status === 'published') {
        await replacePublishedAnnouncement(connection);
      }
      await connection.query(
        `
          INSERT INTO announcements (
            announcement_key, title, summary, content_markdown, content_preview, cover_image_url,
            announcement_type, push_version,
            severity, status, popup_enabled, force_popup, allow_snooze_today, target_variants_json,
            min_app_version, max_app_version, start_at, end_at,
            primary_button_text, primary_button_url, primary_button_mode,
            secondary_button_text, secondary_button_url, secondary_button_mode,
            published_at
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
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
    }, payload.status === 'published' ? { lockName: PUBLISH_LOCK_NAME } : undefined);
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
    await withTransaction(async (connection) => {
      const current = await findAnnouncementById(connection, req.params.id, { lockForUpdate: true });
      if (!current) {
        throw createAnnouncementNotFoundError();
      }
      if (payload.status === 'published') {
        await replacePublishedAnnouncement(connection, current.id);
      }
      let nextPushVersion = normalizePushVersion(current.push_version);
      if (payload.status === 'published') {
        if (current.status === 'offline') {
          nextPushVersion = nextPushVersionForPublish(current.status, current.push_version);
        } else if (current.status === 'published' && hasClientVisibleChanges(current, payload)) {
          nextPushVersion += 1;
        }
      }
      const [result] = await connection.query(
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
            push_version = ?,
            published_at = CASE
              WHEN ? = 'published' AND ? <> 'published' THEN NOW()
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
          nextPushVersion,
          payload.status,
          current.status,
          payload.status,
          current.id,
        ]
      );
      if (result.affectedRows === 0) {
        throw createAnnouncementNotFoundError();
      }
    }, payload.status === 'published' ? { lockName: PUBLISH_LOCK_NAME } : undefined);
    setFlash(req, 'success', '公告已更新');
    res.redirect('/admin');
  } catch (error) {
    if (isAnnouncementNotFoundError(error)) {
      setFlash(req, 'error', error.message);
      return res.redirect('/admin');
    }
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
  const [result] = await pool.query('DELETE FROM announcements WHERE id = ? LIMIT 1', [req.params.id]);
  if (result.affectedRows === 0) {
    setFlash(req, 'error', '公告不存在');
    return res.redirect('/admin');
  }
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
  try {
    const result = await withTransaction(async (connection) => {
      const announcement = await findAnnouncementById(connection, req.params.id, { lockForUpdate: true });
      if (!announcement) {
        throw createAnnouncementNotFoundError();
      }
      assertPublishableNow(announcement);
      await replacePublishedAnnouncement(connection, announcement.id);
      const nextPushVersion = nextPushVersionForPublish(announcement.status, announcement.push_version);
      const [updateResult] = await connection.query(
        `
          UPDATE announcements
          SET status = ?, push_version = ?,
              published_at = CASE WHEN ? <> 'published' THEN NOW() ELSE published_at END
          WHERE id = ?
        `,
        ['published', nextPushVersion, announcement.status, announcement.id]
      );
      if (updateResult.affectedRows === 0) {
        throw createAnnouncementNotFoundError();
      }
      return {
        previousStatus: announcement.status,
        pushVersion: nextPushVersion,
      };
    }, { lockName: PUBLISH_LOCK_NAME });
    setFlash(
      req,
      'success',
      result.previousStatus === 'offline'
        ? `公告已重新发布，当前推送版本 ${result.pushVersion}`
        : '公告已发布'
    );
    res.redirect('/admin');
  } catch (error) {
    if (isAnnouncementNotFoundError(error) || isInvalidPublishStateError(error)) {
      setFlash(req, 'error', error.message);
      return res.redirect('/admin');
    }
    throw error;
  }
});

app.post('/admin/announcements/:id/offline', requireAdmin, async (req, res) => {
  const [result] = await pool.query('UPDATE announcements SET status = ? WHERE id = ?', ['offline', req.params.id]);
  if (result.affectedRows === 0) {
    setFlash(req, 'error', '公告不存在');
    return res.redirect('/admin');
  }
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
        GREATEST(
          COALESCE(updated_at, '1970-01-01 00:00:00'),
          COALESCE(published_at, '1970-01-01 00:00:00')
        ) DESC,
        id DESC
      LIMIT 50
    `
  );

  const activeAnnouncements = rows
    .filter((row) => matchesVariant(row, variant) && matchesVersion(row, version))
    .map((row) => serializeAnnouncement(row));

  return res.json({
    announcement: activeAnnouncements[0] || null,
    announcements: activeAnnouncements,
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
