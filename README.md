# DanmuApiApp (Node.js Mobile)

这个项目是一个“壳”安卓 App：用 **nodejs-mobile** 把 Node.js 跑在普通未 root 的手机上（不需要解锁 BL / Magisk）。

> 你给的 GitHub Actions 工作流会自动下载并拷贝 `libnode.so` + header，所以仓库里 **不用** 放 Node 核心库。

## 你需要放进来的东西（你删掉的“模块核心/依赖”）

### 1) 你的 JS 项目
把你的模块里真正要跑的 JS 代码放到：

`app/src/main/assets/nodejs-project/`

默认入口文件是：
`app/src/main/assets/nodejs-project/main.js`

如果你有多文件/目录结构，直接把整个项目文件夹内容放进来即可（同级目录放 package.json / main.js / 其它文件）。

### 2) 依赖（node_modules）
- **纯 JS 依赖**：在手机上用 Termux 跑 `npm install --omit=dev`，然后把生成的 `node_modules/` 整个放进
  `app/src/main/assets/nodejs-project/node_modules/`
- **带原生扩展（C/C++）的依赖**：需要用 `prebuild-for-nodejs-mobile` 或自己交叉编译，难度更高（先确认你的依赖是否真需要原生模块）。

## Node 核心库去哪里下（如果你想手动下/自己拷）
nodejs-mobile 的 Android 预编译包在官方 Release 里：

- nodejs-mobile/nodejs-mobile → Releases
- 下载 `*android*.zip` 的那个附件（里面包含 `include/` 和 `bin/`，例如 `bin/arm64-v8a/libnode.so`）

> 但再次强调：你给的工作流已经会自动下载并拷进 `app/libnode/`，你一般不需要手动做这步。

## 前台服务（不容易被杀）
App 启动后点“启动”，会开一个前台服务 `NodeService`，在后台启动 Node 并跑 `main.js`。

默认示例是本机 127.0.0.1:3000 的 HTTP server。
你要改端口/监听地址，直接改 `main.js`。

## 已知限制
- nodejs-mobile 最新官方 release 目前是 v18.20.4（也就是 Node 18.20.4）。
  如果你项目必须 Node 20/22，只能自己从 nodejs-mobile 源码编译新的 core library（工作量会大很多）。
