# 弹幕 APi (Android)

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

## 默认配置与安全建议

配置文件：`config/.env`（在首次运行时从 assets 拷贝到应用内部存储）。

建议至少关注：

- `TOKEN=87654321`（默认值）
- `ADMIN_TOKEN=`（默认为空，建议自行设置）

> 安全建议：如果你会在局域网开放给其他设备访问，建议修改 `TOKEN`/`ADMIN_TOKEN`，并避免把服务暴露到公网。


---

## 致谢

- danmu_api：`huangxd-/danmu_api`、`lilixu3/danmu_api`
- Node.js on Android：`nodejs-mobile`