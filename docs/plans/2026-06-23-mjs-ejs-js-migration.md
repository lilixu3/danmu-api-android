# MJS / EJS 迁移为 JS 计划

## 目标

将仓库自有源码中的 `.ejs` 与 `.mjs` 文件迁移为 `.js`，降低扩展名与模块系统混用带来的维护成本，同时不改变公告后台、Android 内置 Node 运行时、普通模式、Root 模式和构建流程的既有行为。

## 范围

### 迁移范围

- `announcement-center/views/*.ejs`
- `announcement-center/src/server.js` 中的 EJS 渲染链路
- `node-tests/parse-dotenv-regression.mjs`
- `app/src/main/assets/nodejs-project/android-server.mjs`
- `app/src/main/assets/nodejs-project/worker-proxy.mjs`
- `app/src/main/assets/nodejs-project/startup-failure.mjs`
- Kotlin、Gradle、Root 启动脚本中写死的 `.mjs` 文件名

### 不迁移范围

- `node_modules` 或第三方依赖中的 `.mjs`
- `danmu_api_stable`、`danmu_api_dev`、`danmu_api_custom` 核心源码
- 公告后台数据库结构
- Android App UI 和业务功能

## 当前发现

- `.ejs` 仅存在于公告后台：
  - `announcement-center/views/dashboard.ejs`
  - `announcement-center/views/editor.ejs`
  - `announcement-center/views/login.ejs`
- `.mjs` 自有源码共 4 个：
  - `node-tests/parse-dotenv-regression.mjs`
  - `app/src/main/assets/nodejs-project/android-server.mjs`
  - `app/src/main/assets/nodejs-project/worker-proxy.mjs`
  - `app/src/main/assets/nodejs-project/startup-failure.mjs`
- Android Node 项目当前 `package.json` 没有 `"type": "module"`，`main.js` 是 CommonJS 包装入口，通过动态 `import()` 启动 ESM 脚本。
- `worker-proxy.mjs` 使用顶层 `await`，迁移时必须改成 CommonJS 可执行的 async 启动函数。
- `android-server.mjs` 使用 `import.meta.url`、动态 `import()`、`Worker(..., { type: 'module' })`，迁移风险最高。

## 阶段 1：公告后台 EJS 迁移

目标：移除公告后台自有 `.ejs` 文件和 EJS 运行时依赖。

动作：

- 新增 `announcement-center/src/views/`，用 CommonJS `.js` 文件导出页面渲染函数。
- 新增或内联 HTML 转义工具，确保原 EJS `<%=` 的内容继续被转义。
- 仅对原 EJS `<%- previewHtml %>` 对应的 Markdown 预览 HTML 保持 raw 输出。
- 将 `res.render('login' | 'dashboard' | 'editor')` 改为 `res.send(renderLogin|renderDashboard|renderEditor(...))`。
- 删除 `app.set('view engine', 'ejs')` 和 `app.set('views', ...)`。
- 从 `announcement-center/package.json` 和 `package-lock.json` 移除 `ejs` 依赖。
- 从 `announcement-center/Dockerfile` 移除 `COPY views ./views`。
- 删除 `announcement-center/views/*.ejs`。

验证：

- `node --check announcement-center/src/server.js`
- `find announcement-center/src/views -name '*.js' -print -exec node --check {} \;`
- `rg --files announcement-center -g '*.ejs'` 应无自有 `.ejs` 结果。

说明：公告后台本轮只做静态检查，不连接 MySQL、不启动容器。

## 阶段 2：Node 回归测试脚本迁移

目标：将本地测试脚本从 `.mjs` 改为 CommonJS `.js`。

动作：

- 将 `node-tests/parse-dotenv-regression.mjs` 改为 `node-tests/parse-dotenv-regression.js`。
- 将 `import`、`fileURLToPath(import.meta.url)` 改为 `require`、`__dirname`。
- 更新 `app/build.gradle.kts` 中 `testNodeRuntimeParsing` 的脚本路径。
- 后续阶段迁移 `android-server.mjs` 后，同步把测试脚本读取目标改为 `android-server.js`。

验证：

- `node --check node-tests/parse-dotenv-regression.js`
- `./gradlew :app:testNodeRuntimeParsing`

## 阶段 3：启动失败记录模块迁移

目标：先迁移最小运行时模块，验证 CommonJS 运行方式。

动作：

- 将 `startup-failure.mjs` 改为 `startup-failure.js`。
- 将 `export function` 改为 `module.exports`。
- 将 `fileURLToPath(import.meta.url)` 改为 CommonJS 的 `__filename` / `__dirname`。
- 更新 `main.js` 与 `android-server` 中对启动失败模块的引用。

验证：

- `node --check app/src/main/assets/nodejs-project/startup-failure.js`
- `node --check app/src/main/assets/nodejs-project/main.js`

## 阶段 4：Worker 代理迁移

目标：将 worker 入口从 ESM `.mjs` 改为 CommonJS `.js`，保持 worker 请求转发与日志回传行为不变。

动作：

- 将 `worker-proxy.mjs` 改为 `worker-proxy.js`。
- 将静态 `import` 改为 `require`。
- 将顶层 `await loadWorker()` 包进 async 启动函数。
- 保留对核心 `worker.js` 和 `globals.js` 的动态 `import()`，因为核心文件可能仍是 ESM 形态。
- 更新 `android-server` 中 `new Worker()` 的入口文件名。
- 将 `Worker(..., { type: 'module' })` 调整为 CommonJS worker 入口所需参数。

验证：

- `node --check app/src/main/assets/nodejs-project/worker-proxy.js`

## 阶段 5：Android Node 主服务迁移

目标：将 Android 内置 Node 主服务从 ESM `.mjs` 改为 CommonJS `.js`。

动作：

- 将 `android-server.mjs` 改为 `android-server.js`。
- 将静态 `import` 改为 `require`。
- 将 `import.meta.url` 相关路径计算改为 `__dirname` / `pathToFileURL()`。
- 保留必要的动态 `import()`，用于加载可能是 ESM 的核心 `worker.js`、`globals.js` 和第三方 ESM 依赖。
- 更新 `main.js` 启动入口，从动态导入 `.mjs` 改为加载 `.js`。
- 更新 `node-tests/parse-dotenv-regression.js` 读取目标为 `android-server.js`。

验证：

- `node --check app/src/main/assets/nodejs-project/android-server.js`
- `node --check app/src/main/assets/nodejs-project/main.js`
- `./gradlew :app:testNodeRuntimeParsing`

## 阶段 6：硬编码引用迁移

目标：清理 Kotlin、Gradle、Root 脚本中的 `.mjs` 文件名。

动作：

- 更新 `app/build.gradle.kts`：
  - `checkNodeRuntimeScripts`
  - `testNodeRuntimeParsing`
- 更新 `RuntimeRepositoryImpl.kt` 的普通模式热重启关键文件列表。
- 更新 `RootAutoStartServiceScriptPartA.kt` 的 Root 自动启动文件检查。
- 更新 `RootRuntimeController.kt` 中复制、校验、运行时同步相关脚本。
- 搜索并处理剩余自有源码引用：
  - `rg -n "android-server\\.mjs|worker-proxy\\.mjs|startup-failure\\.mjs|parse-dotenv-regression\\.mjs"`

验证：

- `rg --files -g '*.mjs' -g '!**/node_modules/**'` 应无自有 `.mjs` 结果。
- `rg -n "\\.mjs" -g '!**/node_modules/**'` 仅允许历史文档或第三方依赖说明残留；源码、构建脚本、运行脚本不得残留。

## 阶段 7：总体验证

本地验证：

- `node --check announcement-center/src/server.js`
- `find announcement-center/src/views -name '*.js' -print -exec node --check {} \;`
- `node --check app/src/main/assets/nodejs-project/main.js`
- `node --check app/src/main/assets/nodejs-project/android-server.js`
- `node --check app/src/main/assets/nodejs-project/worker-proxy.js`
- `node --check app/src/main/assets/nodejs-project/startup-failure.js`
- `./gradlew :app:testNodeRuntimeParsing`
- `./gradlew :app:compileDebugKotlin`
- `./gradlew :app:assembleRelease`

人工验证：

- 手机端普通模式启动服务。
- TV 兼容模式启动服务。
- 稳定版、开发版、自定义版核心切换。
- 核心下载、更新、删除。
- Worker 模式请求转发。
- Root 模式启动和重启。
- 启动失败时 `logs/startup-failure.json` 可正常写入。

人工验证由真实设备完成，本地迁移阶段只保证静态检查和构建通过。

## 回滚策略

- 每个阶段单独提交，出现问题时优先回滚对应阶段。
- EJS 迁移可独立回滚，不影响 Android App 运行时。
- Node runtime 迁移出现启动问题时，优先回滚阶段 3 到阶段 6。
- 发布前保留迁移前的最后一个可用 release APK，便于 TV / 手机端回退验证。
