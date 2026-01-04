# DanmuApiApp（Android）

DanmuApiApp 是一个 Android “壳”应用：通过 **nodejs-mobile** 在普通未 Root 的手机上运行 Node.js，并在局域网内提供弹幕 API 服务（前台服务常驻，便于家庭内网/NAS/电视盒子等设备调用）。

> 本 App 内置的弹幕服务逻辑**基于** `huangxd-/danmu_api`：
> https://github.com/huangxd-/danmu_api

---

## 快速使用

1. 安装 APK，打开 App。
2. **强烈建议先设置电池策略为“无限制/不受限制”**（否则部分机型会很快后台杀进程）：
   - 系统设置 → 应用 → DanmuApiApp → 电池/耗电管理 → 选择 **不受限制 / 无限制**
   - 如果系统有“自启动/后台活动/后台弹出界面/锁屏清理”等开关，也建议一并允许。
3. 回到 App 主界面，点击 **“启用”**。
   - 启用中按钮会显示“启用中…”并禁用，避免连点。
   - 启用成功后按钮会变为 **“已启用”** 并保持禁用。
4. 启用后页面会显示两个访问地址：
   - **局域网地址**：同一 Wi‑Fi/局域网下的其他设备访问（推荐）
   - **本机地址**：手机本机访问

---

## 默认配置

- `TOKEN=87654321`
- `ADMIN_TOKEN=admin`

说明：

- `TOKEN`：客户端/API 调用需要用的普通访问令牌。
- `ADMIN_TOKEN`：系统管理/管理功能令牌（用于更高权限的管理接口/页面）。

---

## 目录结构说明（开发/二次开发）

- Android App 入口：`app/src/main/java/.../MainActivity.kt`
- Node.js Mobile 入口（打包进 assets）：`app/src/main/assets/nodejs-project/main.js`
- Android 启动脚本（ESM）：`app/src/main/assets/nodejs-project/android-server.mjs`
- 内置弹幕服务（来源 `huangxd-/danmu_api`）：`app/src/main/assets/nodejs-project/danmu_api/`

---

## 常见问题

### 1) 为什么启用后很快又变成未运行？

大概率是系统省电策略/后台限制导致。

请务必将 DanmuApiApp 的电池策略改为 **不受限制/无限制**，并允许后台活动、自启动（尤其是 MIUI/ColorOS/Funtouch/EMUI 等系统）。

### 2) 局域网访问不到？

- 确认手机和访问设备在同一 Wi‑Fi/局域网。
- 确认没有开启 VPN/代理导致路由隔离。
- 确认路由器未启用 AP 隔离/客户端隔离。

---

## 致谢

- 弹幕服务核心：`huangxd-/danmu_api` https://github.com/huangxd-/danmu_api
- Node.js on Android：`nodejs-mobile`
