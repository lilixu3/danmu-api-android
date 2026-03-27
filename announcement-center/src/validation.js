const { isAllowedActionRoute } = require('./action-routes');

function normalizeBoolean(value) {
  return value === true || value === 'true' || value === 'on' || value === '1';
}

function normalizeText(value, maxLength = null) {
  const text = String(value || '').trim();
  if (maxLength == null) return text;
  return text.slice(0, maxLength);
}

function normalizeOptionalUrl(value) {
  const text = normalizeText(value, 500);
  if (!text) return null;
  if (/^https?:\/\//i.test(text)) return text;
  return null;
}

function normalizeVariants(input) {
  const rawList = Array.isArray(input) ? input : [input];
  const aliases = {
    all: 'all',
    全部: 'all',
    全部版本: 'all',
    stable: 'stable',
    正式: 'stable',
    正式版: 'stable',
    dev: 'dev',
    开发: 'dev',
    开发版: 'dev',
    custom: 'custom',
    自定义: 'custom',
    自定义版: 'custom',
  };
  const result = [];
  for (const item of rawList) {
    const raw = normalizeText(item, 16);
    const normalized = aliases[raw] || aliases[raw.toLowerCase()];
    if (!normalized || result.includes(normalized)) continue;
    result.push(normalized);
  }
  if (result.length === 0) {
    return ['all'];
  }
  if (result.includes('all')) {
    return ['all'];
  }
  return result;
}

function normalizeSeverity(input) {
  const raw = normalizeText(input, 16);
  const value = raw.toLowerCase();
  return (
    {
      info: 'info',
      普通: 'info',
      success: 'success',
      成功: 'success',
      warning: 'warning',
      提醒: 'warning',
      danger: 'danger',
      important: 'danger',
      重要: 'danger',
    }[raw] ||
    {
      info: 'info',
      success: 'success',
      warning: 'warning',
      danger: 'danger',
      important: 'danger',
    }[value] ||
    'info'
  );
}

function normalizeStatus(input) {
  const raw = normalizeText(input, 16);
  const value = raw.toLowerCase();
  return (
    {
      draft: 'draft',
      草稿: 'draft',
      published: 'published',
      发布: 'published',
      立即发布: 'published',
      offline: 'offline',
      下线: 'offline',
      已下线: 'offline',
    }[raw] ||
    {
      draft: 'draft',
      published: 'published',
      offline: 'offline',
    }[value] ||
    'draft'
  );
}

function normalizeAnnouncementType(input) {
  const raw = normalizeText(input, 16);
  const value = raw.toLowerCase();
  return (
    {
      short: 'short',
      短期: 'short',
      短期公告: 'short',
      long: 'long',
      长期: 'long',
      长期公告: 'long',
    }[raw] ||
    {
      short: 'short',
      long: 'long',
    }[value] ||
    'long'
  );
}

function normalizePopupMode(input) {
  const raw = normalizeText(input, 16);
  const value = raw.toLowerCase();
  return (
    {
      normal: 'normal',
      普通: 'normal',
      普通弹窗: 'normal',
      force: 'force',
      强提醒: 'force',
      强提醒弹窗: 'force',
    }[raw] ||
    {
      normal: 'normal',
      force: 'force',
    }[value] ||
    'normal'
  );
}

function normalizeActionMode(input) {
  const raw = normalizeText(input, 16);
  const value = raw.toLowerCase();
  return (
    {
      none: 'none',
      不启用: 'none',
      disabled: 'none',
      app_route: 'app_route',
      app: 'app_route',
      app内跳转: 'app_route',
      站内跳转: 'app_route',
      link: 'link',
      custom_link: 'link',
      自定义链接: 'link',
      外部链接: 'link',
    }[raw] ||
    {
      none: 'none',
      disabled: 'none',
      app_route: 'app_route',
      app: 'app_route',
      link: 'link',
      custom_link: 'link',
    }[value] ||
    'none'
  );
}

function normalizeActionTarget(input) {
  return normalizeText(input, 120);
}

function buildActionPayload(body, prefix) {
  const text = normalizeText(body[`${prefix}_button_text`], 80);
  const mode = normalizeActionMode(body[`${prefix}_button_mode`]);
  const route = normalizeActionTarget(body[`${prefix}_button_route`]);
  const link = normalizeOptionalUrl(body[`${prefix}_button_url`]);

  if (mode === 'none') {
    if (text || route || link) {
      throw new Error(`${prefix === 'primary' ? '主按钮' : '次按钮'}已填写内容，请先选择按钮类型`);
    }
    return {
      text: null,
      value: null,
      mode: 'none',
    };
  }

  if (!text) {
    throw new Error(`${prefix === 'primary' ? '主按钮' : '次按钮'}文案不能为空`);
  }

  if (mode === 'app_route') {
    if (!isAllowedActionRoute(route)) {
      throw new Error(`${prefix === 'primary' ? '主按钮' : '次按钮'}的 App 页面无效`);
    }
    return {
      text,
      value: route,
      mode,
    };
  }

  if (!link) {
    throw new Error(`${prefix === 'primary' ? '主按钮' : '次按钮'}链接必须是有效的 http 或 https 地址`);
  }

  return {
    text,
    value: link,
    mode,
  };
}

function normalizeDateTimeLocal(input) {
  const value = normalizeText(input, 32);
  if (!value) return null;
  const iso = value.replace('T', ' ');
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return iso.length === 16 ? `${iso}:00` : iso;
}

function buildContentPreview(markdown) {
  return String(markdown || '')
    .replace(/\r\n?/g, '\n')
    .replace(/[`#>*_\[\]]/g, '')
    .replace(/\((https?:\/\/[^)]+)\)/g, '')
    .split('\n')
    .map((line) => line.replace(/[^\S\n]+/g, ' ').trim())
    .join('\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
    .slice(0, 380)
    .trim();
}

function buildAnnouncementPayload(body) {
  const title = normalizeText(body.title, 160);
  if (!title) {
    throw new Error('公告标题不能为空');
  }

  const contentMarkdown = String(body.content_markdown || '').trim();
  if (!contentMarkdown) {
    throw new Error('公告正文不能为空');
  }

  const announcementType = normalizeAnnouncementType(body.announcement_type);
  const startAt = normalizeDateTimeLocal(body.start_at);
  const endAt = normalizeDateTimeLocal(body.end_at);
  if (announcementType === 'short' && !endAt) {
    throw new Error('短期公告必须填写结束时间');
  }
  if (startAt && endAt && new Date(startAt) > new Date(endAt)) {
    throw new Error('结束时间不能早于开始时间');
  }

  const popupMode =
    body.popup_mode !== undefined
      ? normalizePopupMode(body.popup_mode)
      : normalizeBoolean(body.force_popup)
        ? 'force'
        : 'normal';
  const primaryAction = buildActionPayload(body, 'primary');
  const secondaryAction = buildActionPayload(body, 'secondary');

  return {
    title,
    summary: normalizeText(body.summary, 320),
    content_markdown: contentMarkdown,
    content_preview: buildContentPreview(contentMarkdown),
    cover_image_url: normalizeOptionalUrl(body.cover_image_url),
    announcement_type: announcementType,
    severity: normalizeSeverity(body.severity),
    status: normalizeStatus(body.status),
    popup_enabled: true,
    force_popup: popupMode === 'force',
    allow_snooze_today: announcementType === 'long',
    target_variants_json: JSON.stringify(normalizeVariants(body.target_variants)),
    min_app_version: normalizeText(body.min_app_version, 32) || null,
    max_app_version: normalizeText(body.max_app_version, 32) || null,
    start_at: startAt,
    end_at: endAt,
    primary_button_text: primaryAction.text,
    primary_button_url: primaryAction.value,
    primary_button_mode: primaryAction.mode,
    secondary_button_text: secondaryAction.text,
    secondary_button_url: secondaryAction.value,
    secondary_button_mode: secondaryAction.mode,
  };
}

module.exports = {
  buildActionPayload,
  buildAnnouncementPayload,
  buildContentPreview,
  normalizeActionMode,
  normalizeAnnouncementType,
  normalizeBoolean,
  normalizePopupMode,
  normalizeSeverity,
  normalizeStatus,
  normalizeVariants,
};
