const { escapeHtml, renderFlash, renderPage } = require('./helpers');

function renderLoginPage({ title = '公告后台登录', flash = null } = {}) {
  return renderPage({
    title,
    bodyClass: 'page-auth',
    body: `    <main class="auth-card">
      <div class="brand-eyebrow">DanmuApiApp</div>
      <h1>公告中心</h1>
      <p class="muted">登录后可以创建、发布和管理 App 公告。</p>

      ${renderFlash(flash)}

      <form method="post" action="/admin/login" class="auth-form">
        <label>
          <span>账号</span>
          <input type="text" name="username" placeholder="管理员账号" required />
        </label>
        <label>
          <span>密码</span>
          <input type="password" name="password" placeholder="管理员密码" required />
        </label>
        <button type="submit" class="btn btn-primary">登录后台</button>
      </form>
    </main>`,
  });
}

module.exports = {
  renderLoginPage,
};
