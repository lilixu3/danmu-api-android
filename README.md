# DanmuApiApp (Android)

这是一个把 **Node.js + danmu_api** 封装到 Android 的“壳”应用：通过 **nodejs-mobile** 在普通未 Root 的手机上运行 `danmu_api`（不需要解锁 BL / Magisk）。

- **danmu_api 上游项目**：基于 `https://github.com/huangxd-/danmu_api` 开发（本仓库主要做 Android 端封装与适配）。
- 运行方式：App 内启动 **前台服务（Foreground Service）**，后台常驻运行 Node，并提供 HTTP API。

> 请遵循上游项目及本项目所引用依赖的 License。

---

## 1. 使用说明（装好 APK 直接用）

1. 安装 APK 并打开 App。
2. 点击主界面 **“启用”**：
   - 启用中会显示 **“启用中…”**，避免重复点击。
   - 服务真正运行后按钮会变成 **“已启用”** 并保持禁用。
3. **Android 13+** 可能会弹出通知权限请求：建议允许，否则前台服务通知可能被系统拦截。
4. App 页面会显示两个地址（可点击打开/长按复制）：
   - **本机地址**（手机自己访问）：`http://127.0.0.1:9321/`
   - **局域网地址**（同一 Wi-Fi 的其它设备访问）：`http://<手机局域网IP>:9321/`
5. 需要停止时点击 **“关闭并退出”**（会先请求 Node 优雅退出，再停止服务）。

### 强烈建议：关闭电池限制（否则容易后台被杀）

为了让服务长期稳定运行，请把本 App 的电池策略改为 **“无限制/不受限制（Unrestricted）”**：

- 系统设置 → 应用 → DanmuApiApp → 电池/省电策略 → 选择 **不受限制/无限制**
- 另外建议（不同 ROM 入口不一样）：
  - 允许“后台活动/后台运行”
  - 开启“自启动/后台弹出界面”（如 MIUI/ColorOS 等）
  - 把 App 锁在最近任务里（防止一键清理）

---

## 2. 默认端口与访问方式

本项目默认启动两个 HTTP 服务（可通过环境变量修改，见下文）：

- 主服务（danmu_api）：`0.0.0.0:9321`
- 代理服务（/proxy 转发）：`0.0.0.0:9322`

---

## 3. 鉴权与默认 Token（安全提示）

配置文件中有两个 Token：

- `TOKEN`：API 访问令牌（默认 **87654321**）
- `ADMIN_TOKEN`：系统管理令牌（默认 **admin**）

**建议你在实际使用前修改默认值**，避免局域网内被别人直接访问。

> 访问规则说明（简化版）：
> - 当 `TOKEN` 不是默认值时，通常需要在 URL 前缀带上 token：`http://<IP>:9321/<TOKEN>/...`
> - `ADMIN_TOKEN` 用于打开/调用管理相关能力（具体以 danmu_api 上游实现为准）。

---

## 4. 配置文件位置与修改方式

Node 侧项目位于：

- `app/src/main/assets/nodejs-project/`

App 首次启动会把 `assets/nodejs-project/` **解压到应用内部目录**（用于运行与写入日志/配置）：

- `context.filesDir/nodejs-project/`
- 对应到 Android 路径通常类似：`/data/data/<applicationId>/files/nodejs-project/`

其中配置目录：

- `.env`：`nodejs-project/config/.env`
- `config.yaml`：`nodejs-project/config/config.yaml`

### 怎么改配置？

- **最省事（推荐）**：安装后访问 http://<IP>:9321/<ADMIN_TOKEN> 进入系统配置界面修改。

---

## 5. Node 侧代码说明（已做 Android 适配）

- 入口：`assets/nodejs-project/main.js`
- 启动脚本：`assets/nodejs-project/android-server.mjs`
- danmu_api 代码：`assets/nodejs-project/danmu_api/`

本项目默认使用 `danmu_api/worker.js` 作为请求处理入口，并在 `android-server.mjs` 中用 Node 的 `http` 包起一个轻量服务。

---


## 7. 常见问题

- **点“启用”后立刻停止 / 报错找不到模块**：
  - 先检查是否已正确放入 `node_modules/`（以及是否包含 `js-yaml`、`https-proxy-agent` 等依赖）。
- **后台运行一段时间后失效**：
  - 按上面的“电池策略”把电池限制改为 **不受限制**，并开启自启动/后台权限。
- **局域网访问不了**：
  - 确认访问设备和手机在同一 Wi‑Fi；
  - 确认手机没有开启 VPN/代理导致局域网隔离；
  - 部分路由器开启 AP 隔离也会导致互访失败。

