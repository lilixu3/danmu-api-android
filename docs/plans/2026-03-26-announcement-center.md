# Announcement Center Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a VPS-backed announcement center with an admin console and wire DanmuApiApp to fetch and show announcement popups.

**Architecture:** Keep the announcement backend independent from the danmu API core. Reuse the app's existing foreground-check and bottom-sheet patterns, and let the Android app query a single active-announcement API endpoint based on app version and current core variant.

**Tech Stack:** Kotlin + Hilt + OkHttp + Compose on Android, Node.js + Express + EJS + MySQL on VPS, Docker Compose for deployment.

---

### Task 1: Add plan and design artifacts

**Files:**
- Create: `docs/plans/2026-03-26-announcement-center-design.md`
- Create: `docs/plans/2026-03-26-announcement-center.md`

**Step 1: Write the design and implementation plan**

Capture the agreed architecture, backend scope, Android integration points, and deployment target.

**Step 2: Verify files exist**

Run: `ls docs/plans`
Expected: both announcement plan files are listed

### Task 2: Add Android announcement domain and persistence

**Files:**
- Modify: `app/src/main/java/com/example/danmuapiapp/domain/repository/Repositories.kt`
- Modify: `app/src/main/java/com/example/danmuapiapp/data/repository/SettingsRepositoryImpl.kt`
- Create: `app/src/main/java/com/example/danmuapiapp/domain/model/AnnouncementModels.kt`

**Step 1: Add announcement settings contract**

Add announcement base URL state and setter to `SettingsRepository`.

**Step 2: Persist base URL with a sensible default**

Default to `http://117.72.165.47:18086`.

**Step 3: Add announcement models**

Create app-facing announcement DTOs and enums.

### Task 3: Add Android remote fetcher and foreground checker

**Files:**
- Create: `app/src/main/java/com/example/danmuapiapp/data/remote/announcement/AnnouncementRemoteService.kt`
- Create: `app/src/main/java/com/example/danmuapiapp/data/service/AppForegroundAnnouncementChecker.kt`

**Step 1: Implement remote fetcher**

Use `OkHttp` to call the active-announcement endpoint with app version and current variant.

**Step 2: Implement foreground checker**

Mirror `AppForegroundUpdateChecker` behavior: interval-based checking, in-memory latest prompt, local snooze and consumed state.

**Step 3: Add version comparison and target filtering helpers**

Handle `min_app_version`, `max_app_version`, and variant filtering safely.

### Task 4: Wire Android announcement flow into activity and home UI

**Files:**
- Modify: `app/src/main/java/com/example/danmuapiapp/MainActivity.kt`
- Modify: `app/src/main/java/com/example/danmuapiapp/ui/screen/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/example/danmuapiapp/ui/screen/home/HomeScreen.kt`

**Step 1: Trigger checks on foreground resume**

Inject the new announcement checker and call `onAppResume()`.

**Step 2: Observe announcement prompt state in HomeViewModel**

Populate prompt fields from the checker and expose dismiss / snooze / open URL actions.

**Step 3: Render popup in HomeScreen**

Reuse `AppBottomSheetDialog` and support title, summary, content preview, and action buttons.

### Task 5: Add App settings UI for announcement service URL

**Files:**
- Modify: `app/src/main/java/com/example/danmuapiapp/ui/screen/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/example/danmuapiapp/ui/screen/settings/NetworkSettingsScreen.kt`

**Step 1: Expose announcement base URL in settings view model**

Load current value and add save action.

**Step 2: Add editable settings section**

Create a field and save button under network settings so the endpoint can be changed without a rebuild.

### Task 6: Build VPS announcement center service

**Files:**
- Create: `announcement-center/package.json`
- Create: `announcement-center/package-lock.json`
- Create: `announcement-center/src/server.js`
- Create: `announcement-center/src/db.js`
- Create: `announcement-center/src/auth.js`
- Create: `announcement-center/src/validation.js`
- Create: `announcement-center/views/*.ejs`
- Create: `announcement-center/public/*`
- Create: `announcement-center/docker-compose.yml`
- Create: `announcement-center/.env.example`

**Step 1: Build DB schema and migration bootstrap**

Create tables for admins and announcements.

**Step 2: Build auth flow**

Implement login/logout and password hashing.

**Step 3: Build admin UI**

Implement list, create, edit, publish, unpublish, and preview.

**Step 4: Build app-facing API**

Return the highest-priority active announcement for Android.

### Task 7: Deploy backend to VPS

**Files:**
- Create on VPS: `/opt/danmu-announcement-center/*`

**Step 1: Create DB and service env**

Provision a dedicated MySQL database and service environment.

**Step 2: Upload service files**

Copy `announcement-center` to the VPS target directory.

**Step 3: Start service**

Run Docker Compose, verify the container is healthy, and confirm the admin URL is reachable on `18086`.

### Task 8: Verify end to end

**Files:**
- Use the app project and VPS deployment

**Step 1: Local Android verification**

Run compile verification for the app module.

**Step 2: Backend verification**

Check login, create a test announcement, publish it, and call the app-facing API.

**Step 3: Functional verification**

Confirm the app can hit the configured endpoint and that the popup path is wired correctly.
