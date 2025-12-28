# Danmu API Android App

这是 **danmu_api** 的 Android App 封装版：在手机上通过内置 Node.js（nodejs-mobile）运行 danmu_api，本机一键启用弹幕 API 服务，供支持弹弹play规范/同类接口的播放器使用。:contentReference[oaicite:0]{index=0}

## 下载
在本仓库 **Releases** 下载 APK（通常按 ABI 提供 `arm64-v8a` / `armeabi-v7a`）。

## 使用
1. 安装并打开 App  
2. 点击「启用」启动服务  
3. 按 App 内显示的地址/端口，在播放器里填写弹幕服务器地址  
4. 需要停止时，回到 App 点击「停用」

> 上游作者提示：请不要在国内媒体平台宣传该项目。:contentReference[oaicite:1]{index=1}

## 构建（开发者）
- 本地：`./gradlew :app:assembleRelease`
- CI：仓库提供 GitHub Actions 工作流，支持拉取上游 `danmu_api/` 并打包到 `assets/nodejs-project/danmu_api`，按 ABI 产出 APK 并发布 Release。

## 致谢
- 上游项目：huangxd-/danmu_api（JS 弹幕 API 服务端）。:contentReference[oaicite:2]{index=2}

## License
上游 danmu_api 使用 **AGPL-3.0**。:contentReference[oaicite:3]{index=3}  
本仓库如打包/修改了上游代码，发布 APK 时请一并遵守对应开源协议要求。
