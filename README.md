# DanmuApiApp (Android)

DanmuApiApp 是一个 Android “壳”应用：在**普通未 Root 手机**上通过 **nodejs-mobile** 运行 Node.js，启动本地 HTTP 服务，把手机变成一个局域网可访问的 **danmu_api** 服务端（前台服务常驻）。

本项目支持：

- **多核心切换**：stable / dev / custom 三套 danmu_api 核心目录，随时切换
- **内置更新器**：在 App 内直接下载/更新 danmu_api（并支持 GitHub 代理）
- **运行日志 + 诊断信息**：一键导出日志，健康检查 `/__health`
- **保活与自启**：前台服务 + 可选辅助功能保活；Root 模式下可开机自启（可选功能）

> danmu_api 核心来自：
> - stable：`huangxd-/danmu_api`
> - dev：`lilixu3/danmu_api`

---

## 快速使用

1. 安装 APK，打开 App。
2. **强烈建议先把电池策略设置为“不受限制/无限制”**（否则很多 ROM 会后台杀进程）：
   - 系统设置 → 应用 → DanmuApiApp → 电池/耗电管理 → 选择 **不受限制 / 无限制**
   - 如有“自启动/后台活动/锁屏清理”等开关，也建议允许。
3. 回到 App 主界面点击 **启用**。
4. 启用后你会看到访问地址：
   - **本机地址**：手机本机访问
   - **局域网地址**：同一 Wi‑Fi/局域网下的其他设备访问

---

## 核心目录与切换逻辑

App 的 Node 项目目录为：

```
assets/nodejs-project/
```

danmu_api 核心目录约定为：

- `danmu_api_stable/`（stable 核心）
- `danmu_api_dev/`（dev 核心）
- `danmu_api_custom/`（自定义核心）

Node 入口脚本会根据 `DANMU_API_VARIANT` 选择加载哪一个核心：

- `stable` → `danmu_api_stable/worker.js`
- `dev` → `danmu_api_dev/worker.js`
- `custom` → `danmu_api_custom/worker.js`

> 提示：如果你构建 APK 时 **没有把 danmu_api 打包进 assets**，首次运行需要用 App 内“更新/安装 danmu_api”来下载核心，否则 Node 会因缺少 `worker.js` 无法启动。

---

## 默认配置与安全建议

配置文件：`config/.env`（在首次运行时从 assets 拷贝到应用内部存储）。

建议至少关注：

- `TOKEN=87654321`（默认值）
- `ADMIN_TOKEN=`（默认为空，建议自行设置）

> 安全建议：如果你会在局域网开放给其他设备访问，建议修改 `TOKEN`/`ADMIN_TOKEN`，并避免把服务暴露到公网。

---

## 排错与反馈

App 右上角菜单提供：

- **运行日志**：查看/导出 Node 运行日志
- **诊断信息**：汇总运行模式、端口、局域网 IP、权限状态等，并对 TOKEN 做脱敏；同时会请求 `http://127.0.0.1:<PORT>/__health` 进行健康检查

反馈问题时建议：

1) 复制“诊断信息”
2) 导出一份“运行日志”

---

## GitHub Actions 一键构建 APK

本仓库提供工作流：`.github/workflows/build-android.yml`，支持：

- 拉取 danmu_api（stable/dev）并打包（可开关）
- 下载 nodejs-mobile 预编译产物并集成
- 构建 **按 ABI 拆分** 的 Release APK（arm64-v8a / armeabi-v7a）
- 可选：上传 Actions Artifact / 上传到 Release

### 必要 Secrets（可选但推荐）

为了让 Release APK **可稳定覆盖安装**，建议提供固定 keystore：

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

如不提供，将回退到默认 debug 签名（依然可安装，但签名不稳定，不便覆盖安装）。

### 可选（发布到公开仓库）

- `PUBLIC_RELEASE_TOKEN`：用于向公开仓库上传 Release 资源

---

## 目录结构（开发/二次开发）

- Android 入口：`app/src/main/java/.../MainActivity.kt`
- Node.js Mobile 入口：`app/src/main/assets/nodejs-project/main.js`
- Node 主服务（ESM）：`app/src/main/assets/nodejs-project/android-server.mjs`
- 运行时目录（应用内部存储）：`/data/data/<package>/files/nodejs-project/`

### 关于 node_modules.zip

仓库将 Node 侧依赖打包为 `node_modules.zip`（避免提交大量零散文件）。

- GitHub Actions 构建时会解压到 `app/src/main/assets/nodejs-project/node_modules/`
- 本地 Android Studio 构建时，也会由 Gradle 任务 `prepareNodeModules` 自动解压

---

## 致谢

- danmu_api：`huangxd-/danmu_api`、`lilixu3/danmu_api`
- Node.js on Android：`nodejs-mobile`