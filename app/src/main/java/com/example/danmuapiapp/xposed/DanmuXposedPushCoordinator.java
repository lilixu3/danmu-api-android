package com.example.danmuapiapp.xposed;

import static com.example.danmuapiapp.xposed.DanmuXposedHttp.buildShellPushUrl;
import static com.example.danmuapiapp.xposed.DanmuXposedHttp.httpGet;
import static com.example.danmuapiapp.xposed.DanmuXposedHttp.isSuccessfulShellPushResponse;
import static com.example.danmuapiapp.xposed.DanmuXposedHttp.prepareDanmaku;
import static com.example.danmuapiapp.xposed.DanmuXposedHttp.urlEncode;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.joinNonBlank;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.normalizeDisplayTitle;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.normalizeSearchTitle;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
    private final PlaybackReadinessGate readinessGate = new PlaybackReadinessGate();

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
    private final Object autoMatchClaimLock = new Object();
    private String autoMatchClaimSignature = "";
    private long autoMatchClaimExpiresAtMs = 0L;
    private volatile String activeAutoMatchSignature = "";
    private volatile long activeAutoMatchGeneration = 0L;
    private volatile long activeAutoMatchObservedAtMs = 0L;
    private final Object pushGuardLock = new Object();
    private volatile String lastPushInfo = "";
    private volatile String lastPushUrl = "";
    private volatile long lastPushAtMs = 0L;
    private volatile long lastViewedPushAtMs = 0L;
    private final LinkedHashMap<String, Long> inFlightPushes = new LinkedHashMap<>();
    private final LinkedHashMap<String, Long> recentPushes = new LinkedHashMap<>();
    private final LinkedList<String> pushHistory = new LinkedList<>();
    private static final int MAX_PUSH_HISTORY = 6;
    private static final String STATUS_NOT_READY = "not_ready";
    private static final String STATUS_SUPERSEDED = "superseded";
    private static final long PUSH_IN_FLIGHT_TTL_MS = 45_000L;
    private static final long PUSH_RECENT_TTL_MS = 8_000L;
    private static final long AUTO_POLL_FAST_MS = 850L;
    private static final long AUTO_POLL_STABLE_MS = 4_000L;
    private static final long AUTO_POLL_NO_MEDIA_MS = 3_000L;
    private static final long AUTO_POLL_DISABLED_MS = 5_000L;
    private static final long AUTO_POLL_ERROR_MS = 2_600L;
    private static final long AUTO_POLL_FAST_WINDOW_MS = 15_000L;
    private static final long AUTO_PENDING_FAST_WINDOW_MS = 20_000L;
    private static final long AUTO_PENDING_TTL_MS = 120_000L;
    private static final long AUTO_FAILURE_COOLDOWN_MS = 15_000L;
    /** 匹配认领租期：覆盖最坏情况的多关键词搜索超时；到期后允许其他线程重新认领，防止永久卡死。 */
    private static final long AUTO_MATCH_CLAIM_TTL_MS = 90_000L;

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

    void pushCandidate(Activity activity, CandidateHandle candidate, int shellPort, PushFeedback feedback) {
        if (candidate == null || candidate.episodeCandidate == null) {
            feedback.onStatus("剧集候选已过期，请重新搜索");
            return;
        }
        feedback.onStatus("正在推送：" + candidate.label);
        new Thread(() -> {
            BridgeRow row = pushEpisodeCandidate(activity.getApplicationContext(), candidate, shellPort);
            activity.runOnUiThread(() -> {
                feedback.onStatus(row.message);
                feedback.onPushInfo(formatLastPushInfo(activity));
                if ("error".equals(row.status)) {
                    Toast.makeText(activity, row.message, Toast.LENGTH_SHORT).show();
                }
            });
        }, "DanmuDirectPush").start();
    }

    BridgeRow pushEpisodeCandidate(Context context, CandidateHandle handle, int shellPort) {
        EpisodeCandidate candidate = episodeRepository.loadEpisodeCandidate(handle);
        if (candidate == null) return new BridgeRow("error", "剧集候选已过期，请重新搜索", "");
        return pushResolvedCandidate(context, candidate, shellPort, "已推送");
    }

    BridgeRow pushResolvedCandidate(Context context, EpisodeCandidate candidate, int shellPort, String prefix) {
        PushGuard guard = null;
        try {
            InjectionSettings settings = host.readInjectionSettings(context, shellPort);
            String sourceDanmakuUrl = applyDanmakuParams(candidate.url, settings);
            int effectivePort = shellPort > 0 && shellPort <= 65535 ? shellPort : settings.shellPort;
            effectivePort = DanmuXposedHostShell.resolve(context, effectivePort).port;
            guard = beginPushGuard(effectivePort, sourceDanmakuUrl);
            if (!guard.allowed) {
                String status = "recent".equals(guard.reason) ? "skip_recent" : "skip_inflight";
                return new BridgeRow(status, "已跳过重复推送：" + candidate.displayLabel(), "");
            }

            PreparedDanmaku prepared;
            try {
                prepared = prepareDanmaku(settings.corePort, sourceDanmakuUrl);
            } catch (Throwable prepareError) {
                throw new IllegalStateException(
                    "弹幕预取失败，已取消宿主推送：" + formatError(prepareError), prepareError);
            }
            String pushDanmakuUrl = prepared.url;

            String pushUrl = buildShellPushUrl(effectivePort, pushDanmakuUrl);
            String pushResponse = httpGet(pushUrl, 1200, 5000);
            if (!isSuccessfulShellPushResponse(pushResponse)) {
                throw new IllegalStateException("宿主刷新未返回 ok：" + summarizeResponse(pushResponse));
            }
            finishPushGuard(guard, true);
            String label = buildPushLabel(candidate);
            String message = buildPushResultMessage(prefix, label, prepared.count);
            recordLastPush(context, message, pushDanmakuUrl);
            notifyAutoPush(message);
            return new BridgeRow("ok", message, pushDanmakuUrl);
        } catch (Throwable throwable) {
            if (guard != null && guard.allowed) finishPushGuard(guard, false);
            return new BridgeRow("error", "推送失败：" + formatError(throwable), "");
        }
    }

    void autoPushCurrent(Activity activity, int fallbackPort, PushFeedback feedback) {
        feedback.onStatus("正在自动匹配当前播放…");
        new Thread(() -> {
            ShellMedia media = readShellMedia(fallbackPort);
            if (media == null || media.title.isEmpty()) {
                activity.runOnUiThread(() -> feedback.onStatus("未读取到当前播放信息，无法自动推送"));
                return;
            }
            BridgeRow row = queryBridgeAutoPush(activity.getApplicationContext(), media.port > 0 ? media : media.withPort(fallbackPort));
            activity.runOnUiThread(() -> {
                feedback.onStatus(row.message);
                feedback.onPushInfo(formatLastPushInfo(activity));
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
            MediaIdentity target = MediaIdentity.from(media);
            AutoMatchToken token = observeAutoMediaSelection(media);
            PendingAutoPush plan = getPendingAutoPush(token);
            if (plan == null) {
                // 去重改为“按签名认领”：同一签名只允许一个线程做匹配，
                // 网络请求全部在锁外执行——此前整个匹配过程持有
                // autoMatchWorkLock，宿主网络差时自动轮询与手动搜索
                // 会互相阻塞数十秒。
                String claimSignature = matchSignature;
                if (!tryClaimAutoMatch(claimSignature)) {
                    return new BridgeRow("skip_inflight", "已有相同的自动匹配在进行", "");
                }
                try {
                    AutoMatchOutcome outcome = matchAndStoreAutoPlan(context, media, target, token, matchSignature);
                    if (outcome.row != null) return outcome.row;
                    plan = outcome.plan;
                } finally {
                    releaseAutoMatchClaim(claimSignature);
                }
            }
            if (!isAutoMatchCurrent(token)) return supersededAutoPushRow();
            if (!readinessGate.isReady(media)) {
                String stateLabel = media.playbackStateLabel();
                String message = "已预匹配：" + plan.candidate.displayLabel() + "，等待播放态推送" + (stateLabel.isEmpty() ? "" : " · " + stateLabel);
                return new BridgeRow(STATUS_NOT_READY, message, plan.candidate.url);
            }
            BridgeRow pushed = pushResolvedCandidate(context, plan.candidate, media.port > 0 ? media.port : plan.shellPort, "自动推送成功");
            if ("ok".equals(pushed.status) || "skip_recent".equals(pushed.status)) {
                clearPendingAutoPush(token);
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
                        invalidateAutoMediaSelection();
                        sleepAutoLoopQuietly(AUTO_POLL_DISABLED_MS);
                        continue;
                    }
                    if (!settings.autoPushEnabled) {
                        lastAutoSignature = "";
                        invalidateAutoMediaSelection();
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
                        boolean superseded = STATUS_SUPERSEDED.equals(row.status);
                        if (pushed || recentSkipped) {
                            lastAutoSignature = signature;
                            lastAutoFailureSignature = "";
                            lastAutoFailureUntilMs = 0L;
                            autoLoopFastUntilMs = 0L;
                        } else if (!notReady && !inFlightSkipped && !superseded) {
                            rememberAutoFailure(signature);
                        }
                        host.log((pushed || recentSkipped || inFlightSkipped || notReady || superseded) ? Log.INFO : Log.WARN, row.message);
                        delayMs = (!notReady && !inFlightSkipped && !pushed && !recentSkipped && !superseded)
                            ? AUTO_POLL_ERROR_MS
                            : selectAutoPollDelay(
                                superseded || (notReady && hasFreshPendingAutoPush(media.matchSignature())),
                                pushed || recentSkipped,
                                inFlightSkipped);
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
            invalidateAutoMediaSelection();
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

    private AutoMatchToken observeAutoMediaSelection(ShellMedia media) {
        String signature = media == null ? "" : media.matchSignature();
        long now = System.currentTimeMillis();
        boolean changed = false;
        AutoMatchToken token;
        synchronized (this) {
            if (!signature.equals(activeAutoMatchSignature)) {
                activeAutoMatchSignature = signature;
                activeAutoMatchGeneration++;
                activeAutoMatchObservedAtMs = now;
                changed = true;
            }
            token = new AutoMatchToken(
                activeAutoMatchSignature, activeAutoMatchGeneration, activeAutoMatchObservedAtMs);
        }
        if (changed) {
            clearPendingAutoPush("");
            readinessGate.reset();
            requestFastAutoPoll("media selection changed");
            host.log(Log.INFO, "auto media selection observed; generation=" + token.generation);
        }
        return token;
    }

    private boolean isAutoMatchCurrent(AutoMatchToken token) {
        if (token == null) return false;
        synchronized (this) {
            return token.generation == activeAutoMatchGeneration &&
                token.matchSignature.equals(activeAutoMatchSignature);
        }
    }

    private boolean isCurrentActivitySelection(AutoMatchToken token, int preferredPort) {
        if (!isAutoMatchCurrent(token)) return false;
        Activity activity = currentActivity();
        if (activity == null) return isAutoMatchCurrent(token);
        if (activity.isFinishing() || activity.isDestroyed()) return false;
        ShellMedia latest = shellMediaReader.read(activity, preferredPort);
        if (latest == null || latest.title.isEmpty()) return false;
        if (!token.matchSignature.equals(latest.matchSignature())) {
            observeAutoMediaSelection(latest);
            return false;
        }
        return isAutoMatchCurrent(token);
    }

    private BridgeRow supersededAutoPushRow() {
        return new BridgeRow(STATUS_SUPERSEDED, "播放选集已变化，已丢弃旧匹配结果", "");
    }

    private boolean tryClaimAutoMatch(String matchSignature) {
        String signature = matchSignature == null ? "" : matchSignature;
        long now = System.currentTimeMillis();
        synchronized (autoMatchClaimLock) {
            if (signature.equals(autoMatchClaimSignature) && now < autoMatchClaimExpiresAtMs) {
                return false;
            }
            autoMatchClaimSignature = signature;
            autoMatchClaimExpiresAtMs = now + AUTO_MATCH_CLAIM_TTL_MS;
            return true;
        }
    }

    private void releaseAutoMatchClaim(String matchSignature) {
        String signature = matchSignature == null ? "" : matchSignature;
        synchronized (autoMatchClaimLock) {
            if (signature.equals(autoMatchClaimSignature)) {
                autoMatchClaimSignature = "";
                autoMatchClaimExpiresAtMs = 0L;
            }
        }
    }

    /**
     * 执行搜索与剧集匹配（全部为锁外网络请求），成功时把计划写入
     * pendingAutoPush；失败/过期时返回对应的反馈行。
     */
    private AutoMatchOutcome matchAndStoreAutoPlan(
        Context context,
        ShellMedia media,
        MediaIdentity target,
        AutoMatchToken token,
        String matchSignature
    ) throws Exception {
        if (!isAutoMatchCurrent(token)) return AutoMatchOutcome.row(supersededAutoPushRow());
        long matchStartedAtMs = System.currentTimeMillis();
        String searchTitle = normalizeSearchTitle(media.title);
        if (searchTitle.isEmpty()) searchTitle = media.title;
        DirectSearch search = episodeRepository.searchAnimeDirect(context, searchTitle);
        if (!isAutoMatchCurrent(token)) return AutoMatchOutcome.row(supersededAutoPushRow());
        if (search.animes.isEmpty()) {
            clearPendingAutoPush(token);
            return AutoMatchOutcome.row(new BridgeRow("error", "自动推送未找到剧名：" + searchTitle, ""));
        }
        EpisodeCandidate selected = episodeRepository.selectAutoEpisodeInSearchOrder(search.animes, target);
        if (!isAutoMatchCurrent(token) || !isCurrentActivitySelection(token, media.port)) {
            return AutoMatchOutcome.row(supersededAutoPushRow());
        }
        if (selected == null) {
            clearPendingAutoPush(token);
            return AutoMatchOutcome.row(new BridgeRow(
                "error",
                "自动推送未找到可确认的同名同季剧集：" + media.title + " " + media.displayEpisode(),
                ""));
        }
        long matchedAtMs = System.currentTimeMillis();
        PendingAutoPush plan = new PendingAutoPush(
            matchSignature, token.generation, selected, media.port,
            token.observedAtMs, matchedAtMs);
        if (!setPendingAutoPush(plan, token)) return AutoMatchOutcome.row(supersededAutoPushRow());
        host.log(Log.INFO, "auto pre-match completed in " +
            Math.max(0L, matchedAtMs - matchStartedAtMs) + "ms; selection age=" +
            Math.max(0L, matchedAtMs - token.observedAtMs) + "ms");
        return AutoMatchOutcome.plan(plan);
    }

    private void invalidateAutoMediaSelection() {
        synchronized (this) {
            if (!activeAutoMatchSignature.isEmpty()) {
                activeAutoMatchSignature = "";
                activeAutoMatchGeneration++;
                activeAutoMatchObservedAtMs = 0L;
            }
        }
        clearPendingAutoPush("");
        readinessGate.reset();
    }

    private boolean hasFreshPendingAutoPush(String matchSignature) {
        String signature = matchSignature == null ? "" : matchSignature;
        AutoMatchToken token;
        synchronized (this) {
            token = new AutoMatchToken(
                activeAutoMatchSignature, activeAutoMatchGeneration, activeAutoMatchObservedAtMs);
        }
        if (!signature.equals(token.matchSignature)) return false;
        long now = System.currentTimeMillis();
        synchronized (autoPlanLock) {
            PendingAutoPush plan = pendingAutoPush;
            if (plan == null) return false;
            if (!plan.isUsable(signature, token.generation, now, AUTO_PENDING_TTL_MS)) {
                pendingAutoPush = null;
                return false;
            }
            return now - plan.createdAtMs <= AUTO_PENDING_FAST_WINDOW_MS;
        }
    }

    private void resetAutoSignature(String reason) {
        synchronized (this) {
            lastAutoSignature = "";
            lastAutoFailureSignature = "";
            lastAutoFailureUntilMs = 0L;
            playbackSessionSerial++;
            activeAutoMatchSignature = "";
            activeAutoMatchGeneration++;
            activeAutoMatchObservedAtMs = 0L;
        }
        clearPendingAutoPush("");
        readinessGate.reset();
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

    private String buildPushResultMessage(String prefix, String label, int count) {
        String cleanPrefix = prefix == null ? "" : prefix.trim();
        String cleanLabel = label == null ? "" : label.trim();
        String base = cleanPrefix.isEmpty() ? "已推送" : cleanPrefix;
        if (!cleanLabel.isEmpty()) base += "：" + cleanLabel;
        return count >= 0 ? base + "（" + count + "条弹幕）" : base;
    }

    private void recordLastPush(Context context, String message, String url) {
        lastPushInfo = message == null ? "" : message;
        lastPushUrl = url == null ? "" : url;
        lastPushAtMs = System.currentTimeMillis();
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

    List<String> pushHistorySnapshot() {
        synchronized (pushHistory) {
            return new ArrayList<>(pushHistory);
        }
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

    private void clearPendingAutoPush(AutoMatchToken token) {
        if (token == null) return;
        synchronized (autoPlanLock) {
            PendingAutoPush plan = pendingAutoPush;
            if (plan != null && token.generation == plan.generation &&
                token.matchSignature.equals(plan.matchSignature)) {
                pendingAutoPush = null;
            }
        }
    }

    private PendingAutoPush getPendingAutoPush(AutoMatchToken token) {
        if (token == null || !isAutoMatchCurrent(token)) return null;
        long now = System.currentTimeMillis();
        synchronized (autoPlanLock) {
            PendingAutoPush plan = pendingAutoPush;
            if (plan == null) return null;
            if (!plan.isUsable(token.matchSignature, token.generation, now, AUTO_PENDING_TTL_MS)) {
                pendingAutoPush = null;
                return null;
            }
            return plan;
        }
    }

    private boolean setPendingAutoPush(PendingAutoPush plan, AutoMatchToken token) {
        if (plan == null || !isAutoMatchCurrent(token)) return false;
        synchronized (autoPlanLock) {
            if (!isAutoMatchCurrent(token)) return false;
            pendingAutoPush = plan;
            return true;
        }
    }

    private boolean hasPendingAutoPush() {
        AutoMatchToken token;
        synchronized (this) {
            token = new AutoMatchToken(
                activeAutoMatchSignature, activeAutoMatchGeneration, activeAutoMatchObservedAtMs);
        }
        long now = System.currentTimeMillis();
        synchronized (autoPlanLock) {
            PendingAutoPush plan = pendingAutoPush;
            if (plan == null) return false;
            if (!plan.isUsable(token.matchSignature, token.generation, now, AUTO_PENDING_TTL_MS)) {
                pendingAutoPush = null;
                return false;
            }
            return true;
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
        return shellMediaReader.read(currentActivity(), preferredPort);
    }

    private String formatError(Throwable throwable) {
        if (throwable == null) return "未知错误";
        String message = throwable.getMessage();
        if (message == null || message.trim().isEmpty()) return throwable.getClass().getSimpleName();
        return throwable.getClass().getSimpleName() + " " + message;
    }

    private String summarizeResponse(String response) {
        String value = response == null ? "空响应" : response.trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) return "空响应";
        return value.length() <= 120 ? value : value.substring(0, 120) + "…";
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

    private static final class AutoMatchToken {
        final String matchSignature;
        final long generation;
        final long observedAtMs;

        AutoMatchToken(String matchSignature, long generation, long observedAtMs) {
            this.matchSignature = matchSignature == null ? "" : matchSignature;
            this.generation = generation;
            this.observedAtMs = observedAtMs;
        }
    }

    private static final class AutoMatchOutcome {
        final PendingAutoPush plan;
        final BridgeRow row;

        private AutoMatchOutcome(PendingAutoPush plan, BridgeRow row) {
            this.plan = plan;
            this.row = row;
        }

        static AutoMatchOutcome plan(PendingAutoPush plan) {
            return new AutoMatchOutcome(plan, null);
        }

        static AutoMatchOutcome row(BridgeRow row) {
            return new AutoMatchOutcome(null, row);
        }
    }
}
