const { active, escapeHtml, renderFlash, renderPage } = require('./helpers');

function formatDateTime(value, fallback) {
  if (!value) {
    return fallback;
  }
  if (typeof value.toISOString === 'function') {
    return value.toISOString().slice(0, 16).replace('T', ' ');
  }
  const date = new Date(value);
  if (!Number.isNaN(date.getTime())) {
    return date.toISOString().slice(0, 16).replace('T', ' ');
  }
  return String(value);
}

function renderAnnouncementCard(item) {
  const itemId = escapeHtml(item.id);
  const summary = item.summary || item.content_preview;
  return `            <article class="announcement-card">
              <div class="card-topline">
                <span class="badge severity-${escapeHtml(item.severity)}">${escapeHtml(item.severityLabel)}</span>
                <span class="badge state">${escapeHtml(item.displayState)}</span>
              </div>
              <h3>${escapeHtml(item.title)}</h3>
              <p class="muted text-clamp-3">${escapeHtml(summary)}</p>

              <dl class="meta-grid">
                <div>
                  <dt>公告类型</dt>
                  <dd>${escapeHtml(item.announcementTypeLabel)}</dd>
                </div>
                <div>
                  <dt>目标变体</dt>
                  <dd>${escapeHtml(item.targetVariantLabel)}</dd>
                </div>
                <div>
                  <dt>推送版本</dt>
                  <dd>第 ${escapeHtml(item.pushVersion)} 次</dd>
                </div>
                <div>
                  <dt>版本范围</dt>
                  <dd>${escapeHtml(item.min_app_version || '*')} ~ ${escapeHtml(item.max_app_version || '*')}</dd>
                </div>
                <div>
                  <dt>生效时间</dt>
                  <dd>${escapeHtml(formatDateTime(item.start_at, '立即'))}</dd>
                </div>
                <div>
                  <dt>失效时间</dt>
                  <dd>${escapeHtml(formatDateTime(item.end_at, '不限'))}</dd>
                </div>
              </dl>

              <div class="card-actions">
                <a href="/admin/announcements/${itemId}/edit" class="btn btn-ghost">编辑</a>
                ${item.status === 'published' ? `                  <form method="post" action="/admin/announcements/${itemId}/repush" onsubmit="return confirm('确认再次推送这条公告吗？已点过不再提示或今日不提醒的用户也会再次收到。');">
                    <button type="submit" class="btn btn-primary">再次推送</button>
                  </form>` : ''}
                ${item.status !== 'published' ? `                  <form method="post" action="/admin/announcements/${itemId}/publish">
                    <button type="submit" class="btn btn-primary">发布</button>
                  </form>` : ''}
                ${item.status === 'published' ? `                  <form method="post" action="/admin/announcements/${itemId}/offline">
                    <button type="submit" class="btn btn-danger">下线</button>
                  </form>` : ''}
                <form method="post" action="/admin/announcements/${itemId}/delete" onsubmit="return confirm('确认删除这条公告吗？删除后不可恢复。');">
                  <button type="submit" class="btn btn-ghost">删除</button>
                </form>
              </div>
            </article>`;
}

function renderDashboardPage({ title = '公告中心', items = [], filter = 'all', flash = null } = {}) {
  const cards = items.length > 0
    ? items.map(renderAnnouncementCard).join('\n')
    : '            <div class="empty-state">当前筛选下还没有公告。</div>';

  return renderPage({
    title,
    body: `    <header class="topbar">
      <div>
        <div class="brand-eyebrow">DanmuApiApp</div>
        <h1>公告中心</h1>
      </div>
      <div class="topbar-actions">
        <a href="/admin/announcements/new" class="btn btn-primary">新建公告</a>
        <form method="post" action="/admin/logout">
          <button type="submit" class="btn btn-ghost">退出登录</button>
        </form>
      </div>
    </header>

    <main class="page-shell">
      ${renderFlash(flash)}

      <section class="panel">
        <div class="panel-heading">
          <div>
            <h2>公告列表</h2>
            <p class="muted">管理草稿、已发布和已下线公告。</p>
          </div>
          <div class="filter-row">
            <a class="chip${active(filter === 'all')}" href="/admin?status=all">全部</a>
            <a class="chip${active(filter === 'draft')}" href="/admin?status=draft">草稿</a>
            <a class="chip${active(filter === 'published')}" href="/admin?status=published">已发布</a>
            <a class="chip${active(filter === 'offline')}" href="/admin?status=offline">已下线</a>
            <a class="chip${active(filter === '待生效')}" href="/admin?status=%E5%BE%85%E7%94%9F%E6%95%88">待生效</a>
          </div>
        </div>

        <div class="announcement-grid">
${cards}
        </div>
      </section>
    </main>`,
  });
}

module.exports = {
  renderDashboardPage,
};
