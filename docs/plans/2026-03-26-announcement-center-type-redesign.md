# Announcement Center Type Redesign Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为公告中心新增显式的短期/长期公告模型，重构后台表单，并让 App 按公告类型展示正确的关闭行为。

**Architecture:** 在公告中心数据库中新增 `announcement_type` 字段，服务层统一做类型归一化与兼容映射。后台界面改为中文业务表单，接口保持兼容并补充新字段，App 侧只改数据模型和交互文案，不改现有弹窗视觉结构。

**Tech Stack:** Node.js, Express, EJS, MySQL, Kotlin, Jetpack Compose, kotlinx.serialization

---

### Task 1: 数据库与校验层

**Files:**
- Modify: `announcement-center/src/db.js`
- Modify: `announcement-center/src/validation.js`

**Step 1: 写入失败前提的业务规则**

- 短期公告必须有结束时间
- 长期公告允许结束时间为空
- 公告类型只允许 `short` / `long`

**Step 2: 扩展数据库结构**

- 为 `announcements` 增加 `announcement_type`
- 保证历史库可安全启动

**Step 3: 扩展表单归一化**

- 新增公告类型中文值与内部值映射
- 保持严重级别、状态、版本变体都支持中文输入

**Step 4: 运行后端语法检查**

Run: `node --check announcement-center/src/validation.js`
Expected: 无语法错误

### Task 2: 后台服务与列表页

**Files:**
- Modify: `announcement-center/src/server.js`
- Modify: `announcement-center/views/dashboard.ejs`

**Step 1: 服务层支持新类型字段**

- `toEditorValues()` 返回公告类型
- `toEditorFormFromBody()` 支持中文表单回填
- `serializeAnnouncement()` 返回 `announcement_type`

**Step 2: 创建与编辑 SQL 补齐字段**

- 插入与更新语句包含 `announcement_type`

**Step 3: 列表页展示类型**

- 在卡片中展示“短期公告 / 长期公告”

**Step 4: 运行后端语法检查**

Run: `node --check announcement-center/src/server.js`
Expected: 无语法错误

### Task 3: 后台编辑页重构

**Files:**
- Modify: `announcement-center/views/editor.ejs`
- Modify: `announcement-center/public/style.css`

**Step 1: 重组页面结构**

- 按“基础内容 / 展示方式 / 发布时间 / 跳转按钮”拆分

**Step 2: 全部中文化**

- 用中文标签替换英文枚举
- 加上简短说明文字

**Step 3: 增加联动提示**

- 选择短期公告时强调结束时间必填
- 选择长期公告时说明会显示“今日不提醒”

**Step 4: 本地检查渲染相关语法**

Run: `node --check announcement-center/src/server.js`
Expected: 模板渲染入口保持正常

### Task 4: App 数据模型与交互语义

**Files:**
- Modify: `app/src/main/java/com/example/danmuapiapp/domain/model/AnnouncementModels.kt`
- Modify: `app/src/main/java/com/example/danmuapiapp/ui/screen/home/HomeScreen.kt`

**Step 1: 增加公告类型模型**

- 解析接口新增 `announcement_type`
- 提供 `isShortTerm()` / `isLongTerm()` 语义

**Step 2: 按类型调整按钮行为文案**

- 短期公告隐藏“今日不提醒”
- 短期公告确认按钮显示“`不再提示`”
- 长期公告维持“今日不提醒”

**Step 3: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`
Expected: 编译通过

### Task 5: 总体验证

**Files:**
- Modify: `announcement-center/src/db.js`
- Modify: `announcement-center/src/validation.js`
- Modify: `announcement-center/src/server.js`
- Modify: `announcement-center/views/editor.ejs`
- Modify: `announcement-center/views/dashboard.ejs`
- Modify: `announcement-center/public/style.css`
- Modify: `app/src/main/java/com/example/danmuapiapp/domain/model/AnnouncementModels.kt`
- Modify: `app/src/main/java/com/example/danmuapiapp/ui/screen/home/HomeScreen.kt`

**Step 1: 运行后端语法检查**

Run: `node --check announcement-center/src/server.js`
Expected: PASS

**Step 2: 运行 Kotlin 编译**

Run: `./gradlew :app:compileDebugKotlin`
Expected: PASS

**Step 3: 打包需要时构建 release**

Run: `./gradlew assembleRelease`
Expected: PASS
