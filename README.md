# 弹幕API (Android)

DanmuApiApp 是一个 Android “壳”应用：在手机/平板上通过 **nodejs-mobile** 运行 Node.js，把设备变成局域网可访问的 **danmu_api** 服务端。

## 功能概览

- **本地 HTTP 服务**：手机即服务端，局域网访问
- **多核心切换**：stable / dev / custom 三套 danmu_api 核心
- **内置更新**：App 自更新 + 核心更新（支持 GitHub 代理/自定义源）
- **运行模式**：普通模式 / Root 模式
- **保活与自启**：前台服务常驻、无障碍保活；Root 模式可选开机自启、崩溃重启守护
- **配置管理**：.env 可视化编辑、多配置档案（profiles）
- **诊断与日志**：健康检查、日志导出（支持 WebDAV）
- **工作目录可切换**：可放到外置存储（需权限）

---

## 下载与安装

1. 进入 Releases 下载 APK。
2. 选择匹配架构：
   - **arm64-v8a**：大多数新设备
   - **armeabi-v7a**：较老 32 位设备
3. 安装后首次打开，按提示设置电池策略为“不受限制/无限制”。

> 最低支持 Android 5.0（API 21）。

---

## 快速使用

1. 打开 App，主页点击 **启用**。
2. 选择下载的核心，推荐 **稳定版**。
2. 页面会显示 **本机地址** / **局域网地址**。
3. 在同一 Wi‑Fi 的设备中访问即可。

---

## 运行模式说明

### 普通模式（默认）
- 通过前台服务启动 Node（独立进程 `:node`）。
- 需要通知权限，系统可能限制后台运行。
- 可选 **无障碍保活**（更稳定，但需开启无障碍服务）。
- 可选 **开机自启**（依赖系统广播，部分 ROM 可能限制）。

### Root 模式（可选）
- 通过 root + `app_process` 启动独立进程，不依赖前台服务/通知，**更省电、更稳**。
- 支持 **Magisk/KernelSU 模块开机自启**（仅触发一次，不轮询）。
- 可选 **崩溃重启守护**（Root 守护进程监听 `:node` 崩溃）。
- 需要 Root 授权；首次使用可能会弹出授权提示。

> Root 模式开机自启：需要至少打开过一次 App，确保运行时文件已同步到 `/data/adb`。

---

## 配置与目录

- 运行时目录（默认）：`/data/user/0/com.example.danmuapiapp/files/`
- 可自定义运行目录（安卓11+需要授予所有文件访问权限）
- 配置文件：`config/.env`
- 多配置档案：`config/profiles/*.env`
- 核心目录：
  - `danmu_api_stable/`
  - `danmu_api_dev/`
  - `danmu_api_custom/`

常用配置：
- `TOKEN`（默认 `87654321`）
- `ADMIN_TOKEN`（建议设置）

---

## 更新机制

- **App 更新**：从 GitHub Releases 检查并下载（支持代理/Token）。
- **核心更新**：App 内置更新器下载 danmu_api（stable/dev/custom）。
- 自定义核心支持自定义仓库地址。

---

## 常见问题

- **启动失败/提示未安装核心**：先在“更新弹幕 API”中下载核心。
- **后台被杀**：将电池策略改为“不受限制/无限制”，必要时开启无障碍保活。
- **Root 模式无法开机自启**：确保安装 Magisk/KernelSU 模块，并至少打开过一次 App 完成同步。

---

## 安全提示

- 强烈建议修改 `TOKEN` / `ADMIN_TOKEN`。
- 不建议将服务暴露到公网。

---

## 核心来源 / 致谢

- danmu_api：`huangxd-/danmu_api`、`lilixu3/danmu_api`
- nodejs-mobile
