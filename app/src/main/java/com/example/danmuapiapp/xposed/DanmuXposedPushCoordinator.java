package com.example.danmuapiapp.xposed;

import static com.example.danmuapiapp.xposed.DanmuXposedHttp.buildShellPushUrl;
import static com.example.danmuapiapp.xposed.DanmuXposedHttp.countDanmaku;
import static com.example.danmuapiapp.xposed.DanmuXposedHttp.httpGet;
import static com.example.danmuapiapp.xposed.DanmuXposedHttp.urlEncode;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.extractEpisodeNumber;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.joinNonBlank;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.normalizeDisplayTitle;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.normalizeSearchTitle;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DanmuXposedPushCoordinator {
    interface Host {
        InjectionSettings readInjectionSettings(Context context, int fallbackPort);

        boolean isModuleGenerationActive();

        void log(int level, String message);

        void log(int level, String message, Throwable throwable);
    }

    private final Host host;
    private final DanmuXposedEpisodeRepository episodeRepository;
    private final DanmuXposedShellMediaReader shellMediaReader = new DanmuXposedShellMediaReader();

    private volatile boolean autoLoopStarted = false;
    private volatile boolean playbackActivityVisible = false;
    private volatile int foregroundActivityIdentity = 0;
    private volatile long autoLoopFastUntilMs = 0L;
    private volatile String lastAutoFailureSignature = "";
    private volatile long lastAutoFailureUntilMs = 0L;
    private final Object autoLoopWakeLock = new Object();
    private volatile String lastAutoSignature = "";
    private volatile int lastPlaybackActivityIdentity = 0;
    private volatile long playbackSessionSerial = 0L;
    private volatile WeakReference<Activity> autoLoopActivity = null;
    private volatile PendingAutoPush pendingAutoPush = null;
    private final Object autoPlanLock = new Object();
    private final Object pushGuardLock = new Object();
    private volatile String lastPushInfo = "";
    private volatile String lastPushUrl = "";
    private volatile long lastPushAtMs = 0L;
    private volatile long lastViewedPushAtMs = 0L;
    private final LinkedHashMap<String, Long> inFlightPushes = new LinkedHashMap<>();
    private final LinkedHashMap<String, Long> recentPushes = new LinkedHashMap<>();
    private final LinkedList<String> pushHistory = new LinkedList<>();
    private static final int MAX_PUSH_HISTORY = 6;
    private volatile String lastPushSummary = "";
    private static final String STATUS_NOT_READY = "not_ready";
    private static final long PUSH_IN_FLIGHT_TTL_MS = 20_000L;
    private static final long PUSH_RECENT_TTL_MS = 8_000L;
    private static final long AUTO_POLL_FAST_MS = 850L;
    private static final long AUTO_POLL_STABLE_MS = 4_000L;
    private static final long AUTO_POLL_NO_MEDIA_MS = 3_000L;
    private static final long AUTO_POLL_DISABLED_MS = 5_000L;
    private static final long AUTO_POLL_ERROR_MS = 2_600L;
    private static final long AUTO_POLL_FAST_WINDOW_MS = 15_000L;
    private static final long AUTO_PENDING_FAST_WINDOW_MS = 20_000L;
    private static final long AUTO_FAILURE_COOLDOWN_MS = 15_000L;

    DanmuXposedPushCoordinator(Host host, DanmuXposedEpisodeRepository episodeRepository) {
        this.host = host;
        this.episodeRepository = episodeRepository;
    }

    Activity currentActivity() {
        WeakReference<Activity> ref = autoLoopActivity;
        return ref == null ? null : ref.get();
    }

    void prepareForHotReload(Bundle outState) {
        playbackActivityVisible = false;
        autoLoopActivity = null;
        stop();
        if (outState == null) return;
        outState.putString("lastAutoSignature", lastAutoSignature == null ? "" : lastAutoSignature);
        outState.putString("lastPushInfo", lastPushInfo == null ? "" : lastPushInfo);
        outState.putLong("lastPushAtMs", lastPushAtMs);
        outState.putLong("playbackSessionSerial", playbackSessionSerial);
    }

    void restoreHotReloadState(Bundle bundle) {
        if (bundle == null) return;
        lastAutoSignature = bundle.getString("lastAutoSignature", "");
        lastPushInfo = bundle.getString("lastPushInfo", "");
        lastPushAtMs = bundle.getLong("lastPushAtMs", 0L);
        playbackSessionSerial = bundle.getLong("playbackSessionSerial", playbackSessionSerial);
    }

    void pushCandidate(Activity activity, CandidateHandle candidate, int shellPort, TextView statusText, TextView pushInfoText) {
        statusText.setText("正在推送：" + candidate.label);
        new Thread(() -> {
            BridgeRow row = pushEpisodeCandidate(activity.getApplicationContext(), candidate.handle, shellPort);
            activity.runOnUiThread(() -> {
                statusText.setText(row.message);
                if (pushInfoText != null) pushInfoText.setText(formatLastPushInfo(activity));
                if ("error".equals(row.status)) {
                    Toast.makeText(activity, row.message, Toast.LENGTH_SHORT).show();
                }
            });
        }, "DanmuDirectPush").start();
    }

    BridgeRow pushEpisodeCandidate(Context context, String handle, int shellPort) {
        EpisodeCandidate candidate = episodeRepository.loadEpisodeCandidate(handle);
        if (candidate == null) return new BridgeRow("error", "剧集候选已过期，请重新搜索", "");
        return pushResolvedCandidate(context, candidate, shellPort, "已推送");
    }

    BridgeRow pushResolvedCandidate(Context context, EpisodeCandidate candidate, int shellPort, String prefix) {
        PushGuard guard = null;
        try {
            InjectionSettings settings = host.readInjectionSettings(context, shellPort);
            String danmakuUrl = applyDanmakuParams(candidate.url, settings);
            int effectivePort = shellPort > 0 && shellPort <= 65535 ? shellPort : settings.shellPort;
            String pushUrl = buildShellPushUrl(effectivePort, danmakuUrl);
            guard = beginPushGuard(effectivePort, danmakuUrl);
            if (!guard.allowed) {
                String status = "recent".equals(guard.reason) ? "skip_recent" : "skip_inflight";
                return new BridgeRow(status, "已跳过重复推送：" + candidate.displayLabel(), "");
            }
            httpGet(pushUrl, 1200, 5000);
            finishPushGuard(guard, true);
            String label = buildPushLabel(candidate);
            String message = buildPushPendingMessage(prefix, label);
            scheduleDanmakuCountUpdate(context, candidate, danmakuUrl);
            return new BridgeRow("ok", message, danmakuUrl);
        } catch (Throwable throwable) {
            if (guard != null && guard.allowed) finishPushGuard(guard, false);
            return new BridgeRow("error", "推送失败：" + formatError(throwable), "");
        }
    }

    void autoPushCurrent(Activity activity, int fallbackPort, TextView statusText, TextView pushInfoText) {
        statusText.setText("正在自动匹配当前播放…");
        new Thread(() -> {
            ShellMedia media = readShellMedia(fallbackPort);
            if (media == null || media.title.isEmpty()) {
                activity.runOnUiThread(() -> statusText.setText("未读取到当前播放信息，无法自动推送"));
                return;
            }
            BridgeRow row = queryBridgeAutoPush(activity.getApplicationContext(), media.port > 0 ? media : media.withPort(fallbackPort));
            activity.runOnUiThread(() -> {
                statusText.setText(row.message);
                if (pushInfoText != null) pushInfoText.setText(formatLastPushInfo(activity));
                Toast.makeText(activity, row.message, Toast.LENGTH_SHORT).show();
            });
        }, "DanmuManualAutoPush").start();
    }

    BridgeRow queryBridgeAutoPush(Context context, ShellMedia media) {
        if (media == null || media.title.isEmpty()) {
            return new BridgeRow("error", "自动推送失败：未识别到片名", "");
        }
        try {
            String matchSignature = media.matchSignature();
            PendingAutoPush plan = getPendingAutoPush(matchSignature);
            if (plan == null) {
                int targetEpisodeNumber = extractEpisodeNumber(media.displayEpisode());
                String searchTitle = normalizeSearchTitle(media.title);
                if (searchTitle.isEmpty()) searchTitle = media.title;
                DirectSearch search = episodeRepository.searchAnimeDirect(context, searchTitle);
                if (search.animes.isEmpty()) {
                    clearPendingAutoPush(matchSignature);
                    return new BridgeRow("error", "自动推送未找到剧名：" + searchTitle, "");
                }
                EpisodeCandidate selected = episodeRepository.selectAutoEpisodeInSearchOrder(search.animes, targetEpisodeNumber);
                if (selected == null) {
                    clearPendingAutoPush(matchSignature);
                    return new BridgeRow("error", "自动推送未匹配到剧集：" + media.title + " " + media.displayEpisode(), "");
                }
                plan = new PendingAutoPush(matchSignature, selected, media.port, System.currentTimeMillis());
                setPendingAutoPush(plan);
            }
            if (!media.isReadyForDanmaku()) {
                String stateLabel = media.playbackStateLabel();
                String message = "已预匹配：" + plan.candidate.displayLabel() + "，等待播放态推送" + (stateLabel.isEmpty() ? "" : " · " + stateLabel);
                return new BridgeRow(STATUS_NOT_READY, message, plan.candidate.url);
            }
            BridgeRow pushed = pushResolvedCandidate(context, plan.candidate, media.port > 0 ? media.port : plan.shellPort, "自动推送成功");
            if ("ok".equals(pushed.status) || "skip_recent".equals(pushed.status)) {
                clearPendingAutoPush(matchSignature);
            }
            return pushed;
        } catch (Throwable throwable) {
            return new BridgeRow("error", "自动推送失败：" + formatError(throwable), "");
        }
    }

    void startAutoPushLoopOnce(Activity activity) {
        if (activity == null) return;
        autoLoopActivity = new WeakReference<>(activity);
        markPlaybackActivity(activity);
        Context appContext = activity.getApplicationContext();
        if (appContext == null) return;
        synchronized (this) {
            if (autoLoopStarted) return;
            autoLoopStarted = true;
        }
        Thread loop = new Thread(() -> {
            while (host.isModuleGenerationActive()) {
                long delayMs = AUTO_POLL_FAST_MS;
                try {
                    if (!isPlaybackActivityVisible()) {
                        sleepAutoLoopQuietly(AUTO_POLL_DISABLED_MS);
                        continue;
                    }
                    InjectionSettings settings = host.readInjectionSettings(appContext, 9978);
                    if (!settings.injectionEnabled) {
                        lastAutoSignature = "";
                        clearPendingAutoPush("");
                        sleepAutoLoopQuietly(AUTO_POLL_DISABLED_MS);
                        continue;
                    }
                    if (!settings.autoPushEnabled) {
                        lastAutoSignature = "";
                        clearPendingAutoPush("");
                        sleepAutoLoopQuietly(AUTO_POLL_DISABLED_MS);
                        continue;
                    }
                    ShellMedia media = readShellMedia(settings.shellPort);
                    if (media == null || media.title.isEmpty()) {
                        if (!lastAutoSignature.isEmpty() || hasPendingAutoPush()) {
                            resetAutoSignature("media unavailable");
                        }
                        sleepAutoLoopQuietly(selectNoMediaPollDelay());
                        continue;
                    }
                    String signature = media.signature();
                    if (isAutoFailureCoolingDown(signature)) {
                        sleepAutoLoopQuietly(AUTO_POLL_STABLE_MS);
                        continue;
                    }
                    if (!signature.equals(lastAutoSignature) || hasPendingAutoPush()) {
                        BridgeRow row = queryBridgeAutoPush(appContext, media);
                        boolean pushed = "ok".equals(row.status);
                        boolean recentSkipped = "skip_recent".equals(row.status);
                        boolean inFlightSkipped = "skip_inflight".equals(row.status);
                        boolean notReady = STATUS_NOT_READY.equals(row.status);
                        if (pushed || recentSkipped) {
                            lastAutoSignature = signature;
                            lastAutoFailureSignature = "";
                            lastAutoFailureUntilMs = 0L;
                            autoLoopFastUntilMs = 0L;
                        } else if (!notReady && !inFlightSkipped) {
                            rememberAutoFailure(signature);
                        }
                        host.log((pushed || recentSkipped || inFlightSkipped || notReady) ? Log.INFO : Log.WARN, row.message);
                        delayMs = (!notReady && !inFlightSkipped && !pushed && !recentSkipped)
                            ? AUTO_POLL_ERROR_MS
                            : selectAutoPollDelay(notReady && hasFreshPendingAutoPush(signature), pushed || recentSkipped, inFlightSkipped);
                    } else {
                        delayMs = selectAutoPollDelay(false, false, false);
                    }
                    sleepAutoLoopQuietly(delayMs);
                } catch (Throwable throwable) {
                    host.log(Log.WARN, "auto push loop failed: " + throwable.getMessage());
                    sleepAutoLoopQuietly(AUTO_POLL_ERROR_MS);
                }
            }
            synchronized (DanmuXposedPushCoordinator.this) {
                autoLoopStarted = false;
            }
            host.log(Log.INFO, "xposed auto push loop stopped");
        }, "DanmuAutoPushLoop");
        loop.setDaemon(true);
        loop.start();
        host.log(Log.INFO, "xposed auto push loop started");
    }

    void markActivityResumed(Activity activity) {
        if (activity == null) return;
        foregroundActivityIdentity = System.identityHashCode(activity);
    }

    void markPlaybackActivity(Activity activity) {
        if (activity == null) return;
        int identity = System.identityHashCode(activity);
        playbackActivityVisible = true;
        if (identity != 0 && identity != lastPlaybackActivityIdentity) {
            lastPlaybackActivityIdentity = identity;
            shellMediaReader.resetCache();
            resetAutoSignature("new playback activity");
        }
        autoLoopActivity = new WeakReference<>(activity);
        requestFastAutoPoll("playback activity active");
    }

    void markActivityPaused(Activity activity) {
        if (activity == null) return;
        int identity = System.identityHashCode(activity);
        if (identity == foregroundActivityIdentity) foregroundActivityIdentity = 0;
        if (identity == lastPlaybackActivityIdentity) {
            playbackActivityVisible = false;
            clearPendingAutoPush("");
            wakeAutoLoop();
            host.log(Log.INFO, "playback activity paused; keep successful auto signature");
        }
    }

    void markActivityDestroyed(Activity activity) {
        if (activity == null) return;
        int identity = System.identityHashCode(activity);
        if (identity == foregroundActivityIdentity) foregroundActivityIdentity = 0;
        if (identity == lastPlaybackActivityIdentity) {
            playbackActivityVisible = false;
            autoLoopActivity = null;
            lastPlaybackActivityIdentity = 0;
            shellMediaReader.resetCache();
            resetAutoSignature("playback activity destroyed");
            wakeAutoLoop();
        }
    }

    boolean isActivityActiveForInjection(Activity activity) {
        if (activity == null || activity.isFinishing()) return false;
        if (activity.isDestroyed()) return false;
        if (System.identityHashCode(activity) != foregroundActivityIdentity) return false;
        Window window = activity.getWindow();
        View decor = window == null ? null : window.getDecorView();
        return decor != null && decor.isShown();
    }

    boolean isPlaybackActivityVisible() {
        if (!playbackActivityVisible) return false;
        WeakReference<Activity> ref = autoLoopActivity;
        Activity activity = ref == null ? null : ref.get();
        if (activity == null || activity.isFinishing()) return false;
        if (activity.isDestroyed()) return false;
        int identity = System.identityHashCode(activity);
        return identity == foregroundActivityIdentity && (lastPlaybackActivityIdentity == 0 || identity == lastPlaybackActivityIdentity);
    }

    void requestFastAutoPoll(String reason) {
        long until = System.currentTimeMillis() + AUTO_POLL_FAST_WINDOW_MS;
        if (until > autoLoopFastUntilMs) autoLoopFastUntilMs = until;
        wakeAutoLoop();
    }

    void wakeAutoLoop() {
        synchronized (autoLoopWakeLock) {
            autoLoopWakeLock.notifyAll();
        }
    }

    private void sleepAutoLoopQuietly(long delayMs) {
        try {
            synchronized (autoLoopWakeLock) {
                autoLoopWakeLock.wait(Math.max(0L, delayMs));
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private long selectNoMediaPollDelay() {
        return System.currentTimeMillis() < autoLoopFastUntilMs ? AUTO_POLL_FAST_MS : AUTO_POLL_NO_MEDIA_MS;
    }

    private long selectAutoPollDelay(boolean pendingOrNotReady, boolean stableSuccess, boolean inFlightSkipped) {
        if (pendingOrNotReady || inFlightSkipped) return AUTO_POLL_FAST_MS;
        if (lastAutoSignature == null || lastAutoSignature.isEmpty()) return AUTO_POLL_FAST_MS;
        if (stableSuccess) return AUTO_POLL_STABLE_MS;
        return AUTO_POLL_STABLE_MS;
    }

    private boolean isAutoFailureCoolingDown(String signature) {
        String sig = signature == null ? "" : signature;
        return !sig.isEmpty() && sig.equals(lastAutoFailureSignature) && System.currentTimeMillis() < lastAutoFailureUntilMs;
    }

    private void rememberAutoFailure(String signature) {
        String sig = signature == null ? "" : signature;
        if (sig.isEmpty()) return;
        lastAutoFailureSignature = sig;
        lastAutoFailureUntilMs = System.currentTimeMillis() + AUTO_FAILURE_COOLDOWN_MS;
    }

    private boolean hasFreshPendingAutoPush(String matchSignature) {
        String signature = matchSignature == null ? "" : matchSignature;
        synchronized (autoPlanLock) {
            PendingAutoPush plan = pendingAutoPush;
            return plan != null && signature.equals(plan.matchSignature) && System.currentTimeMillis() - plan.createdAtMs <= AUTO_PENDING_FAST_WINDOW_MS;
        }
    }

    private void resetAutoSignature(String reason) {
        synchronized (this) {
            lastAutoSignature = "";
            lastAutoFailureSignature = "";
            lastAutoFailureUntilMs = 0L;
            playbackSessionSerial++;
        }
        clearPendingAutoPush("");
        cleanupPushGuards(System.currentTimeMillis());
        host.log(Log.INFO, "auto push signature reset: " + reason);
    }

    private PushGuard beginPushGuard(int shellPort, String danmakuUrl) {
        long now = System.currentTimeMillis();
        cleanupPushGuards(now);
        int port = shellPort > 0 && shellPort <= 65535 ? shellPort : 9978;
        String globalKey = port + "|" + (danmakuUrl == null ? "" : danmakuUrl.trim());
        String sessionKey = playbackSessionSerial + "|" + globalKey;
        synchronized (pushGuardLock) {
            Long inFlightAt = inFlightPushes.get(globalKey);
            if (inFlightAt != null && now - inFlightAt < PUSH_IN_FLIGHT_TTL_MS) {
                return new PushGuard(false, globalKey, sessionKey, "inflight");
            }
            Long recentAt = recentPushes.get(sessionKey);
            Long globalRecentAt = recentPushes.get(globalKey);
            if ((recentAt != null && now - recentAt < PUSH_RECENT_TTL_MS) ||
                (globalRecentAt != null && now - globalRecentAt < PUSH_RECENT_TTL_MS)) {
                return new PushGuard(false, globalKey, sessionKey, "recent");
            }
            inFlightPushes.put(globalKey, now);
            return new PushGuard(true, globalKey, sessionKey, "");
        }
    }

    private void finishPushGuard(PushGuard guard, boolean success) {
        if (guard == null || !guard.allowed) return;
        long now = System.currentTimeMillis();
        synchronized (pushGuardLock) {
            inFlightPushes.remove(guard.globalKey);
            if (success) {
                recentPushes.put(guard.sessionKey, now);
                recentPushes.put(guard.globalKey, now);
            } else {
                recentPushes.remove(guard.sessionKey);
            }
        }
    }

    private void cleanupPushGuards(long now) {
        synchronized (pushGuardLock) {
            ArrayList<String> inflightKeys = new ArrayList<>(inFlightPushes.keySet());
            for (String key : inflightKeys) {
                Long startedAt = inFlightPushes.get(key);
                if (startedAt == null || now - startedAt > PUSH_IN_FLIGHT_TTL_MS) inFlightPushes.remove(key);
            }
            ArrayList<String> recentKeys = new ArrayList<>(recentPushes.keySet());
            for (String key : recentKeys) {
                Long pushedAt = recentPushes.get(key);
                if (pushedAt == null || now - pushedAt > PUSH_RECENT_TTL_MS) recentPushes.remove(key);
            }
        }
    }

    private String buildPushLabel(EpisodeCandidate candidate) {
        if (candidate == null) return "";
        String title = normalizeDisplayTitle(candidate.name);
        String episode = candidate.episode == null ? "" : candidate.episode.trim();
        StringBuilder sb = new StringBuilder();
        if (!title.isEmpty()) sb.append("《").append(title).append("》");
        if (!episode.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(episode);
        }
        if (sb.length() == 0) {
            String fallback = joinNonBlank(candidate.name, candidate.episode);
            if (!fallback.isEmpty()) sb.append(fallback);
        }
        return sb.toString();
    }

    private String buildPushPendingMessage(String prefix, String label) {
        String cleanPrefix = prefix == null ? "" : prefix.trim();
        String cleanLabel = label == null ? "" : label.trim();
        if (cleanPrefix.isEmpty()) {
            return cleanLabel.isEmpty() ? "统计弹幕数中…" : cleanLabel + "，统计弹幕数中…";
        }
        return cleanLabel.isEmpty() ? cleanPrefix + "：统计弹幕数中…" : cleanPrefix + "：" + cleanLabel + "，统计弹幕数中…";
    }

    private String buildPushCountMessage(String label, int count) {
        String cleanLabel = label == null ? "" : label.trim();
        String countText = count >= 0 ? count + "条弹幕" : "弹幕数未知";
        if (cleanLabel.isEmpty()) return "已匹配（" + countText + "）";
        return "已匹配" + cleanLabel + "（" + countText + "）";
    }

    private void scheduleDanmakuCountUpdate(Context context, EpisodeCandidate candidate, String danmakuUrl) {
        new Thread(() -> {
            int count = countDanmaku(danmakuUrl);
            String label = buildPushLabel(candidate);
            String message = buildPushCountMessage(label, count);
            recordLastPush(context, message, danmakuUrl);
            notifyAutoPush(message);
        }, "DanmuCountAfterPush").start();
    }

    private void recordLastPush(Context context, String message, String url) {
        lastPushInfo = message == null ? "" : message;
        lastPushUrl = url == null ? "" : url;
        lastPushAtMs = System.currentTimeMillis();
        lastPushSummary = buildPushSummary(message);
        synchronized (pushHistory) {
            pushHistory.addFirst(message == null ? "" : message);
            while (pushHistory.size() > MAX_PUSH_HISTORY) {
                String last = null;
                for (String item : pushHistory) last = item;
                if (last == null) break;
                pushHistory.remove(last);
            }
        }
    }

    private String buildPushSummary(String message) {
        if (message == null || message.trim().isEmpty()) return "";
        String msg = message.trim();
        int colonIdx = msg.indexOf("：");
        if (colonIdx > 0 && colonIdx < msg.length() - 1) {
            String label = msg.substring(colonIdx + 1).trim();
            int parenIdx = label.indexOf("（");
            if (parenIdx > 0) {
                return label.substring(0, parenIdx) + label.substring(parenIdx);
            }
            return label;
        }
        return msg;
    }

    String formatPushTimeChip() {
        if (lastPushAtMs <= 0L || lastPushSummary.isEmpty()) return "暂无推送";
        long agoMs = Math.max(0L, System.currentTimeMillis() - lastPushAtMs);
        long agoSec = agoMs / 1000L;
        if (agoSec < 60L) return "刚刚";
        if (agoSec < 3600L) return (agoSec / 60L) + "分钟前";
        return (agoSec / 3600L) + "小时前";
    }

    String formatLastPushInfo(Context context) {
        String info = lastPushInfo == null ? "" : lastPushInfo.trim();
        if (info.isEmpty()) return "最近：暂无";
        long agoMs = Math.max(0L, System.currentTimeMillis() - lastPushAtMs);
        long agoSec = agoMs / 1000L;
        String ago;
        if (lastPushAtMs <= 0L) ago = "刚刚";
        else if (agoSec < 5L) ago = "刚刚";
        else if (agoSec < 60L) ago = agoSec + "秒前";
        else ago = Math.min(99L, agoSec / 60L) + "分钟前";
        return "最近(" + ago + ")：" + info;
    }

    boolean hasUnviewedPush() {
        return lastPushAtMs > lastViewedPushAtMs && lastPushAtMs > 0L;
    }

    void markPushHistoryViewed() {
        lastViewedPushAtMs = System.currentTimeMillis();
    }

    void showPushHistoryDialog(Activity activity, DanmuTheme t, View notifyButton, TextView notifyDot) {
        lastViewedPushAtMs = System.currentTimeMillis();
        notifyDot.setVisibility(View.GONE);

        Dialog dlg = new Dialog(activity);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, DanmuTheme.SPACE_5), dp(activity, DanmuTheme.SPACE_4),
            dp(activity, DanmuTheme.SPACE_5), dp(activity, DanmuTheme.SPACE_4));
        root.setBackground(t.roundRect(t.sheetBg, DanmuTheme.RADIUS_LG, t.stroke, 1, activity));

        TextView title = DanmuUi.text(activity, t, "推送历史", DanmuTheme.TEXT_TITLE, t.textPrimary, true);
        root.addView(title, matchWrapWithBottom(activity, DanmuTheme.SPACE_4));

        ArrayList<String> entries;
        synchronized (pushHistory) {
            entries = new ArrayList<>(pushHistory);
        }
        if (entries.isEmpty()) {
            LinearLayout empty = DanmuUi.emptyState(activity, t, "暂无推送", "收到推送后会记录在这里");
            root.addView(empty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            for (int i = 0; i < entries.size(); i++) {
                String entry = entries.get(i);
                if (entry == null || entry.trim().isEmpty()) continue;

                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackground(t.roundRect(t.surfaceAlt, DanmuTheme.RADIUS_SM, t.stroke, 1, activity));
                row.setPadding(dp(activity, DanmuTheme.SPACE_3), dp(activity, DanmuTheme.SPACE_3),
                    dp(activity, DanmuTheme.SPACE_3), dp(activity, DanmuTheme.SPACE_3));

                TextView badge = DanmuUi.text(activity, t, String.valueOf(i + 1),
                    DanmuTheme.TEXT_CAPTION, t.accentSoftText, true);
                badge.setBackground(t.roundRect(t.accentSoft, DanmuTheme.RADIUS_SM, activity));
                int badgeSize = dp(activity, 22);
                LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(badgeSize, badgeSize);
                badgeLp.rightMargin = dp(activity, DanmuTheme.SPACE_3);
                badge.setGravity(Gravity.CENTER);
                row.addView(badge, badgeLp);

                TextView tv = DanmuUi.text(activity, t, entry, DanmuTheme.TEXT_BODY, t.textPrimary, false);
                row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowLp.bottomMargin = dp(activity, DanmuTheme.SPACE_2);
                root.addView(row, rowLp);
            }
        }

        Button closeBtn = DanmuUi.ghostButton(activity, t, "关闭");
        LinearLayout.LayoutParams closeBtnLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 36));
        closeBtnLp.topMargin = dp(activity, DanmuTheme.SPACE_4);
        closeBtnLp.gravity = Gravity.END;
        root.addView(closeBtn, closeBtnLp);
        closeBtn.setOnClickListener(v -> dlg.dismiss());

        dlg.setContentView(root);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            int width = activity.getResources().getDisplayMetrics().widthPixels;
            w.setLayout((int) (width * 0.80f), WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
        }
        dlg.show();
    }

    void notifyAutoPush(String message) {
        WeakReference<Activity> ref = autoLoopActivity;
        Activity activity = ref == null ? null : ref.get();
        if (activity == null || activity.isFinishing()) return;
        if (activity.isDestroyed()) return;
        activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
    }

    void stop() {
        synchronized (this) {
            autoLoopStarted = false;
        }
        wakeAutoLoop();
    }

    private void clearPendingAutoPush(String matchSignature) {
        String signature = matchSignature == null ? "" : matchSignature;
        synchronized (autoPlanLock) {
            if (pendingAutoPush == null) return;
            if (signature.isEmpty() || signature.equals(pendingAutoPush.matchSignature)) pendingAutoPush = null;
        }
    }

    private PendingAutoPush getPendingAutoPush(String matchSignature) {
        String signature = matchSignature == null ? "" : matchSignature;
        synchronized (autoPlanLock) {
            PendingAutoPush plan = pendingAutoPush;
            if (plan == null || !signature.equals(plan.matchSignature)) return null;
            return plan;
        }
    }

    private void setPendingAutoPush(PendingAutoPush plan) {
        synchronized (autoPlanLock) {
            pendingAutoPush = plan;
        }
    }

    private boolean hasPendingAutoPush() {
        synchronized (autoPlanLock) {
            return pendingAutoPush != null;
        }
    }

    private String applyDanmakuParams(String danmakuUrl, InjectionSettings settings) throws Exception {
        String url = danmakuUrl == null ? "" : danmakuUrl.trim();
        if (settings == null || url.isEmpty()) return url;
        ArrayList<String> params = new ArrayList<>();
        if (Math.abs(settings.offsetSec) > 1e-6) {
            params.add("offset=" + urlEncode(DanmuXposedTextPolicy.formatOffsetSeconds(settings.offsetSec)));
        }
        if (settings.fontSize > 0) {
            params.add("fontSize=" + settings.fontSize);
        }
        if (params.isEmpty()) return url;
        String cleaned = removeQueryParams(url, "offset", "fontSize");
        String fragment = "";
        int hash = cleaned.indexOf('#');
        if (hash >= 0) {
            fragment = cleaned.substring(hash);
            cleaned = cleaned.substring(0, hash);
        }
        char sep = cleaned.contains("?") ? '&' : '?';
        return cleaned + sep + joinWithAmpersand(params) + fragment;
    }

    private String removeQueryParams(String url, String... names) {
        if (url == null) return "";
        int q = url.indexOf('?');
        if (q < 0) return url;
        String base = url.substring(0, q);
        String query = url.substring(q + 1);
        String fragment = "";
        int hash = query.indexOf('#');
        if (hash >= 0) {
            fragment = query.substring(hash);
            query = query.substring(0, hash);
        }
        StringBuilder kept = new StringBuilder();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            if (pair == null || pair.isEmpty()) continue;
            String key = pair;
            int eq = pair.indexOf('=');
            if (eq >= 0) key = pair.substring(0, eq);
            boolean drop = false;
            for (String name : names) {
                if (name.equalsIgnoreCase(key)) {
                    drop = true;
                    break;
                }
            }
            if (!drop) {
                if (kept.length() > 0) kept.append('&');
                kept.append(pair);
            }
        }
        return kept.length() == 0 ? base + fragment : base + "?" + kept + fragment;
    }

    private String joinWithAmpersand(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;
            if (sb.length() > 0) sb.append('&');
            sb.append(part);
        }
        return sb.toString();
    }

    ShellMedia readShellMedia(int preferredPort) {
        return shellMediaReader.read(preferredPort);
    }

    private String formatError(Throwable throwable) {
        if (throwable == null) return "未知错误";
        String message = throwable.getMessage();
        if (message == null || message.trim().isEmpty()) return throwable.getClass().getSimpleName();
        return throwable.getClass().getSimpleName() + " " + message;
    }

    private LinearLayout.LayoutParams matchWrapWithBottom(Activity activity, int bottomDp) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0, 0, 0, dp(activity, bottomDp)); return lp; }

    private int dp(Activity activity, int value) {
        float density = activity.getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }

    private static final class PushGuard {
        final boolean allowed;
        final String globalKey;
        final String sessionKey;
        final String reason;

        PushGuard(boolean allowed, String globalKey, String sessionKey, String reason) {
            this.allowed = allowed;
            this.globalKey = globalKey;
            this.sessionKey = sessionKey;
            this.reason = reason;
        }
    }
}
