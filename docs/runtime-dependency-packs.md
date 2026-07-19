# 核心运行时依赖包

Android App 不在手机上执行 `npm install`。核心升级时，App 会在 staging 目录读取核心根目录的 `package.json`，先检查 App 公共 `node_modules` 和该核心自己的 `node_modules`；只有发现缺失依赖时才查询对应通道的签名依赖仓库。

## 固定核心通道

依赖包仓库：

<https://github.com/lilixu3/danmu-api-runtime-packs>

App 只接受以下两组固定映射：

| App 核心 | 核心来源 | 核心分支 | 签名索引 |
|---|---|---|---|
| 稳定版 | `huangxd-/danmu_api` | `main` | `stable/index.json` |
| 开发版 | `lilixu3/danmu_api` | `main` | `dev/index.json` |

稳定版不会回退到开发版索引，开发版也不会回退到稳定版索引。即使两个通道的完整依赖指纹相同，它们仍使用各自签名的 entry、manifest 和 Release ZIP。

自定义核心不会查询稳定版或开发版依赖索引。自定义核心已有运行时能够满足全部依赖时仍可安装；存在缺失依赖时，App 会在替换旧核心前取消更新并明确提示。

## 更新流程

1. 从所选固定仓库的 `main` 下载并解压新核心到 staging；
2. 合并读取 `dependencies` 与 `optionalDependencies`；
3. 计算规范化 SHA-256 依赖指纹；
4. 检查 App 公共运行时和核心本地依赖是否已经满足版本范围；
5. Stable 缺包时只查 stable 索引，Dev 缺包时只查 dev 索引；
6. 命中后下载、验签并安装到 staging 核心自己的 `node_modules`；
7. 再次校验依赖，成功后才原子替换旧核心；
8. Root 模式将核心、核心本地依赖和审计文件作为同一目录同步。

## 未收录依赖

如果对应通道的签名索引没有当前依赖指纹，App 会中止 staging 安装并提示未安装的包名，旧核心目录不会被替换。此流程不会自动向 npm 下载，也不会在设备上执行依赖脚本。

以下依赖不会进入自动补齐通道：

- 带有 `preinstall`、`install` 或 `postinstall` 的包；
- `.node`、`.so`、`.dll`、`.dylib`、`binding.gyp` 等原生产物；
- 依赖特定 CPU、操作系统或 libc 的包；
- `git:`、`file:`、`http:`、私有 registry 等非公开 npm semver 依赖；
- 与 App 内嵌 Node.js 18 不兼容的包。

这类情况需要发布包含相应 ABI/运行时能力的新 App，不能通过 JavaScript 依赖包绕过。

## 完整性与安全

App 在写入 staging 核心前依次验证：

- 内置 RSA 公钥对应的通道 `index.json` 原始字节签名；
- schema、channel、core repo 和 branch 与所选 App 核心严格一致；
- 每个 entry 的通道、来源 SHA 和依赖指纹映射一致；
- Release URL 必须使用对应的 `stable-core-*` 或 `dev-core-*` 路径；
- ZIP 大小和 SHA-256；
- manifest SHA-256、channel、repo、branch、协议版本和 `nodeMajor=18`；
- 包列表与每个运行文件的大小、路径和 SHA-256；
- ZIP 路径穿越、重复路径、条目数量和总解压大小。

依赖包的 manifest 与 npm lock 会作为隐藏审计文件保存在核心目录中，使普通模式、Root 模式和回滚使用同一依赖快照。

## 发布频率

依赖仓库每 2 小时错峰检查：

- minute 17：`huangxd-/danmu_api@main`；
- minute 47：`lilixu3/danmu_api@main`。

CI 在 Node.js 18.20.4 下解析依赖、禁用生命周期脚本并执行 `worker.js` smoke。核心代码只在无签名私钥和无仓库写权限的 Build Job 中运行；Publish Job 只发布已验证产物并签署对应通道索引。
