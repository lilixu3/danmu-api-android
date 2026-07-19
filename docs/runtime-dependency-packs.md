# 核心运行时依赖包

Android App 不在手机上执行 `npm install`。核心升级时，App 会在 staging 目录读取核心根目录的 `package.json`，先检查 App 公共 `node_modules` 和该核心自己的 `node_modules`，只有发现缺失依赖时才查询签名依赖仓库。

## 适用范围

稳定版、开发版和自定义版使用同一套流程：

1. 下载并解压新核心到 staging；
2. 合并读取 `dependencies` 与 `optionalDependencies`；
3. 计算规范化 SHA-256 依赖指纹；
4. 检查已有运行时是否已满足版本范围；
5. 缺包时查询签名索引中的 `dependencyEntries[fingerprint]`；
6. 命中后下载并校验依赖包，安装到 staging 核心自己的 `node_modules`；
7. 再次校验依赖，成功后才替换旧核心；
8. Root 模式将核心和核心本地依赖作为同一目录同步。

依赖包仓库：

<https://github.com/lilixu3/danmu-api-runtime-packs>

依赖仓库只自动监控官方上游：

<https://github.com/huangxd-/danmu_api>

开发版或自定义版不会被当成官方稳定核心；它们只是可以在**完整依赖指纹完全一致**时复用已经发布的纯 JavaScript 依赖包。

## 未收录依赖

如果签名索引没有对应依赖指纹，App 会中止 staging 安装并提示未安装的包名，旧核心目录不会被替换。此流程不会自动向 npm 下载，也不会在设备上执行依赖脚本。

以下依赖不会进入自动补齐通道：

- 带有 `preinstall`、`install` 或 `postinstall` 的包；
- `.node`、`.so`、`.dll`、`.dylib`、`binding.gyp` 等原生产物；
- 依赖特定 CPU、操作系统或 libc 的包；
- `git:`、`file:`、`http:`、私有 registry 等非公开 npm semver 依赖；
- 与 App 内嵌 Node.js 18 不兼容的包。

这类情况需要发布包含相应 ABI/运行时能力的新 App，不能通过 JavaScript 依赖包绕过。

## 完整性与安全

App 在写入 staging 核心前依次验证：

- 内置 RSA 公钥对应的 `index.json` 签名；
- 索引来源仍为 `huangxd-/danmu_api`；
- 依赖指纹映射与 entry 一致；
- Release URL 必须属于受信任依赖包仓库；
- ZIP 大小和 SHA-256；
- manifest SHA-256、协议版本和 `nodeMajor=18`；
- 包列表与每个运行文件的大小、路径和 SHA-256；
- ZIP 路径穿越、重复路径、条目数量和总解压大小。

依赖包的 manifest 与 npm lock 会作为隐藏审计文件保存在核心目录中，使普通模式、Root 模式和回滚使用同一依赖快照。

## 发布频率

依赖仓库每 2 小时检查一次官方上游 `main`，并保留手动 workflow dispatch。检测到新提交后，CI 在 Node.js 18.20.4 下解析依赖、禁用生命周期脚本、执行 `worker.js` smoke、发布 Release 并重新签名索引。
