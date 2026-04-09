<div align="center">

# 弹幕 API Android

在手机上运行弹幕 API 服务，局域网内任意设备均可访问

<br>

[📦 下载最新版本](https://github.com/lilixu3/danmu-api-android/releases) · [🐛 反馈问题](../../issues) · [💬 讨论](../../discussions)

</div>

---

## 这是什么

一个 Android 应用，让你的手机变成弹幕 API 服务器。安装后在本机启动服务，同一局域网内的电视、电脑、平板都可以直接调用，不需要额外的服务器或电脑常开。

---

## 功能特性

### 🚀 一键启动服务
- 打开 App 点击启动，服务即刻运行
- 支持开机自启，手机重启后自动恢复

### 📡 局域网访问
- 服务启动后，首页会显示本机地址和局域网地址
- 复制地址给播放器直接使用，无需配置

### 🔧 核心管理
- 内置稳定版和开发版两套核心
- 支持一键切换、更新、回退
- 支持安装自定义核心

### ⚙️ 可视化配置
- 无需手动编辑配置文件
- App 内直接修改各项参数
- 改完自动生效无需重启

### 🛠️ 内置工具
- **接口调试**：直接在 App 内测试 API 是否正常
- **弹幕推送**：手动推送弹幕到指定视频
- **请求记录**：查看所有经过服务的请求
- **实时日志**：查看服务运行日志
- **设备控制**：设备黑名单管理
- **弹幕下载**：支持下载弹幕到本地
- **缓存管理**：查看和管理弹幕缓存

### 💾 备份与恢复
- 支持将配置导出备份
- 通过 WebDAV 自动同步到网盘
- 换机迁移方便快捷

### 🌐 网络优化
- 内置 GitHub 线路测速
- 自动选择最快的下载节点
- 国内网络也能流畅更新核心

### 📱 设备兼容
- 支持 Android 6.0 及以上设备
- TV/盒子自动进入兼容模式
- 支持扫码同步配置

---

## 快速开始

### 安装

1. 前往 [Releases](https://github.com/lilixu3/danmu-api-android/releases/latest) 页面下载最新版 APK
2. 选择适合你设备架构的 APK 文件：
   - `arm64-v8a`：绝大多数 2017 年后的手机（优先选择）
   - `armeabi-v7a`：较老的 32 位 ARM 设备
   - `x86_64`：x86 架构设备（少数模拟器或特殊机型）
3. 安装 APK 文件（首次安装需要允许「安装未知来源应用」）

### 启动服务

1. 打开应用
2. 点击首页的「启动服务」按钮
3. 服务启动后，首页会显示服务地址
4. 将地址复制到播放器中使用

### 基本配置

1. **核心管理**：在「设置」页面可以切换稳定版/开发版核心
2. **服务配置**：在「配置」页面可以修改服务参数
3. **网络设置**：在「设置」页面可以配置 GitHub 代理

---

## 技术架构

### 项目结构

- **Android 应用**：提供用户界面和系统集成
- **Node.js 服务**：弹幕 API 核心服务
- **公告中心**：服务端公告管理

### 技术栈

- **前端**：Kotlin、Jetpack Compose
- **后端**：Node.js
- **依赖注入**：Hilt
- **异步编程**：Coroutines
- **网络请求**：OkHttp
- **本地存储**：SQLite
- **容器化**：Docker (公告中心)

### 架构特点

- **分层架构**：domain 层、data 层、ui 层清晰分离
- **模块化设计**：按功能拆分，便于维护和扩展
- **依赖注入**：使用 Hilt 实现依赖注入，提高代码可测试性
- **响应式编程**：使用 Coroutines 和 Flow 实现异步操作

---

## 常见问题

### 安装时提示"未知来源"怎么办？
APK 不是从应用商店安装，需要手动允许。在弹出的提示中点击「设置」，开启「允许安装未知来源应用」，然后返回重新安装即可。

### 启动服务后局域网其他设备访问不了？
1. 确认手机和目标设备连接的是同一个 Wi-Fi
2. 检查手机防火墙或安全软件是否拦截了端口（默认 9321）
3. 部分路由器开启了「AP 隔离」，会阻止同一 Wi-Fi 下设备互访，在路由器设置中关闭即可

### 服务运行一段时间后自动停了？
这是 Android 系统的电池优化导致的。解决方法：
1. 进入手机「设置 → 电池 → 电池优化」，找到本 App，选择「不限制」
2. App 内「设置 → 服务配置」中开启无障碍保活（可选，但会增加耗电）

### 提示需要 Root 权限，不 Root 能用吗？
完全可以。Root 模式是可选的增强功能，普通模式下所有核心功能均正常使用。

### 核心安装失败或更新失败？
1. 检查网络连接是否正常
2. 进入「设置 → 网络」，尝试切换 GitHub 下载节点，或配置 GitHub Token 提升下载限额

### App 申请了无障碍权限，会不会读取我的隐私？
不会。无障碍服务仅用于在服务意外停止时自动拉起，已明确关闭窗口内容读取（`canRetrieveWindowContent=false`），不会读取任何屏幕内容或其他应用数据。

---

## 开发与贡献

### 开发环境要求
- Android Studio Arctic Fox 或更高版本
- JDK 17 或更高版本
- Node.js 16 或更高版本（用于公告中心开发）

### 构建项目

```bash
# 克隆仓库
git clone https://github.com/lilixu3/danmu-api-android

# 进入项目目录
cd danmu-api-android

# 构建发布版本
./gradlew assembleRelease
```

### 代码风格
- 遵循 Kotlin 官方代码风格
- 遵循 Android 官方编码规范
- 使用 Hilt 进行依赖注入
- 使用 Jetpack Compose 构建 UI

### 贡献流程
1. Fork 项目仓库
2. 创建功能分支
3. 提交代码
4. 推送分支
5. 创建 Pull Request

---

## 许可证

本项目基于 [MIT License](LICENSE) 开源。

---

## 联系方式

- **GitHub 仓库**：[https://github.com/lilixu3/danmu-api-android](https://github.com/lilixu3/danmu-api-android)
- **Issues**：[https://github.com/lilixu3/danmu-api-android/issues](https://github.com/lilixu3/danmu-api-android/issues)
- **Discussions**：[https://github.com/lilixu3/danmu-api-android/discussions](https://github.com/lilixu3/danmu-api-android/discussions)

---

<div align="center">

**如果你觉得这个项目对你有帮助，欢迎给个 ⭐️ 支持一下！**

</div>