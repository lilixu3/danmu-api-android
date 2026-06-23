package com.example.danmuapiapp.xposed;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.GridLayout;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;

import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam;
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam;

/**
 * LSPosed API 102 target / API 101+ compatible entry point. It runs inside the hooked OK影视/FongMi process.
 */
@SuppressLint("SetTextI18n") // Hook 端注入宿主进程 UI，不需要国际化
public class DanmuXposedModule extends XposedModule {
    private static final String TAG = "DanmuAppXposed";
    private static final String MODULE_PACKAGE = "com.example.danmuapiapp";
    private static final int API_102_VERSION = 102;
    private static final String HOOK_ID_ON_RESUME = "danmuapi.activity.onResume";
    private static final String HOOK_ID_ON_WINDOW_FOCUS_CHANGED = "danmuapi.activity.onWindowFocusChanged";
    private static final String HOOK_ID_ON_PAUSE = "danmuapi.activity.onPause";
    private static final String HOOK_ID_ON_DESTROY = "danmuapi.activity.onDestroy";
    private static final String BUTTON_TAG = "com.example.danmuapiapp.APP_DANMU_BUTTON";
    private static final String SETTINGS_ROW_TAG = "com.example.danmuapiapp.APP_DANMU_SETTINGS_ROW";
    private static final String SETTINGS_OVERLAY_TAG = "com.example.danmuapiapp.APP_DANMU_SETTINGS_OVERLAY";
    private static final long SETTINGS_OVERLAY_NAV_GUARD_INTERVAL_MS = 80L;

    // 设置面板里"播放设置"行容器的资源 id（OK影视/FongMi）；id 命中优先，文字兜底。
    private static final String[] SETTINGS_ROW_ANCHOR_IDS = {"player"};
    private static final String[] SETTINGS_ROW_ANCHOR_TEXTS = {"播放设置"};
    private static final String PREFS_INJECTION = "app_danmu_injection";
    private static final String KEY_INJECTION_ENABLED = "injection_enabled";
    private static final String KEY_AUTO_PUSH_ENABLED = "auto_push_enabled";
    private static final String KEY_CORE_PORT = "core_port";
    private static final String KEY_CORE_TOKEN = "core_token";
    private static final String KEY_OFFSET_SEC = "offset_sec";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_SHELL_PORT = "shell_port";
    private static final String KEY_UI_DARK_THEME = "ui_dark_theme";
    private static final String KEY_EPISODE_SHOW_TITLES = "episode_show_titles";
    private static final String KEY_DIALOG_STYLE = "dialog_style";
    private static final int DIALOG_STYLE_CENTER = 0;
    private static final int DIALOG_STYLE_BOTTOM_SHEET = 1;
    private static final int MODE_ANIME = 1;
    private static final int MODE_EPISODE = 2;
    private static final String STATUS_NOT_READY = "not_ready";
    private static final Pattern PATTERN_SEASON_EPISODE = Pattern.compile("(?i)(?:^|[^a-z0-9])s\\s*0*(\\d{1,2})\\s*e\\s*0*(\\d{1,3})(?:\\D|$)");
    private static final Pattern PATTERN_EXPLICIT_EPISODE = Pattern.compile("(?i)(?:第\\s*([0-9]{1,3}|[一二两三四五六七八九十百零〇]+)\\s*[集期话]|(?:episode|ep|e)\\s*\\.?\\s*0*(\\d{1,3})(?:\\D|$)|[_\\-.\\s\\[]0*(\\d{1,3})(?=[_\\-.\\s\\]集期话]|$))");
    private static final Pattern PATTERN_TRAILING_NUMBER = Pattern.compile("(?:^|[^0-9])0*(\\d{1,3})(?:\\D*)$");
    private static final Pattern PATTERN_YEAR = Pattern.compile("(?<!\\d)(?:19|20)\\d{2}(?!\\d)");
    private static final Pattern PATTERN_BRACKETED = Pattern.compile("[\\[【「『（(](.*?)[\\]】」』）)]");
    private static final Pattern PATTERN_NOISE_WORDS = Pattern.compile("(?i)(全\\s*\\d+\\s*[集期话]|全集|全季|完结|更新至|更至|第\\s*\\d+\\s*[集期话]|s\\s*\\d+\\s*e\\s*\\d+|episode\\s*\\d+|ep\\s*\\d+|e\\s*\\d+|4k|8k|1080p|720p|2160p|hdr|h265|h264|x264|x265|web[- ]?dl|bluray|webrip|国语|国剧|国产|内地|大陆|电视剧|连续剧|剧集|电影|综艺|动漫|动画|中字|字幕|高清|超清|蓝光|资源|网盘|阿里云盘|夸克|百度云|腾讯|爱奇艺|优酷|mkv|mp4|avi)");
    private static final Pattern PATTERN_LEADING_FILE_SIZE = Pattern.compile("^\\s*[\\[【(（]\\s*[0-9]+(?:\\.[0-9]+)?\\s*(?:gb|g|mb|m|kb|k|tb|t)\\s*[\\]】)）]\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_LEADING_EPISODE_FILE = Pattern.compile("^\\s*0*(\\d{1,3})(?=\\s*(?:4k|8k|1080p|720p|2160p|hdr|hevc|h265|h264|x264|x265|\\.|-|_|$))", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_SOURCE_FROM_TITLE = Pattern.compile("(?i)\\bfrom\\s+([a-zA-Z0-9&]+)\\s*$");

    private static final String[] ACTIVITY_HINTS = {
        "video", "player", "playback"
    };
    private static final String[] ANCHOR_TEXTS = {
        "片尾", "弹幕", "弹幕搜索", "选集", "更多", "下集"
    };
    private static final String[] CONTROL_BAR_TEXTS = {
        "EXO", "硬解", "软解", "字幕", "音轨", "视轨", "原始", "刷新", "循环",
        "片头", "片尾", "弹幕", "弹幕搜索", "选集", "更多", "下集"
    };
    private static final String[] SHELL_CONTROL_ANCHOR_IDS = {
        "danmaku", "ending", "episodes", "opening", "video", "audio", "text"
    };
    private static final String[] CONTAINER_ANCHOR_IDS = {
        "bottom"
    };

    private String packageName = "";
    private boolean lifecycleHooked = false;
    private volatile boolean autoLoopStarted = false;
    private volatile boolean moduleGenerationActive = true;
    private volatile boolean playbackActivityVisible = false;
    private volatile int foregroundActivityIdentity = 0;
    private volatile long autoLoopFastUntilMs = 0L;
    private volatile int cachedShellMediaPort = -1;
    private volatile int cachedShellMediaMisses = 0;
    private volatile long cachedShellMediaPortUpdatedAtMs = 0L;
    private volatile String lastAutoFailureSignature = "";
    private volatile long lastAutoFailureUntilMs = 0L;
    private final Object autoLoopWakeLock = new Object();
    private volatile String lastAutoSignature = "";
    private volatile int lastPlaybackActivityIdentity = 0;
    private volatile long playbackSessionSerial = 0L;
    private volatile WeakReference<Activity> autoLoopActivity = null;
    private volatile PendingAutoPush pendingAutoPush = null;
    private final Object autoPlanLock = new Object();
    private final List<XposedInterface.HookHandle> activityHookHandles = Collections.synchronizedList(new ArrayList<>());
    private final Map<Integer, android.view.ViewTreeObserver.OnGlobalLayoutListener> injectionWatchers = new ConcurrentHashMap<>();
    private final Map<String, Boolean> settingsOverlayBackHooks = new ConcurrentHashMap<>();
    private volatile String cachedCoreBase = "";
    private volatile String lastPushInfo = "";
    private volatile String lastPushUrl = "";
    private volatile long lastPushAtMs = 0L;
    private volatile long lastViewedPushAtMs = 0L;
    private final java.util.LinkedList<String> pushHistory = new java.util.LinkedList<>();
    private static final int MAX_PUSH_HISTORY = 6;
    private volatile String lastPushSummary = "";
    private final Object pushGuardLock = new Object();
    private final Map<String, AnimeRef> animeRefs = new ConcurrentHashMap<>();
    private final Map<String, EpisodeCandidate> episodeCandidates = new ConcurrentHashMap<>();
    private final Map<String, Long> inFlightPushes = new ConcurrentHashMap<>();
    private final Map<String, Long> recentPushes = new ConcurrentHashMap<>();
    private static final int MAX_RETURN_ANIMES = 60;
    private static final int MAX_RETURN_EPISODES = 180;
    private static final int MAX_AUTO_DETAIL_REQUESTS = 8;
    private static final int MAX_COMPACT_EPISODES = 40;
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
    private static final long AUTO_MEDIA_PORT_CACHE_TTL_MS = 30_000L;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "module loaded in " + param.getProcessName());
    }

    @Override
    public boolean onHotReloading(HotReloadingParam param) {
        try {
            moduleGenerationActive = false;
            Activity currentActivity = null;
            WeakReference<Activity> activityRef = autoLoopActivity;
            if (activityRef != null) currentActivity = activityRef.get();
            if (currentActivity != null) clearInjectionWatch(currentActivity);
            playbackActivityVisible = false;
            autoLoopActivity = null;
            wakeAutoLoop();
            Bundle outState = new Bundle();
            outState.putString("packageName", packageName == null ? "" : packageName);
            outState.putString("lastAutoSignature", lastAutoSignature == null ? "" : lastAutoSignature);
            outState.putString("lastPushInfo", lastPushInfo == null ? "" : lastPushInfo);
            outState.putLong("lastPushAtMs", lastPushAtMs);
            outState.putLong("playbackSessionSerial", playbackSessionSerial);
            param.setSavedInstanceState(outState);
            log(Log.INFO, TAG, "api102 hot reload accepted; old generation is stopping");
            return true;
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "api102 hot reload prepare failed: " + throwable.getMessage());
            return false;
        }
    }

    @Override
    public void onHotReloaded(HotReloadedParam param) {
        moduleGenerationActive = true;
        restoreHotReloadState(param);
        unhookOldHotReloadHandles(param);
        if (MODULE_PACKAGE.equals(packageName)) {
            log(Log.INFO, TAG, "api102 hot reloaded in module app; skip target hooks");
            return;
        }
        lifecycleHooked = false;
        try {
            hookActivityLifecycle();
            lifecycleHooked = true;
            log(Log.INFO, TAG, "api102 hot reloaded in " + param.getProcessName() + "; activity lifecycle re-hooked");
        } catch (Throwable throwable) {
            log(Log.ERROR, TAG, "api102 hot reload re-hook failed", throwable);
        }
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        packageName = param.getPackageName();
        if (!MODULE_PACKAGE.equals(packageName)) {
            log(Log.INFO, TAG, "package loaded: " + packageName);
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String readyPackage = param.getPackageName();
        packageName = readyPackage == null ? "" : readyPackage;
        if (MODULE_PACKAGE.equals(packageName) || lifecycleHooked) return;
        try {
            hookActivityLifecycle();
            lifecycleHooked = true;
            log(Log.INFO, TAG, "activity lifecycle hooked for " + packageName);
        } catch (Throwable throwable) {
            log(Log.ERROR, TAG, "failed to hook activity lifecycle", throwable);
        }
    }

    private void hookActivityLifecycle() throws NoSuchMethodException {
        activityHookHandles.clear();
        Method onResume = Activity.class.getDeclaredMethod("onResume");
        installActivityHook(onResume, HOOK_ID_ON_RESUME, chain -> {
            Object result = chain.proceed();
            Object thisObject = chain.getThisObject();
            if (thisObject instanceof Activity) {
                markActivityResumed((Activity) thisObject);
                scheduleInject((Activity) thisObject);
            }
            return result;
        });

        Method onWindowFocusChanged = Activity.class.getDeclaredMethod("onWindowFocusChanged", boolean.class);
        installActivityHook(onWindowFocusChanged, HOOK_ID_ON_WINDOW_FOCUS_CHANGED, chain -> {
            Object result = chain.proceed();
            Object thisObject = chain.getThisObject();
            Object hasFocus = chain.getArg(0);
            if (thisObject instanceof Activity && Boolean.TRUE.equals(hasFocus)) {
                markActivityResumed((Activity) thisObject);
                scheduleInject((Activity) thisObject);
            }
            return result;
        });

        Method onPause = Activity.class.getDeclaredMethod("onPause");
        installActivityHook(onPause, HOOK_ID_ON_PAUSE, chain -> {
            Object result = chain.proceed();
            Object thisObject = chain.getThisObject();
            if (thisObject instanceof Activity) {
                markActivityPaused((Activity) thisObject);
            }
            return result;
        });

        Method onDestroy = Activity.class.getDeclaredMethod("onDestroy");
        installActivityHook(onDestroy, HOOK_ID_ON_DESTROY, chain -> {
            Object result = chain.proceed();
            Object thisObject = chain.getThisObject();
            if (thisObject instanceof Activity) {
                markActivityDestroyed((Activity) thisObject);
            }
            return result;
        });
    }

    private XposedInterface.HookHandle installActivityHook(Method method, String hookId, XposedInterface.Hooker hooker) {
        XposedInterface.HookBuilder builder = hook(method);
        if (getApiVersion() >= API_102_VERSION) {
            try {
                builder.setId(hookId);
            } catch (Throwable throwable) {
                log(Log.WARN, TAG, "api102 hook id unavailable for " + hookId + ": " + throwable.getMessage());
            }
        }
        XposedInterface.HookHandle handle = builder.intercept(hooker);
        activityHookHandles.add(handle);
        return handle;
    }

    private void restoreHotReloadState(HotReloadedParam param) {
        try {
            if (getApiVersion() < API_102_VERSION) return;
            Object state = param.getSavedInstanceState();
            if (!(state instanceof Bundle)) return;
            Bundle bundle = (Bundle) state;
            packageName = bundle.getString("packageName", packageName == null ? "" : packageName);
            lastAutoSignature = bundle.getString("lastAutoSignature", "");
            lastPushInfo = bundle.getString("lastPushInfo", "");
            lastPushAtMs = bundle.getLong("lastPushAtMs", 0L);
            playbackSessionSerial = bundle.getLong("playbackSessionSerial", playbackSessionSerial);
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "api102 hot reload state restore failed: " + throwable.getMessage());
        }
    }

    private void unhookOldHotReloadHandles(HotReloadedParam param) {
        try {
            if (getApiVersion() < API_102_VERSION) return;
            List<XposedInterface.HookHandle> oldHandles = param.getOldHookHandles();
            if (oldHandles == null) return;
            for (XposedInterface.HookHandle handle : oldHandles) {
                if (handle == null) continue;
                try {
                    handle.unhook();
                } catch (Throwable throwable) {
                    log(Log.WARN, TAG, "api102 old hook unhook failed: " + throwable.getMessage());
                }
            }
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "api102 old hook cleanup failed: " + throwable.getMessage());
        }
    }

    private void scheduleInject(Activity activity) {
        try {
            if (activity == null) return;
            installInjectionWatch(activity);
            Window window = activity.getWindow();
            if (window == null) return;
            View decor = window.getDecorView();
            if (decor == null) return;
            decor.post(() -> {
                injectButton(activity);
                injectSettingsRow(activity);
            });
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "schedule inject failed: " + throwable.getMessage());
        }
    }


    private void installInjectionWatch(Activity activity) {
        try {
            if (activity == null) return;
            int identity = System.identityHashCode(activity);
            if (injectionWatchers.containsKey(identity)) return;
            Window window = activity.getWindow();
            if (window == null) return;
            View decor = window.getDecorView();
            if (decor == null) return;
            android.view.ViewTreeObserver observer = decor.getViewTreeObserver();
            if (observer == null || !observer.isAlive()) return;
            android.view.ViewTreeObserver.OnGlobalLayoutListener listener = new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    try {
                        if (activity.isFinishing() || activity.isDestroyed()) {
                            clearInjectionWatch(activity);
                            return;
                        }
                        Window currentWindow = activity.getWindow();
                        View currentDecor = currentWindow == null ? null : currentWindow.getDecorView();
                        if (!(currentDecor instanceof ViewGroup)) return;
                        ViewGroup currentGroup = (ViewGroup) currentDecor;
                        if (findTaggedButton(currentGroup) != null) {
                            startAutoPushLoopOnce(activity);
                            clearInjectionWatch(activity);
                            return;
                        }
                        injectButton(activity);
                        injectSettingsRow(activity);
                        if (findTaggedButton(currentGroup) != null) {
                            startAutoPushLoopOnce(activity);
                            clearInjectionWatch(activity);
                        }
                    } catch (Throwable throwable) {
                        log(Log.WARN, TAG, "injection layout watch failed: " + throwable.getMessage());
                    }
                }
            };
            injectionWatchers.put(identity, listener);
            observer.addOnGlobalLayoutListener(listener);
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "install injection watch failed: " + throwable.getMessage());
        }
    }

    private void clearInjectionWatch(Activity activity) {
        try {
            if (activity == null) return;
            int identity = System.identityHashCode(activity);
            android.view.ViewTreeObserver.OnGlobalLayoutListener listener = injectionWatchers.remove(identity);
            if (listener == null) return;
            Window window = activity.getWindow();
            if (window == null) return;
            View decor = window.getDecorView();
            if (decor == null) return;
            android.view.ViewTreeObserver observer = decor.getViewTreeObserver();
            if (observer != null && observer.isAlive()) {
                observer.removeOnGlobalLayoutListener(listener);
            }
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "clear injection watch failed: " + throwable.getMessage());
        }
    }


    private void injectButton(Activity activity) {
        try {
            if (activity.isFinishing()) return;
            if (activity.isDestroyed()) return;
            if (!isActivityActiveForInjection(activity)) {
                return;
            }

            Window window = activity.getWindow();
            if (window == null) return;
            View decorView = window.getDecorView();
            if (!(decorView instanceof ViewGroup)) return;
            ViewGroup decor = (ViewGroup) decorView;
            InjectionSettings injectSettings = readInjectionSettings(activity, 9978);
            View existingButton = findTaggedButton(decor);
            if (!injectSettings.injectionEnabled) {
                if (existingButton != null && existingButton.getParent() instanceof ViewGroup) {
                    ((ViewGroup) existingButton.getParent()).removeView(existingButton);
                }
                clearInjectionWatch(activity);
                return;
            }
            if (existingButton != null) {
                startAutoPushLoopOnce(activity);
                clearInjectionWatch(activity);
                return;
            }

            Anchor anchor = findAnchor(decor);
            if (!looksLikePlaybackPage(activity, decor, anchor)) {
                return;
            }

            if (anchor == null || anchor.parent == null) {
                startAutoPushLoopOnce(activity);
                log(Log.WARN, TAG, "playback page found but no control-bar anchor yet");
                return;
            }

            View button = createButton(activity, anchor.view);
            button.setOnClickListener(v -> showManualSearchDialog(activity));
            button.setOnLongClickListener(v -> {
                Toast.makeText(activity, "APP弹幕：自动推送已随 LSPosed 启用，点击可手动搜索", Toast.LENGTH_SHORT).show();
                return true;
            });
            if (addButtonToDecor(decor, button, anchor)) {
                attachButtonReinjectGuard(activity, button);
                log(Log.INFO, TAG, "APP danmu button injected into " + activity.getClass().getName());
                clearInjectionWatch(activity);
            }
            startAutoPushLoopOnce(activity);
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "inject button failed: " + throwable.getMessage());
        }
    }


    private void attachButtonReinjectGuard(Activity activity, View button) {
        if (activity == null || button == null) return;
        button.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                try {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    Window window = activity.getWindow();
                    View decor = window == null ? null : window.getDecorView();
                    if (decor != null) {
                        decor.postDelayed(() -> scheduleInject(activity), 120L);
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    // 把"APP弹幕设置"作为一行注入到宿主设置面板（HomeActivity 里"播放设置"行后面）。
    // 识别：先按行容器资源 id 命中，兜底按文字"播放设置"上溯到可点击行；两条都要求该行含锚点文字。
    // 样式：直接克隆"播放设置"行的真实背景 Drawable（渐变一起带过来），不自己调色。
    private void injectSettingsRow(Activity activity) {
        try {
            if (!isActivityActiveForInjection(activity)) return;
            Window window = activity.getWindow();
            if (window == null) return;
            View decorView = window.getDecorView();
            if (!(decorView instanceof ViewGroup)) return;
            ViewGroup decor = (ViewGroup) decorView;

            InjectionSettings injectSettings = readInjectionSettings(activity, 9978);
            View existing = findTaggedView(decor, SETTINGS_ROW_TAG);
            if (!injectSettings.injectionEnabled) {
                if (existing != null && existing.getParent() instanceof ViewGroup) {
                    ((ViewGroup) existing.getParent()).removeView(existing);
                }
                return;
            }
            if (existing != null) return;

            View anchorRow = findSettingsAnchorRow(decor);
            if (anchorRow == null || !(anchorRow.getParent() instanceof ViewGroup)) return;
            ViewGroup parent = (ViewGroup) anchorRow.getParent();

            View newRow = createSettingsRow(activity, anchorRow);
            int index = parent.indexOfChild(anchorRow);
            parent.addView(newRow, index >= 0 ? Math.min(index + 1, parent.getChildCount()) : parent.getChildCount());
            log(Log.INFO, TAG, "APP danmu settings row injected into " + activity.getClass().getName());
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "inject settings row failed: " + throwable.getMessage());
        }
    }

    private View findSettingsAnchorRow(ViewGroup decor) {
        Context ctx = decor.getContext();
        String pkg = ctx == null ? "" : ctx.getPackageName();
        for (String name : SETTINGS_ROW_ANCHOR_IDS) {
            try {
                int id = decor.getResources().getIdentifier(name, "id", pkg);
                if (id == 0) continue;
                View found = decor.findViewById(id);
                if (found == null || found.getVisibility() != View.VISIBLE || !found.isShown()) continue;
                if (rowContainsAnchorText(found)) return found;
            } catch (Throwable ignored) {
            }
        }
        TextView label = findVisibleAnchorTextView(decor);
        if (label != null) {
            View row = climbToClickableRow(label);
            if (row != null) return row;
        }
        return null;
    }

    private boolean matchesSettingsAnchorText(String text) {
        if (text == null || text.isEmpty()) return false;
        for (String anchor : SETTINGS_ROW_ANCHOR_TEXTS) {
            if (anchor.equals(text)) return true;
        }
        return false;
    }

    private boolean rowContainsAnchorText(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) return false;
        if (view instanceof TextView && matchesSettingsAnchorText(readViewText(view))) return true;
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (rowContainsAnchorText(g.getChildAt(i))) return true;
            }
        }
        return false;
    }

    private TextView findVisibleAnchorTextView(View root) {
        if (root == null || root.getVisibility() != View.VISIBLE || !root.isShown()) return null;
        if (root instanceof TextView) {
            return matchesSettingsAnchorText(readViewText(root)) ? (TextView) root : null;
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView found = findVisibleAnchorTextView(g.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private View climbToClickableRow(View label) {
        View current = label;
        for (int depth = 0; depth < 6 && current != null; depth++) {
            if (current.isClickable() && current instanceof ViewGroup && current.getParent() instanceof ViewGroup) {
                return current;
            }
            ViewParent p = current.getParent();
            current = (p instanceof View) ? (View) p : null;
        }
        if (label.getParent() instanceof View && ((View) label.getParent()).getParent() instanceof ViewGroup) {
            return (View) label.getParent();
        }
        return null;
    }

    private View createSettingsRow(Activity activity, View anchorRow) {
        TextView anchorLabel = findFirstTextView(anchorRow);

        LinearLayout row = new LinearLayout(activity);
        row.setTag(SETTINGS_ROW_TAG);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setPadding(anchorRow.getPaddingLeft(), anchorRow.getPaddingTop(),
            anchorRow.getPaddingRight(), anchorRow.getPaddingBottom());
        Drawable clonedBg = cloneDrawable(anchorRow.getBackground());
        if (clonedBg != null) row.setBackground(clonedBg);
        int minH = anchorRow.getHeight();
        if (minH > 0) row.setMinimumHeight(minH);

        TextView label = new TextView(activity);
        label.setText("APP弹幕");
        label.setSingleLine(true);
        if (anchorLabel != null) {
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, anchorLabel.getTextSize());
            label.setTextColor(anchorLabel.getTextColors());
            label.setTypeface(anchorLabel.getTypeface());
            label.setGravity(anchorLabel.getGravity());
            label.setIncludeFontPadding(anchorLabel.getIncludeFontPadding());
            label.setPadding(anchorLabel.getPaddingLeft(), anchorLabel.getPaddingTop(),
                anchorLabel.getPaddingRight(), anchorLabel.getPaddingBottom());
        }
        ViewGroup.LayoutParams labelLp = anchorLabel != null && anchorLabel.getLayoutParams() != null
            ? cloneRowLayoutParams(anchorLabel.getLayoutParams())
            : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row.addView(label, labelLp);

        ViewGroup.LayoutParams rowLp = anchorRow.getLayoutParams() != null
            ? cloneRowLayoutParams(anchorRow.getLayoutParams())
            : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(rowLp);

        int port = readInjectionSettings(activity, 9978).shellPort;
        row.setOnClickListener(v -> showInjectionSettingsDialog(activity, anchorRow, new int[]{port}));
        return row;
    }


    private TextView findFirstTextView(View root) {
        if (root instanceof TextView) return (TextView) root;
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView found = findFirstTextView(g.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private ViewGroup.LayoutParams cloneRowLayoutParams(ViewGroup.LayoutParams source) {
        if (source instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams src = (LinearLayout.LayoutParams) source;
            LinearLayout.LayoutParams out = new LinearLayout.LayoutParams(src.width, src.height);
            out.setMargins(src.leftMargin, src.topMargin, src.rightMargin, src.bottomMargin);
            out.gravity = src.gravity;
            out.weight = src.weight;
            return out;
        }
        if (source instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams src = (FrameLayout.LayoutParams) source;
            FrameLayout.LayoutParams out = new FrameLayout.LayoutParams(src.width, src.height);
            out.gravity = src.gravity;
            out.setMargins(src.leftMargin, src.topMargin, src.rightMargin, src.bottomMargin);
            return out;
        }
        if (source instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams src = (ViewGroup.MarginLayoutParams) source;
            ViewGroup.MarginLayoutParams out = new ViewGroup.MarginLayoutParams(src.width, src.height);
            out.setMargins(src.leftMargin, src.topMargin, src.rightMargin, src.bottomMargin);
            return out;
        }
        return new ViewGroup.LayoutParams(source.width, source.height);
    }

    private View findTaggedView(View root, String tag) {
        if (root == null) return null;
        if (tag.equals(root.getTag())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findTaggedView(group.getChildAt(i), tag);
                if (found != null) return found;
            }
        }
        return null;
    }

    // Search stage indices for the segmented navigation.
    private static final int STAGE_SEARCH = 0;
    private static final int STAGE_DRAMA = 1;
    private static final int STAGE_EPISODE = 2;

    private void showManualSearchDialog(Activity activity) {
        try {
            InjectionSettings bootSettings = readInjectionSettings(activity, 9978);
            final int[] shellPort = new int[]{bootSettings.shellPort};
            final DanmuTheme t = DanmuTheme.of(bootSettings.darkTheme);
            final boolean isCenter = bootSettings.dialogStyle == DIALOG_STYLE_CENTER;

            // ---- Sheet surface ----
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            int padH = dp(activity, DanmuTheme.SPACE_4);
            int padTop = isCenter ? dp(activity, DanmuTheme.SPACE_4) : dp(activity, DanmuTheme.SPACE_3);
            int padBottom = dp(activity, DanmuTheme.SPACE_4);
            root.setPadding(padH, padTop, padH, padBottom);

            if (!isCenter) {
                root.addView(DanmuUi.dragHandle(activity, t), DanmuUi.dragHandleLp(activity, t));
            }

            // ---- Header: compact single-line top bar ----
            final int[] stage = new int[]{STAGE_SEARCH};
            final int[] reachable = new int[]{STAGE_SEARCH};
            final Runnable[] renderContent = new Runnable[1];
            final Runnable[] applyStageStatus = new Runnable[1];
            final String[] searchMessage = new String[]{""};
            final String[] episodeMessage = new String[]{""};
            final String[] currentDramaTitle = new String[]{""};

            LinearLayout header = new LinearLayout(activity);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(activity, DanmuTheme.SPACE_2), dp(activity, 4), dp(activity, 6), dp(activity, 4));
            header.setBackground(t.roundRect(t.surface, DanmuTheme.RADIUS_LG, t.stroke, 1, activity));

            TextView brandMark = DanmuUi.text(activity, t, "弹幕", DanmuTheme.TEXT_CAPTION, t.accentSoftText, true);
            brandMark.setGravity(Gravity.CENTER);
            brandMark.setPadding(dp(activity, DanmuTheme.SPACE_2), 0, dp(activity, DanmuTheme.SPACE_2), 0);
            brandMark.setBackground(t.roundRect(t.accentSoft, DanmuTheme.RADIUS_PILL, activity));
            LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 28));
            brandLp.setMargins(0, 0, dp(activity, DanmuTheme.SPACE_2), 0);
            header.addView(brandMark, brandLp);

            TextView headerTitleText = DanmuUi.text(activity, t, headerStageTitle(STAGE_SEARCH), DanmuTheme.TEXT_LABEL, t.textPrimary, true);
            headerTitleText.setSingleLine(true);
            headerTitleText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            header.addView(headerTitleText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView pushSummaryText = DanmuUi.text(activity, t, formatPushTimeChip(), DanmuTheme.TEXT_CAPTION, t.textMuted, false);
            pushSummaryText.setSingleLine(true);
            pushSummaryText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            pushSummaryText.setGravity(Gravity.CENTER);
            pushSummaryText.setPadding(dp(activity, DanmuTheme.SPACE_2), 0, dp(activity, DanmuTheme.SPACE_2), 0);
            pushSummaryText.setBackground(t.roundRect(t.surfaceAlt, DanmuTheme.RADIUS_PILL, t.stroke, 1, activity));
            LinearLayout.LayoutParams summaryLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 28));
            summaryLp.setMargins(dp(activity, DanmuTheme.SPACE_2), 0, 0, 0);
            header.addView(pushSummaryText, summaryLp);

            Button notifyButton = DanmuUi.ghostButton(activity, t, "↺");
            notifyButton.setTextSize(DanmuTheme.TEXT_LABEL);
            LinearLayout.LayoutParams notifyLp = new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32));
            notifyLp.setMargins(dp(activity, DanmuTheme.SPACE_2), 0, 0, 0);
            final TextView[] notifyDot = new TextView[1];
            notifyDot[0] = new TextView(activity);
            notifyDot[0].setText("");
            notifyDot[0].setGravity(Gravity.CENTER);
            notifyDot[0].setBackground(t.roundRect(t.danger, DanmuTheme.RADIUS_PILL, activity));

            final Runnable updateNotifyDot = () -> {
                if (lastPushAtMs > lastViewedPushAtMs && lastPushAtMs > 0L) {
                    notifyDot[0].setVisibility(View.VISIBLE);
                } else {
                    notifyDot[0].setVisibility(View.GONE);
                }
            };
            updateNotifyDot.run();

            FrameLayout notifyWrapper = new FrameLayout(activity);
            notifyWrapper.addView(notifyButton, new FrameLayout.LayoutParams(
                dp(activity, 32), dp(activity, 32), Gravity.CENTER));
            FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(
                dp(activity, 7), dp(activity, 7), Gravity.TOP | Gravity.END);
            dotLp.topMargin = dp(activity, 5);
            dotLp.rightMargin = dp(activity, 5);
            notifyWrapper.addView(notifyDot[0], dotLp);
            notifyButton.setOnClickListener(v -> showPushHistoryDialog(activity, t, notifyButton, notifyDot[0]));

            Button closeButton = DanmuUi.ghostButton(activity, t, "×");
            closeButton.setTextSize(DanmuTheme.TEXT_TITLE);
            LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32));
            closeLp.setMargins(dp(activity, DanmuTheme.SPACE_1), 0, 0, 0);
            header.addView(notifyWrapper, notifyLp);
            header.addView(closeButton, closeLp);
            root.addView(header, matchWrapWithBottom(activity, DanmuTheme.SPACE_2));

            // ---- Search row: keyword + 搜索 + 推送 ----
            LinearLayout searchRow = new LinearLayout(activity);
            searchRow.setOrientation(LinearLayout.HORIZONTAL);
            searchRow.setGravity(Gravity.CENTER_VERTICAL);
            EditText keywordInput = DanmuUi.textField(activity, t, "输入剧名 / 自动读取当前播放", "");
            Button searchButton = DanmuUi.primaryButton(activity, t, "搜索");
            Button actionButton = DanmuUi.primaryButton(activity, t, "推送");
            searchRow.addView(keywordInput, new LinearLayout.LayoutParams(0, dp(activity, 44), 1f));
            LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(dp(activity, 64), dp(activity, 44));
            searchLp.setMargins(dp(activity, DanmuTheme.SPACE_2), 0, 0, 0);
            searchRow.addView(searchButton, searchLp);
            LinearLayout.LayoutParams pushLp = new LinearLayout.LayoutParams(dp(activity, 64), dp(activity, 44));
            pushLp.setMargins(dp(activity, DanmuTheme.SPACE_2), 0, 0, 0);
            searchRow.addView(actionButton, pushLp);
            root.addView(searchRow, matchWrapWithBottom(activity, DanmuTheme.SPACE_2));

            // pushInfoText 保留为隐藏引用，用于接收推送更新
            TextView pushInfoText = DanmuUi.chip(activity, t, "", true);
            pushInfoText.setVisibility(View.GONE);

            // ---- Content area: keep the newer taller sheet, but compute chrome for the compact
            // one-line header so the inner result ScrollView owns overflow instead of the window. ---
            int screenH = activity.getResources().getDisplayMetrics().heightPixels;
            int sheetTarget = (int) (screenH * (isCenter ? 0.88f : 0.90f));
            int searchChrome = dp(activity, 28 + 16 + 50 + 52 + 12 + 24);
            int contentHeightPx = clamp(sheetTarget - searchChrome, dp(activity, 260), dp(activity, 900));
            FrameLayout contentFrame = new FrameLayout(activity);
            LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, contentHeightPx);
            contentLp.setMargins(0, 0, 0, dp(activity, DanmuTheme.SPACE_3));
            root.addView(contentFrame, contentLp);

            // stage 0: search prompt / empty state
            FrameLayout promptHolder = new FrameLayout(activity);
            promptHolder.addView(DanmuUi.emptyState(activity, t, "输入剧名后开始搜索",
                "也可直接打开，会自动读取当前播放并预填"),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            contentFrame.addView(promptHolder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // stage 1: drama list + source filters. Keep filters outside the vertical list so they
            // stay above results, and use a horizontal scroller instead of wrapping chips.
            LinearLayout resultsSection = new LinearLayout(activity);
            resultsSection.setOrientation(LinearLayout.VERTICAL);
            HorizontalScrollView platformFilterScroll = buildHorizontalChipScroll(activity);
            LinearLayout platformFilterRow = new LinearLayout(activity);
            platformFilterRow.setOrientation(LinearLayout.HORIZONTAL);
            platformFilterRow.setGravity(Gravity.CENTER_VERTICAL);
            platformFilterScroll.addView(platformFilterRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            resultsSection.addView(platformFilterScroll, matchWrapWithBottom(activity, DanmuTheme.SPACE_2));

            ScrollView resultsScroll = buildSheetScroll(activity);
            LinearLayout resultsContainer = new LinearLayout(activity);
            resultsContainer.setOrientation(LinearLayout.VERTICAL);
            resultsScroll.addView(resultsContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            resultsSection.addView(resultsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            contentFrame.addView(resultsSection, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // stage 2: episode section (toolbar + grid)
            LinearLayout episodeSection = new LinearLayout(activity);
            episodeSection.setOrientation(LinearLayout.VERTICAL);

            final boolean[] showTitles = new boolean[]{readEpisodeShowTitles(activity)};
            final ArrayList<TextView> episodeItemViews = new ArrayList<>();
            final int[] gridMetrics = new int[]{9, 40};

            LinearLayout episodeToolbar = new LinearLayout(activity);
            episodeToolbar.setOrientation(LinearLayout.HORIZONTAL);
            episodeToolbar.setGravity(Gravity.CENTER_VERTICAL);
            TextView episodeCountText = DanmuUi.text(activity, t, "", DanmuTheme.TEXT_BODY, t.textSecondary, true);
            episodeCountText.setSingleLine(true);
            episodeCountText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            Button numberModeButton = DanmuUi.toggleChip(activity, t, "数字", !showTitles[0]);
            Button titleModeButton = DanmuUi.toggleChip(activity, t, "标题", showTitles[0]);
            Button episodeBackButton = DanmuUi.ghostButton(activity, t, "返回");
            episodeToolbar.addView(episodeCountText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams numLp = new LinearLayout.LayoutParams(dp(activity, 52), dp(activity, 32));
            episodeToolbar.addView(numberModeButton, numLp);
            LinearLayout.LayoutParams titLp = new LinearLayout.LayoutParams(dp(activity, 52), dp(activity, 32));
            titLp.setMargins(dp(activity, DanmuTheme.SPACE_2), 0, 0, 0);
            episodeToolbar.addView(titleModeButton, titLp);
            LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dp(activity, 52), dp(activity, 32));
            backLp.setMargins(dp(activity, DanmuTheme.SPACE_2), 0, 0, 0);
            episodeToolbar.addView(episodeBackButton, backLp);
            episodeSection.addView(episodeToolbar, matchWrapWithBottom(activity, DanmuTheme.SPACE_2));

            ScrollView episodeScroll = buildSheetScroll(activity);
            GridLayout episodeGrid = new GridLayout(activity);
            episodeGrid.setColumnCount(9);
            episodeGrid.setUseDefaultMargins(false);
            episodeGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
            ArrayList<CandidateHandle> compactHandles = new ArrayList<>();
            episodeScroll.addView(episodeGrid, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            episodeSection.addView(episodeScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            contentFrame.addView(episodeSection, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // statusText 保留为隐藏引用，部分方法需要写入状态信息
            TextView statusText = new TextView(activity);
            statusText.setVisibility(View.GONE);

            final ArrayList<String> animeLabels = new ArrayList<>();
            final ArrayList<CandidateHandle> animeHandles = new ArrayList<>();
            final ArrayList<SourceFilter> sourceFilters = new ArrayList<>();
            final String[] selectedSource = new String[]{""};
            final boolean[] searching = new boolean[]{false};
            final int[] mode = new int[]{MODE_ANIME};
            final String[] currentEpisode = new String[]{""};
            final int[] selectedEpisodeIndex = new int[]{0};

            final Runnable refreshEpisodeHeader = () -> {
                if (compactHandles.isEmpty()) {
                    episodeCountText.setText("");
                    return;
                }
                int sel = clamp(selectedEpisodeIndex[0], 0, compactHandles.size() - 1);
                String dramaTitle = currentDramaTitle[0];
                String count = compactHandles.size() + " 集";
                String display = dramaTitle.isEmpty() ? "共 " + count : dramaTitle + "  共" + count;
                episodeCountText.setText(display);
            };

            final Runnable renderEpisodeGrid = () -> {
                renderEpisodeGrid(activity, t, episodeGrid, compactHandles, episodeItemViews, selectedEpisodeIndex,
                    showTitles[0], shellPort[0], statusText, actionButton, pushInfoText, gridMetrics, refreshEpisodeHeader);
                refreshEpisodeHeader.run();
            };

            // Single source of truth: shows the right stage view + refreshes compact header copy.
            renderContent[0] = () -> {
                reachable[0] = Math.max(reachable[0], stage[0]);
                if (!animeLabels.isEmpty()) reachable[0] = Math.max(reachable[0], STAGE_DRAMA);
                if (!compactHandles.isEmpty()) reachable[0] = Math.max(reachable[0], STAGE_EPISODE);
                headerTitleText.setText(headerStageTitle(stage[0]));
                pushSummaryText.setText(formatPushTimeChip());
                promptHolder.setVisibility(stage[0] == STAGE_SEARCH ? View.VISIBLE : View.GONE);
                resultsSection.setVisibility(stage[0] == STAGE_DRAMA ? View.VISIBLE : View.GONE);
                episodeSection.setVisibility(stage[0] == STAGE_EPISODE ? View.VISIBLE : View.GONE);
                actionButton.setText("推送");
            };

            applyStageStatus[0] = () -> {
                if (stage[0] == STAGE_EPISODE) {
                    statusText.setText(episodeMessage[0]);
                } else {
                    statusText.setText(searchMessage[0]);
                }
            };

            View.OnClickListener modeToggle = v -> {
                boolean wantTitles = v == titleModeButton;
                if (wantTitles == showTitles[0]) return;
                showTitles[0] = wantTitles;
                saveEpisodeShowTitles(activity, wantTitles);
                DanmuUi.styleToggleChip(activity, t, numberModeButton, !wantTitles);
                DanmuUi.styleToggleChip(activity, t, titleModeButton, wantTitles);
                renderEpisodeGrid.run();
                scrollEpisodeGridToIndex(activity, episodeScroll, selectedEpisodeIndex[0], gridMetrics[0], gridMetrics[1]);
            };
            numberModeButton.setOnClickListener(modeToggle);
            titleModeButton.setOnClickListener(modeToggle);
            episodeBackButton.setOnClickListener(v -> {
                stage[0] = STAGE_DRAMA;
                renderContent[0].run();
                applyStageStatus[0].run();
            });

            final Runnable[] renderDramaList = new Runnable[1];
            renderDramaList[0] = () -> {
                resultsContainer.removeAllViews();
                renderPlatformFilters(activity, t, platformFilterRow, sourceFilters, selectedSource[0], source -> {
                    selectedSource[0] = source == null ? "" : source;
                    renderDramaList[0].run();
                    platformFilterScroll.post(() -> {
                        if (selectedSource[0].isEmpty()) platformFilterScroll.smoothScrollTo(0, 0);
                    });
                });
                platformFilterScroll.setVisibility(sourceFilters.isEmpty() ? View.GONE : View.VISIBLE);
                if (searching[0]) {
                    resultsContainer.addView(DanmuUi.emptyState(activity, t, "搜索中…", "正在向弹幕核心查询，请稍候"),
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    return;
                }
                if (animeLabels.isEmpty()) {
                    resultsContainer.addView(DanmuUi.emptyState(activity, t, "无剧名结果", "换个关键词再搜一次"),
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    return;
                }
                int visibleIndex = 0;
                for (int i = 0; i < animeLabels.size(); i++) {
                    final CandidateHandle candidate = i < animeHandles.size() ? animeHandles.get(i) : null;
                    if (candidate != null && !selectedSource[0].isEmpty() && !selectedSource[0].equals(candidate.source)) {
                        continue;
                    }
                    visibleIndex++;
                    String[] parts = splitDramaLabel(animeLabels.get(i));
                    LinearLayout row = DanmuUi.listRow(activity, t, String.valueOf(visibleIndex), parts[0], parts[1]);
                    row.setOnClickListener(v -> {
                        if (candidate == null) return;
                        currentDramaTitle[0] = parts[0];
                        loadAnimeDetailIntoSheet(activity, candidate, currentEpisode[0], episodeScroll,
                            compactHandles, selectedEpisodeIndex, mode, searchButton, statusText, pushInfoText,
                            renderEpisodeGrid, gridMetrics, stage, renderContent[0], episodeMessage,
                            currentDramaTitle);
                    });
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.bottomMargin = dp(activity, DanmuTheme.SPACE_2);
                    resultsContainer.addView(row, lp);
                }
                if (visibleIndex == 0) {
                    selectedSource[0] = "";
                    renderDramaList[0].run();
                    return;
                }
                resultsScroll.post(() -> resultsScroll.scrollTo(0, 0));
            };

            if (isCenter) {
                root.setBackground(t.roundRect(t.sheetBg, DanmuTheme.RADIUS_SHEET, activity));
            } else {
                root.setBackground(t.topRoundedSheet(activity));
            }

            AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(root)
                .create();
            closeButton.setOnClickListener(v -> dialog.dismiss());

            final Runnable searchAction = () -> {
                String keyword = keywordInput.getText() == null ? "" : keywordInput.getText().toString().trim();
                if (keyword.isEmpty()) {
                    Toast.makeText(activity, "先输入剧名", Toast.LENGTH_SHORT).show();
                    return;
                }
                mode[0] = MODE_ANIME;
                compactHandles.clear();
                episodeItemViews.clear();
                selectedEpisodeIndex[0] = 0;
                episodeGrid.removeAllViews();
                animeLabels.clear();
                animeHandles.clear();
                sourceFilters.clear();
                selectedSource[0] = "";
                stage[0] = STAGE_DRAMA;
                searching[0] = true;
                searchMessage[0] = "搜索中…";
                statusText.setText("搜索中…");
                searchButton.setEnabled(false);
                renderDramaList[0].run();
                renderContent[0].run();
                new Thread(() -> {
                    BridgeResult result = queryBridgeAnimeSearch(activity, keyword);
                    activity.runOnUiThread(() -> {
                        searching[0] = false;
                        searchButton.setEnabled(true);
                        searchMessage[0] = result.message;
                        if (stage[0] != STAGE_EPISODE) statusText.setText(result.message);
                        animeLabels.clear();
                        animeHandles.clear();
                        sourceFilters.clear();
                        if (result.ok) {
                            animeHandles.addAll(result.candidates);
                            sourceFilters.addAll(result.filters);
                            for (CandidateHandle candidate : result.candidates) {
                                animeLabels.add(candidate.label);
                            }
                        }
                        renderDramaList[0].run();
                        renderContent[0].run();
                    });
                }, "DanmuSearchAnime").start();
            };

            searchButton.setOnClickListener(v -> searchAction.run());
            actionButton.setOnClickListener(v -> {
                if (mode[0] == MODE_EPISODE && !compactHandles.isEmpty()) {
                    int index = clamp(selectedEpisodeIndex[0], 0, compactHandles.size() - 1);
                    pushCandidate(activity, compactHandles.get(index), shellPort[0], statusText, pushInfoText);
                } else {
                    autoPushCurrent(activity, shellPort[0], statusText, pushInfoText);
                }
            });

            dialog.setOnShowListener(d -> {
                Window window = dialog.getWindow();
                if (window != null) {
                    int width = activity.getResources().getDisplayMetrics().widthPixels;
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    if (isCenter) {
                        window.setGravity(Gravity.CENTER);
                    } else {
                        window.setGravity(Gravity.BOTTOM);
                    }
                    window.setLayout((int) (width * 0.82f), ViewGroup.LayoutParams.WRAP_CONTENT);
                }
                // Opening the player popup means the latest push has been seen; refresh the
                // relative-time label and clear stale notification state immediately.
                lastViewedPushAtMs = System.currentTimeMillis();
                renderContent[0].run();
                pushSummaryText.setText(formatPushTimeChip());
                updateNotifyDot.run();
                pushInfoText.setText(formatLastPushInfo(activity));
                new Thread(() -> {
                    ShellMedia media = readShellMedia(shellPort[0]);
                    activity.runOnUiThread(() -> {
                        pushSummaryText.setText(formatPushTimeChip());
                        updateNotifyDot.run();
                        pushInfoText.setText(formatLastPushInfo(activity));
                        if (media != null) {
                            shellPort[0] = media.port;
                            currentEpisode[0] = media.displayEpisode();
                            if (keywordInput.getText() == null || keywordInput.getText().toString().trim().isEmpty()) {
                                String filled = normalizeDisplayTitle(media.title).isEmpty() ? media.title : normalizeDisplayTitle(media.title);
                                keywordInput.setText(filled);
                                keywordInput.setSelection(keywordInput.getText().length());
                            }
                            if (!media.title.isEmpty()) searchAction.run();
                        }
                    });
                }, "DanmuReadMedia").start();
            });
            dialog.show();
        } catch (Throwable throwable) {
            Toast.makeText(activity, "打开 APP弹幕 搜索失败：" + throwable.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
            log(Log.ERROR, TAG, "show manual search dialog failed", throwable);
        }
    }


    /** A scroll container styled as a sheet panel surface. */
    private ScrollView buildSheetScroll(Activity activity) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setScrollbarFadingEnabled(false);
        scroll.setSmoothScrollingEnabled(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        scroll.setOnTouchListener((v, event) -> {
            ViewParent parent = v.getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
            return false;
        });
        return scroll;
    }

    /** Splits a joined drama label ("title · count · source · type") into title + meta line. */
    private String[] splitDramaLabel(String label) {
        String value = label == null ? "" : label.trim();
        int sep = value.indexOf(" · ");
        if (sep < 0) return new String[]{value, ""};
        return new String[]{value.substring(0, sep), value.substring(sep + 3)};
    }

    private void loadAnimeDetailIntoSheet(
        Activity activity, CandidateHandle anime, String episodeHint, ScrollView episodeScroll,
        ArrayList<CandidateHandle> compactHandles, final int[] selectedEpisodeIndex, int[] mode,
        Button searchButton, TextView statusText, TextView pushInfoText, Runnable renderEpisodeGrid,
        int[] gridMetrics, final int[] stage, Runnable renderContent, String[] episodeMessage,
        String[] currentDramaTitle
    ) {
        statusText.setText("正在加载剧集：" + anime.label);
        searchButton.setEnabled(false);
        new Thread(() -> {
            BridgeResult result = loadAnimeDetailDirect(anime.handle, episodeHint);
            activity.runOnUiThread(() -> {
                searchButton.setEnabled(true);
                statusText.setText(result.message);
                if (result.ok && !result.candidates.isEmpty()) {
                    episodeMessage[0] = result.message;
                    mode[0] = MODE_EPISODE;
                    compactHandles.clear();
                    compactHandles.addAll(result.candidates);
                    int targetIndex = clamp(result.selectedIndex, 0, result.candidates.size() - 1);
                    selectedEpisodeIndex[0] = targetIndex;
                    pushInfoText.setText(formatLastPushInfo(activity));
                    renderEpisodeGrid.run();
                    stage[0] = STAGE_EPISODE;
                    renderContent.run();
                    scrollEpisodeGridToIndex(activity, episodeScroll, targetIndex, gridMetrics[0], gridMetrics[1]);
                } else {
                    mode[0] = MODE_ANIME;
                    selectedEpisodeIndex[0] = 0;
                    compactHandles.clear();
                    renderEpisodeGrid.run();
                    renderContent.run();
                }
            });
        }, "DanmuAnimeDetail").start();
    }

    private BridgeResult queryBridgeAnimeSearch(Activity activity, String title) {
        BridgeResult result = new BridgeResult();
        String keyword = normalizeSearchTitle(title);
        if (keyword.isEmpty()) keyword = title == null ? "" : title.trim();
        if (keyword.isEmpty()) {
            result.ok = false;
            result.message = "先输入剧名";
            return result;
        }
        try {
            DirectSearch search = searchAnimeDirect(activity, keyword);
            result.ok = true;
            result.message = search.animes.isEmpty()
                ? "未搜到，可换关键词"
                : "搜索到 " + search.animes.size() + " 个结果，点剧名展开";
            animeRefs.clear();
            for (int i = 0; i < Math.min(search.animes.size(), MAX_RETURN_ANIMES); i++) {
                AnimeRef anime = search.animes.get(i);
                String handle = UUID.randomUUID().toString();
                animeRefs.put(handle, anime);
                String countLabel = anime.episodeCount > 0 ? "共" + anime.episodeCount + "集" : "";
                String sourceLabel = displaySourceName(anime.source);
                String titleLabel = normalizeDisplayTitle(anime.title);
                if (titleLabel.isEmpty()) titleLabel = anime.title;
                String label = joinNonBlank(titleLabel, countLabel, sourceLabel, anime.type);
                if (label.isEmpty()) label = "剧名 " + (result.candidates.size() + 1);
                result.candidates.add(new CandidateHandle(MODE_ANIME, handle, label, anime.source));
            }
            result.filters.addAll(buildSourceFilters(result.candidates));
        } catch (Throwable throwable) {
            result.ok = false;
            result.message = "弹幕 API 核心搜索失败：" + formatError(throwable);
        }
        return result;
    }

    private BridgeResult loadAnimeDetailDirect(String animeHandle, String episodeHint) {
        BridgeResult result = new BridgeResult();
        AnimeRef anime = animeRefs.get(animeHandle == null ? "" : animeHandle.trim());
        if (anime == null) {
            result.ok = false;
            result.message = "剧名候选已过期，请重新搜索";
            return result;
        }
        try {
            int targetEpisodeNumber = extractEpisodeNumber(episodeHint);
            List<EpisodeCandidate> episodes = rankCandidates(
                dedupeCandidates(loadEpisodesForAnime(anime, targetEpisodeNumber)),
                targetEpisodeNumber
            );
            result.ok = true;
            int selectedIndex = findEpisodeIndex(episodes, targetEpisodeNumber);
            result.selectedIndex = selectedIndex;
            result.message = episodes.isEmpty()
                ? "《" + anime.title + "》暂无可推送剧集"
                : "已展开《" + anime.title + "》，默认选中当前播放" + selectedEpisodeText(episodes, selectedIndex, targetEpisodeNumber) + "，上下滑动查看更多";
            episodeCandidates.clear();
            for (int i = 0; i < Math.min(episodes.size(), MAX_RETURN_EPISODES); i++) {
                EpisodeCandidate episode = episodes.get(i);
                String handle = UUID.randomUUID().toString();
                episodeCandidates.put(handle, episode);
                String label = joinNonBlank(episode.episode, episode.name, episode.source);
                if (label.isEmpty()) label = "剧集 " + (result.candidates.size() + 1);
                result.candidates.add(new CandidateHandle(MODE_EPISODE, handle, label));
            }
        } catch (Throwable throwable) {
            result.ok = false;
            result.message = "弹幕 API 核心详情失败：" + formatError(throwable);
        }
        return result;
    }

    private void pushCandidate(Activity activity, CandidateHandle candidate, int shellPort, TextView statusText, TextView pushInfoText) {
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

    private BridgeRow pushEpisodeCandidate(Context context, String handle, int shellPort) {
        EpisodeCandidate candidate = episodeCandidates.get(handle == null ? "" : handle.trim());
        if (candidate == null) return new BridgeRow("error", "剧集候选已过期，请重新搜索", "");
        return pushResolvedCandidate(context, candidate, shellPort, "已推送");
    }

    private BridgeRow pushResolvedCandidate(Context context, EpisodeCandidate candidate, int shellPort, String prefix) {
        PushGuard guard = null;
        try {
            InjectionSettings settings = readInjectionSettings(context, shellPort);
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


    private void scheduleDanmakuCountUpdate(Context context, EpisodeCandidate candidate, String danmakuUrl) {
        new Thread(() -> {
            int count = countDanmaku(danmakuUrl);
            String label = buildPushLabel(candidate);
            String message = buildPushCountMessage(label, count);
            recordLastPush(context, message, danmakuUrl);
            notifyAutoPush(message);
        }, "DanmuCountAfterPush").start();
    }

    private void autoPushCurrent(Activity activity, int fallbackPort, TextView statusText, TextView pushInfoText) {
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

    private BridgeRow queryBridgeAutoPush(Context context, ShellMedia media) {
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
                DirectSearch search = searchAnimeDirect(context, searchTitle);
                if (search.animes.isEmpty()) {
                    clearPendingAutoPush(matchSignature);
                    return new BridgeRow("error", "自动推送未找到剧名：" + searchTitle, "");
                }
                EpisodeCandidate selected = selectAutoEpisodeInSearchOrder(search.animes, targetEpisodeNumber);
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

    private void clearPendingAutoPush(String matchSignature) {
        String signature = matchSignature == null ? "" : matchSignature;
        synchronized (autoPlanLock) {
            if (pendingAutoPush == null) return;
            if (signature.isEmpty() || signature.equals(pendingAutoPush.matchSignature)) pendingAutoPush = null;
        }
    }

    private DirectSearch searchAnimeDirect(Context context, String keyword) throws Exception {
        Exception last = null;
        List<String> keywords = buildSearchKeywords(keyword);
        ArrayList<AnimeRef> lastEmpty = null;
        for (String coreBase : coreBaseCandidates(context)) {
            for (String searchKey : keywords) {
                try {
                    String url = coreBase + "/api/v2/search/anime?keyword=" + urlEncode(searchKey);
                    List<AnimeRef> animes = parseAnimeSearch(coreBase, httpGet(url, 1800, 20000));
                    if (!animes.isEmpty()) {
                        cachedCoreBase = coreBase;
                        return new DirectSearch(coreBase, animes);
                    }
                    lastEmpty = new ArrayList<>(animes);
                } catch (Exception e) {
                    last = e;
                }
            }
        }
        if (lastEmpty != null) return new DirectSearch("", lastEmpty);
        throw last == null ? new IllegalStateException("App 端未同步弹幕 API 核心端口，请打开影视壳注入设置页重新检查") : last;
    }

    private List<String> buildSearchKeywords(String raw) {
        ArrayList<String> out = new ArrayList<>();
        String cleaned = normalizeSearchTitle(raw);
        addKeyword(out, cleaned);
        String display = normalizeDisplayTitle(raw);
        addKeyword(out, display);
        String original = raw == null ? "" : raw.trim();
        if (original.length() <= 32) addKeyword(out, original);
        if (out.isEmpty()) addKeyword(out, original);
        return out;
    }

    private void addKeyword(ArrayList<String> out, String value) {
        if (value == null) return;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return;
        if (!out.contains(trimmed)) out.add(trimmed);
    }

    private List<String> coreBaseCandidates(Context context) throws Exception {
        ArrayList<String> bases = new ArrayList<>();
        InjectionSettings settings = readInjectionSettings(context, 9978);
        if (settings.corePort <= 0 || settings.corePort > 65535) {
            throw new IllegalStateException("App 端未同步弹幕 API 核心端口，请打开影视壳注入设置页重新检查");
        }
        String base = "http://127.0.0.1:" + settings.corePort;
        String configured = settings.coreToken.isEmpty() ? base : base + "/" + urlEncode(settings.coreToken);
        if (cachedCoreBase != null && !cachedCoreBase.trim().isEmpty() && cachedCoreBase.startsWith(base)) {
            addUnique(bases, cachedCoreBase.trim());
        }
        addUnique(bases, configured);
        return bases;
    }

    private void addUnique(ArrayList<String> values, String value) {
        if (!values.contains(value)) values.add(value);
    }

    private List<EpisodeCandidate> loadEpisodesForAnime(AnimeRef anime, int targetEpisodeNumber) throws Exception {
        String id = anime.animeId.isEmpty() ? anime.bangumiId : anime.animeId;
        if (id.isEmpty()) return new ArrayList<>();
        String raw = httpGet(anime.coreBase + "/api/v2/bangumi/" + urlEncode(id), 1800, 20000);
        return parseBangumiCandidates(anime.coreBase, raw, anime, targetEpisodeNumber);
    }

    private List<AnimeRef> parseAnimeSearch(String coreBase, String raw) throws Exception {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) return new ArrayList<>();
        JSONArray array;
        if (trimmed.startsWith("[")) {
            array = new JSONArray(trimmed);
        } else {
            JSONObject root = new JSONObject(trimmed);
            array = root.optJSONArray("animes");
            if (array == null) array = root.optJSONArray("data");
            if (array == null) array = root.optJSONArray("items");
            if (array == null) array = root.optJSONArray("list");
            if (array == null) array = root.optJSONArray("results");
            if (array == null) array = new JSONArray();
        }
        ArrayList<AnimeRef> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String animeId = readString(item, "animeId", "id");
            String bangumiId = readString(item, "bangumiId", "bangumi_id");
            if (animeId.isEmpty() && bangumiId.isEmpty()) continue;
            String name = readString(item, "animeTitle", "title", "name", "anime", "vod_name");
            if (name.isEmpty()) name = "候选 " + (result.size() + 1);
            String sourceRaw = extractSourceFromTitle(name);
            if (sourceRaw.isEmpty()) sourceRaw = readString(item, "source", "sourceName", "provider", "api", "from");
            String source = normalizeSourceKey(sourceRaw);
            String type = readString(item, "typeDescription", "type");
            int episodeCount = Math.max(0, readInt(item, "episodeCount", "totalEpisodes", "count"));
            result.add(new AnimeRef(coreBase, animeId, bangumiId, name, episodeCount, source, type));
        }
        return result;
    }

    private List<EpisodeCandidate> parseBangumiCandidates(String coreBase, String raw, AnimeRef anime, int targetEpisodeNumber) throws Exception {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) return new ArrayList<>();
        JSONObject root = new JSONObject(trimmed);
        if (root.has("success") && !root.optBoolean("success", false)) return new ArrayList<>();
        JSONObject bangumi = root.optJSONObject("bangumi");
        if (bangumi == null) bangumi = root;
        String animeTitle = readString(bangumi, "animeTitle", "title", "name", "anime", "vod_name");
        if (animeTitle.isEmpty()) animeTitle = anime.title;
        String source = anime.source.isEmpty() ? anime.type : anime.source;
        JSONArray episodes = bangumi.optJSONArray("episodes");
        if (episodes == null) episodes = bangumi.optJSONArray("episodeList");
        if (episodes == null) episodes = bangumi.optJSONArray("items");
        if (episodes == null) episodes = bangumi.optJSONArray("list");
        if (episodes == null) return new ArrayList<>();
        ArrayList<EpisodeCandidate> result = new ArrayList<>();
        for (int i = 0; i < episodes.length(); i++) {
            JSONObject ep = episodes.optJSONObject(i);
            if (ep == null) continue;
            String directUrl = readString(ep, "url", "path", "danmaku", "danmu");
            String episodeId = readString(ep, "episodeId", "id", "commentId", "episode_id");
            String url;
            if (directUrl.startsWith("http://") || directUrl.startsWith("https://")) {
                url = directUrl;
            } else if (!episodeId.isEmpty()) {
                url = coreBase + "/api/v2/comment/" + urlEncode(episodeId) + "?format=xml";
            } else {
                continue;
            }
            String episodeNumber = readString(ep, "episodeNumber", "episodeNo", "episodeIndex", "number", "sort");
            String episodeTitle = readString(ep, "episodeTitle", "title", "name", "episode", "episodeName", "remark", "remarks");
            int parsedEpisodeNumber = extractEpisodeNumber(episodeNumber);
            if (parsedEpisodeNumber <= 0) parsedEpisodeNumber = extractEpisodeNumber(episodeTitle);
            String episodeLabel = buildEpisodeLabel(episodeNumber, episodeTitle, targetEpisodeNumber);
            result.add(new EpisodeCandidate(animeTitle, episodeLabel, parsedEpisodeNumber, source, url));
        }
        return result;
    }

    private String buildEpisodeLabel(String episodeNumber, String episodeTitle, int targetEpisodeNumber) {
        ArrayList<String> parts = new ArrayList<>();
        int number = extractEpisodeNumber(episodeNumber);
        if (number > 0) parts.add("第" + number + "集");
        if (episodeTitle != null && !episodeTitle.trim().isEmpty() && (number <= 0 || !episodeTitle.trim().equals(String.valueOf(number)))) {
            parts.add(episodeTitle.trim());
        }
        if (parts.isEmpty() && targetEpisodeNumber > 0) parts.add("第" + targetEpisodeNumber + "集");
        return joinNonBlank(parts.toArray(new String[0]));
    }

    private EpisodeCandidate selectAutoEpisodeInSearchOrder(List<AnimeRef> animes, int targetEpisodeNumber) throws Exception {
        EpisodeCandidate firstFallback = null;
        int limit = Math.min(animes == null ? 0 : animes.size(), MAX_AUTO_DETAIL_REQUESTS);
        for (int i = 0; i < limit; i++) {
            List<EpisodeCandidate> episodes = rankCandidates(
                dedupeCandidates(loadEpisodesForAnime(animes.get(i), targetEpisodeNumber)),
                targetEpisodeNumber
            );
            if (episodes.isEmpty()) continue;
            EpisodeCandidate selected = selectBestEpisode(episodes, targetEpisodeNumber);
            if (firstFallback == null) firstFallback = selected;
            if (targetEpisodeNumber <= 0 || scoreEpisode(selected, targetEpisodeNumber) >= 3) {
                return selected;
            }
        }
        return firstFallback;
    }

    private int findEpisodeIndex(List<EpisodeCandidate> episodes, int targetEpisodeNumber) {
        if (episodes == null || episodes.isEmpty()) return 0;
        if (targetEpisodeNumber <= 0) return 0;
        int bestIndex = 0;
        int bestScore = -1;
        for (int i = 0; i < episodes.size(); i++) {
            int score = scoreEpisode(episodes.get(i), targetEpisodeNumber);
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
            if (score >= 3) return i;
        }
        return bestIndex;
    }

    private String selectedEpisodeText(List<EpisodeCandidate> episodes, int selectedIndex, int targetEpisodeNumber) {
        if (episodes == null || episodes.isEmpty()) return targetEpisodeNumber > 0 ? "第" + targetEpisodeNumber + "集" : "剧集";
        int index = clamp(selectedIndex, 0, episodes.size() - 1);
        String label = shortEpisodeText(episodes.get(index));
        return label.isEmpty() ? "剧集" : label;
    }

    private String shortEpisodeText(EpisodeCandidate episode) {
        if (episode == null) return "";
        int number = episodeSortNumber(episode);
        if (number != Integer.MAX_VALUE) return "第" + number + "集";
        return episode.episode == null ? "" : episode.episode;
    }

    private List<EpisodeCandidate> dedupeCandidates(List<EpisodeCandidate> input) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        ArrayList<EpisodeCandidate> out = new ArrayList<>();
        for (EpisodeCandidate candidate : input) {
            if (seen.add(candidate.url)) out.add(candidate);
        }
        return out;
    }

    private List<EpisodeCandidate> rankCandidates(List<EpisodeCandidate> candidates, int targetEpisodeNumber) {
        candidates.sort((a, b) -> {
            int an = episodeSortNumber(a);
            int bn = episodeSortNumber(b);
            if (an != bn) return Integer.compare(an, bn);
            return a.displayLabel().compareTo(b.displayLabel());
        });
        return candidates;
    }

    private EpisodeCandidate selectBestEpisode(List<EpisodeCandidate> candidates, int targetEpisodeNumber) {
        if (candidates == null || candidates.isEmpty()) return null;
        if (targetEpisodeNumber <= 0) return candidates.get(0);
        EpisodeCandidate best = null;
        int bestScore = -1;
        for (EpisodeCandidate candidate : candidates) {
            int score = scoreEpisode(candidate, targetEpisodeNumber);
            if (score > bestScore || (score == bestScore && episodeSortNumber(candidate) < episodeSortNumber(best))) {
                best = candidate;
                bestScore = score;
            }
        }
        return best == null ? candidates.get(0) : best;
    }

    private int episodeSortNumber(EpisodeCandidate candidate) {
        if (candidate == null) return Integer.MAX_VALUE;
        if (candidate.episodeNumber > 0) return candidate.episodeNumber;
        int parsed = extractEpisodeNumber(candidate.episode);
        return parsed > 0 ? parsed : Integer.MAX_VALUE;
    }

    private int scoreEpisode(EpisodeCandidate candidate, int targetEpisodeNumber) {
        if (candidate == null || targetEpisodeNumber <= 0) return 0;
        if (isTrailerText(candidate.episode) || isTrailerText(candidate.name)) return 0;
        int parsed = episodeSortNumber(candidate);
        if (parsed == targetEpisodeNumber) return 4;
        if (episodeMatches(candidate.episode, targetEpisodeNumber)) return 3;
        if (episodeMatches(candidate.displayLabel(), targetEpisodeNumber)) return 2;
        return 0;
    }

    private boolean episodeMatches(String text, int targetEpisodeNumber) {
        if (text == null || targetEpisodeNumber <= 0 || isTrailerText(text)) return false;
        String target = String.valueOf(targetEpisodeNumber);
        String normalized = text.trim();
        if (normalized.isEmpty()) return false;
        String two = targetEpisodeNumber < 10 ? "0" + targetEpisodeNumber : target;
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("_" + target + "_") || lower.contains("_" + two + "_") ||
            lower.contains("_" + target + ".") || lower.contains("_" + two + ".")) return true;
        int parsed = extractEpisodeNumber(normalized);
        return parsed == targetEpisodeNumber;
    }

    private int extractEpisodeNumber(String raw) {
        if (raw == null) return -1;
        String text = raw.trim();
        if (text.isEmpty()) return -1;
        String strippedFileMeta = stripLeadingFileSize(text);
        Matcher leadingEpisodeFile = PATTERN_LEADING_EPISODE_FILE.matcher(strippedFileMeta);
        if (leadingEpisodeFile.find()) return safeEpisodeNumber(leadingEpisodeFile.group(1));
        Matcher seasonEpisode = PATTERN_SEASON_EPISODE.matcher(strippedFileMeta);
        if (seasonEpisode.find()) return safeEpisodeNumber(seasonEpisode.group(2));
        Matcher episodeFileAfterSize = PATTERN_LEADING_EPISODE_FILE.matcher(text);
        if (episodeFileAfterSize.find()) return safeEpisodeNumber(episodeFileAfterSize.group(1));
        Matcher explicit = PATTERN_EXPLICIT_EPISODE.matcher(strippedFileMeta);
        while (explicit.find()) {
            for (int i = 1; i <= explicit.groupCount(); i++) {
                String group = explicit.group(i);
                int value = parseEpisodeToken(group);
                if (value > 0) return value;
            }
        }
        String withoutNoise = PATTERN_NOISE_WORDS.matcher(strippedFileMeta).replaceAll(" ");
        Matcher trailing = PATTERN_TRAILING_NUMBER.matcher(withoutNoise);
        int fallback = -1;
        while (trailing.find()) {
            int value = safeEpisodeNumber(trailing.group(1));
            if (value > 0) fallback = value;
        }
        return fallback;
    }

    private String stripLeadingFileSize(String text) {
        if (text == null) return "";
        String stripped = PATTERN_LEADING_FILE_SIZE.matcher(text.trim()).replaceFirst("");
        return stripped.trim();
    }

    private int parseEpisodeToken(String raw) {
        if (raw == null) return -1;
        String token = raw.trim();
        if (token.isEmpty()) return -1;
        int numeric = safeEpisodeNumber(token);
        if (numeric > 0) return numeric;
        return parseChineseNumber(token);
    }

    private int safeEpisodeNumber(String raw) {
        int value = safeParseInt(raw);
        if (value <= 0 || value > 999) return -1;
        if (value >= 1900 && value <= 2099) return -1;
        return value;
    }

    private int parseChineseNumber(String raw) {
        if (raw == null) return -1;
        String text = raw.trim();
        if (text.isEmpty()) return -1;
        int total = 0;
        int current = 0;
        boolean seen = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int digit = chineseDigit(c);
            if (digit >= 0) {
                current = digit;
                seen = true;
            } else if (c == '十') {
                total += (current <= 0 ? 1 : current) * 10;
                current = 0;
                seen = true;
            } else if (c == '百') {
                total += (current <= 0 ? 1 : current) * 100;
                current = 0;
                seen = true;
            }
        }
        int value = total + current;
        return seen && value > 0 && value <= 999 ? value : -1;
    }

    private int chineseDigit(char c) {
        switch (c) {
            case '零':
            case '〇': return 0;
            case '一': return 1;
            case '二':
            case '两': return 2;
            case '三': return 3;
            case '四': return 4;
            case '五': return 5;
            case '六': return 6;
            case '七': return 7;
            case '八': return 8;
            case '九': return 9;
            default: return -1;
        }
    }

    private String normalizeSearchTitle(String raw) {
        String title = normalizeDisplayTitle(raw);
        title = PATTERN_NOISE_WORDS.matcher(title).replaceAll(" ");
        title = PATTERN_YEAR.matcher(title).replaceAll(" ");
        title = title.replaceAll("(?i)\\b[a-z]\\b", " ");
        title = title.replaceAll("[\uD83C-\uDBFF\uDC00-\uDFFF★☆🔥❤♡]+", " ");
        title = title.replaceAll("[·・•—–\\-－～~：:_,，。/\\\\|]+", " ");
        title = title.replaceAll("\\s+", " ").trim();
        return title;
    }

    private String normalizeDisplayTitle(String raw) {
        if (raw == null) return "";
        String title = raw.trim();
        if (title.isEmpty()) return "";
        title = title.replaceAll("(?i)\\s+from\\s+[\\w&.-]+$", " ");
        title = title.replaceAll("\\s*\\|\\s*[\\w&.-]+$", " ");
        Matcher bracket = PATTERN_BRACKETED.matcher(title);
        StringBuffer sb = new StringBuffer();
        while (bracket.find()) {
            String inside = bracket.group(1) == null ? "" : bracket.group(1).trim();
            boolean keepYear = PATTERN_YEAR.matcher(inside).matches();
            boolean keepLikelyTitle = inside.length() >= 2 && inside.length() <= 16 && !PATTERN_NOISE_WORDS.matcher(inside).find() && !PATTERN_YEAR.matcher(inside).find();
            bracket.appendReplacement(sb, keepYear || keepLikelyTitle ? " " + Matcher.quoteReplacement(inside) + " " : " ");
        }
        bracket.appendTail(sb);
        title = sb.toString();
        title = PATTERN_SEASON_EPISODE.matcher(title).replaceAll(" ");
        title = PATTERN_EXPLICIT_EPISODE.matcher(title).replaceAll(" ");
        title = title.replaceAll("(?i)(^|\\s)(tv|hd|sd)(?=\\s|$)", " ");
        title = title.replaceAll("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF★☆🔥❤♡]+", " ");
        title = title.replaceAll("\\s+", " ").trim();
        String collapsed = title.replaceAll("[\\s·・•—–\\-－～~：:_,，。/\\\\|]+", " ").trim();
        String[] parts = collapsed.split("\\s+");
        if (parts.length > 1) {
            for (String part : parts) {
                if (part.length() >= 2 && !PATTERN_NOISE_WORDS.matcher(part).find() && !PATTERN_YEAR.matcher(part).matches()) {
                    return part;
                }
            }
        }
        return collapsed;
    }

    private String extractSourceFromTitle(String title) {
        if (title == null || title.trim().isEmpty()) return "";
        Matcher matcher = PATTERN_SOURCE_FROM_TITLE.matcher(title.trim());
        return matcher.find() ? matcher.group(1) : "";
    }

    private String normalizeSourceKey(String raw) {
        if (raw == null) return "";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return "";
        if (value.contains("&")) {
            LinkedHashSet<String> parts = new LinkedHashSet<>();
            for (String part : value.split("&")) {
                String canonical = canonicalSingleSource(part);
                if (!canonical.isEmpty()) parts.add(canonical);
            }
            return joinAmp(parts);
        }
        return canonicalSingleSource(value);
    }

    private String canonicalSingleSource(String raw) {
        if (raw == null) return "";
        String source = raw.trim().toLowerCase(Locale.ROOT);
        if (source.isEmpty()) return "";
        if ("qq".equals(source)) return "tencent";
        if ("qiyi".equals(source)) return "iqiyi";
        if ("bilibili1".equals(source) || "bili".equals(source)) return "bilibili";
        if ("mango".equals(source) || "mgtv".equals(source)) return "imgo";
        if ("360kan".equals(source) || "kan360".equals(source)) return "360";
        switch (source) {
            case "360":
            case "vod":
            case "tmdb":
            case "douban":
            case "tencent":
            case "youku":
            case "iqiyi":
            case "imgo":
            case "bilibili":
            case "migu":
            case "renren":
            case "hanjutv":
            case "bahamut":
            case "dandan":
            case "sohu":
            case "leshi":
            case "xigua":
            case "maiduidui":
            case "aiyifan":
            case "animeko":
            case "custom":
                return source;
            default:
                return "";
        }
    }

    private String joinAmp(LinkedHashSet<String> parts) {
        StringBuilder sb = new StringBuilder();
        if (parts == null) return "";
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append('&');
            sb.append(part.trim());
        }
        return sb.toString();
    }

    private String displaySourceName(String source) {
        String key = normalizeSourceKey(source);
        if (key.isEmpty()) return "";
        if (key.contains("&")) {
            ArrayList<String> labels = new ArrayList<>();
            for (String part : key.split("&")) {
                String label = displaySingleSourceName(part);
                if (!label.isEmpty() && !labels.contains(label)) labels.add(label);
            }
            return joinNonBlank(labels.toArray(new String[0])).replace(" · ", "/");
        }
        return displaySingleSourceName(key);
    }

    private String displaySingleSourceName(String source) {
        String key = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
        switch (key) {
            case "360": return "360";
            case "vod": return "VOD";
            case "tmdb": return "TMDB";
            case "douban": return "豆瓣";
            case "tencent": return "腾讯";
            case "youku": return "优酷";
            case "iqiyi": return "爱奇艺";
            case "imgo": return "芒果TV";
            case "bilibili": return "哔哩哔哩";
            case "migu": return "咪咕";
            case "renren": return "人人";
            case "hanjutv": return "韩剧TV";
            case "bahamut": return "巴哈姆特";
            case "dandan": return "弹弹Play";
            case "sohu": return "搜狐";
            case "leshi": return "乐视";
            case "xigua": return "西瓜";
            case "maiduidui": return "埋堆堆";
            case "aiyifan": return "爱壹帆";
            case "animeko": return "Animeko";
            case "custom": return "自定义源";
            default: return "";
        }
    }

    private List<SourceFilter> buildSourceFilters(List<CandidateHandle> candidates) {
        LinkedHashMap<String, SourceFilter> map = new LinkedHashMap<>();
        if (candidates == null) return new ArrayList<>();
        for (CandidateHandle candidate : candidates) {
            if (candidate == null) continue;
            String source = normalizeSourceKey(candidate.source);
            if (source.isEmpty()) continue;
            SourceFilter existing = map.get(source);
            if (existing == null) {
                map.put(source, new SourceFilter(source, displaySourceName(source), 1));
            } else {
                existing.count += 1;
            }
        }
        return new ArrayList<>(map.values());
    }

    private boolean isTrailerText(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("预告") || lower.contains("trailer") || lower.contains("花絮") || lower.contains("彩蛋");
    }

    private int safeParseInt(String raw) {
        try {
            if (raw == null || raw.trim().isEmpty()) return -1;
            return Integer.parseInt(raw.trim());
        } catch (Throwable ignored) {
            return -1;
        }
    }


    private LinearLayout.LayoutParams matchWrapWithBottom(Activity activity, int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 0, 0, dp(activity, bottomDp));
        return lp;
    }

    private LinearLayout.LayoutParams matchWrapWithTop(Activity activity, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, dp(activity, topDp), 0, 0);
        return lp;
    }

    private LinearLayout.LayoutParams withBottomMargin(LinearLayout.LayoutParams lp, int bottomPx) {
        lp.setMargins(0, 0, 0, bottomPx);
        return lp;
    }

    private HorizontalScrollView buildHorizontalChipScroll(Activity activity) {
        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setScrollbarFadingEnabled(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.setFillViewport(false);
        scroll.setOnTouchListener((v, event) -> {
            ViewParent parent = v.getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
            return false;
        });
        return scroll;
    }

    private TextView platformChip(Activity activity, DanmuTheme t, String label, boolean selected) {
        TextView chip = new TextView(activity);
        chip.setText(label == null ? "" : label);
        chip.setTextSize(DanmuTheme.TEXT_CAPTION);
        chip.setSingleLine(true);
        chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chip.setGravity(Gravity.CENTER);
        chip.setIncludeFontPadding(false);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        chip.setTextColor(selected ? t.accentText : t.textSecondary);
        int fill = selected ? t.accent : t.surfaceAlt;
        int stroke = selected ? t.accentStrong : t.stroke;
        chip.setBackground(t.roundRect(fill, DanmuTheme.RADIUS_PILL, stroke, selected ? 2 : 1, activity));
        chip.setPadding(dp(activity, DanmuTheme.SPACE_3), 0, dp(activity, DanmuTheme.SPACE_3), 0);
        return chip;
    }

    private void renderPlatformFilters(Activity activity, DanmuTheme t, LinearLayout chipsRow,
                                       List<SourceFilter> filters, String selectedSource,
                                       FilterSelectListener listener) {
        chipsRow.removeAllViews();
        TextView allChip = platformChip(activity, t, "全部 " + countAllFilters(filters), selectedSource == null || selectedSource.trim().isEmpty());
        allChip.setOnClickListener(v -> {
            if (listener != null) listener.onSelect("");
        });
        LinearLayout.LayoutParams allLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 30));
        allLp.rightMargin = dp(activity, DanmuTheme.SPACE_2);
        chipsRow.addView(allChip, allLp);

        for (SourceFilter filter : filters) {
            if (filter == null || filter.source.isEmpty() || filter.count <= 0) continue;
            boolean selected = filter.source.equals(selectedSource == null ? "" : selectedSource.trim());
            TextView chip = platformChip(activity, t, filter.displayName() + " " + filter.count, selected);
            chip.setOnClickListener(v -> {
                if (listener != null) listener.onSelect(filter.source);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 30));
            lp.rightMargin = dp(activity, DanmuTheme.SPACE_2);
            chipsRow.addView(chip, lp);
        }
    }

    private int countAllFilters(List<SourceFilter> filters) {
        int count = 0;
        if (filters == null) return 0;
        for (SourceFilter filter : filters) {
            if (filter != null) count += Math.max(0, filter.count);
        }
        return count;
    }

    private GradientDrawable makeRoundRect(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(radiusDp * 1.0f);
        if (strokeWidthDp > 0) bg.setStroke(strokeWidthDp, strokeColor);
        return bg;
    }

    private void adjustNumericInput(EditText input, double delta) {
        if (input == null) return;
        String raw = input.getText() == null ? "" : input.getText().toString().trim();
        double value = safeParseDouble(raw, 0.0d);
        input.setText(formatOffsetSeconds(value + delta));
        input.setSelection(input.getText().length());
    }

    private void renderEpisodeGrid(Activity activity, DanmuTheme t, GridLayout episodeGrid, ArrayList<CandidateHandle> compactHandles,
                                   ArrayList<TextView> itemViews, final int[] selectedEpisodeIndex, boolean showTitles,
                                   int shellPort, TextView statusText, Button actionButton, TextView pushInfoText, int[] gridMetrics,
                                   Runnable onSelectionChanged) {
        episodeGrid.removeAllViews();
        itemViews.clear();
        if (compactHandles.isEmpty()) {
            gridMetrics[0] = 1;
            gridMetrics[1] = 40;
            return;
        }
        int columns = showTitles ? 1 : computeEpisodeColumns(activity);
        int rowHeightDp = showTitles ? 44 : 38;
        gridMetrics[0] = columns;
        gridMetrics[1] = rowHeightDp;
        episodeGrid.setColumnCount(columns);
        episodeGrid.setMinimumWidth(0);
        episodeGrid.setClipToPadding(false);
        int marginPx = dp(activity, DanmuTheme.SPACE_1);
        int selected = clamp(selectedEpisodeIndex[0], 0, compactHandles.size() - 1);
        selectedEpisodeIndex[0] = selected;
        for (int i = 0; i < compactHandles.size(); i++) {
            final CandidateHandle candidate = compactHandles.get(i);
            final int index = i;
            TextView cell = DanmuUi.episodeCell(activity, t);
            DanmuUi.styleEpisodeCell(activity, t, cell, episodeCellLabel(candidate, index, showTitles), index == selected, showTitles);
            cell.setOnClickListener(v -> {
                int prev = selectedEpisodeIndex[0];
                if (prev == index) {
                    // re-click an already-selected episode pushes it directly
                    pushCandidate(activity, candidate, shellPort, statusText, pushInfoText);
                    return;
                }
                selectedEpisodeIndex[0] = index;
                if (prev >= 0 && prev < itemViews.size()) {
                    DanmuUi.styleEpisodeCell(activity, t, itemViews.get(prev), episodeCellLabel(compactHandles.get(prev), prev, showTitles), false, showTitles);
                }
                DanmuUi.styleEpisodeCell(activity, t, cell, episodeCellLabel(candidate, index, showTitles), true, showTitles);
                if (actionButton != null) actionButton.setText("推送");
                statusText.setText("已选中第" + shortEpisodeLabel(candidate, index) + "集，再点一次或按推送执行");
                if (onSelectionChanged != null) onSelectionChanged.run();
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(activity, showTitles ? 40 : 36);
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
            lp.setMargins(marginPx, marginPx, marginPx, marginPx);
            episodeGrid.addView(cell, lp);
            itemViews.add(cell);
        }
        ViewGroup.LayoutParams existing = episodeGrid.getLayoutParams();
        if (existing != null) {
            existing.width = ViewGroup.LayoutParams.MATCH_PARENT;
            existing.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            episodeGrid.setLayoutParams(existing);
        }
    }

    private String episodeCellLabel(CandidateHandle candidate, int index, boolean showTitles) {
        if (showTitles) {
            return buildEpisodeTitleLabel(candidate, index);
        }
        return shortEpisodeLabel(candidate, index);
    }

    private String buildEpisodeTitleLabel(CandidateHandle candidate, int index) {
        int number = candidate == null ? 0 : extractEpisodeNumber(candidate.label);
        String head = number > 0 ? "第" + number + "集" : "第" + (index + 1) + "集";
        EpisodeCandidate episode = candidate == null ? null : episodeCandidates.get(candidate.handle);
        String title = episode == null ? "" : episode.name.trim();
        if (!title.isEmpty() && !title.equals(String.valueOf(number)) && !title.equals(head)) {
            return head + " · " + title;
        }
        return head;
    }

    private int computeEpisodeColumns(Activity activity) {
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int dialogWidth = (int) (screenWidth * 0.86f) - dp(activity, 36);
        int perItem = dp(activity, 52);
        int columns = Math.max(1, dialogWidth / perItem);
        return clamp(columns, 5, 9);
    }

    private void scrollEpisodeGridToIndex(Activity activity, ScrollView episodeScroll, int index, int columns, int rowHeightDp) {
        episodeScroll.post(() -> {
            int safeColumns = Math.max(1, columns);
            int row = Math.max(0, index) / safeColumns;
            int y = Math.max(0, row * dp(activity, rowHeightDp) - dp(activity, 24));
            episodeScroll.smoothScrollTo(0, y);
        });
    }

    private String shortEpisodeLabel(CandidateHandle candidate, int index) {
        int number = extractEpisodeNumber(candidate == null ? "" : candidate.label);
        return number > 0 ? String.valueOf(number) : String.valueOf(index + 1);
    }

    private int countDanmaku(String danmakuUrl) {
        try {
            String body = httpGet(danmakuUrl, 1800, 12000);
            String trimmed = body == null ? "" : body.trim();
            if (trimmed.isEmpty()) return -1;
            if (trimmed.startsWith("{")) {
                JSONObject root = new JSONObject(trimmed);
                JSONArray array = root.optJSONArray("comments");
                if (array == null) array = root.optJSONArray("danmus");
                if (array == null) array = root.optJSONArray("data");
                if (array != null) return array.length();
                JSONObject data = root.optJSONObject("data");
                if (data != null) {
                    array = data.optJSONArray("comments");
                    if (array == null) array = data.optJSONArray("danmus");
                    if (array != null) return array.length();
                }
            }
            if (trimmed.startsWith("[")) return new JSONArray(trimmed).length();
            int xmlCount = countOccurrences(trimmed, "<d ") + countOccurrences(trimmed, "<d>");
            return xmlCount > 0 ? xmlCount : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private int countOccurrences(String text, String needle) {
        if (text == null || needle == null || needle.isEmpty()) return 0;
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private String buildShellPushUrl(int shellPort, String danmakuUrl) throws Exception {
        int port = shellPort > 0 && shellPort <= 65535 ? shellPort : 9978;
        return "http://127.0.0.1:" + port + "/action?do=refresh&type=danmaku&path=" + urlEncode(danmakuUrl);
    }

    private String urlEncode(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8").replace("+", "%20");
    }


    private SharedPreferences getRemotePreferencesOrNull() {
        try {
            if ((getFrameworkProperties() & PROP_CAP_REMOTE) == 0L) return null;
            return getRemotePreferences(PREFS_INJECTION);
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "remote preferences unavailable: " + throwable.getMessage());
            return null;
        }
    }

    private InjectionSettings readInjectionSettings(Context context, int fallbackPort) {
        int normalizedFallbackPort = fallbackPort > 0 && fallbackPort <= 65535 ? fallbackPort : 9978;
        if (context == null) return new InjectionSettings(true, true, 0.0d, -1, normalizedFallbackPort, false, 0, "", DIALOG_STYLE_CENTER);
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_INJECTION, Context.MODE_PRIVATE);
            boolean injectionEnabled = true;
            boolean autoPush = true;
            double offset = safeParseDouble(prefs.getString(KEY_OFFSET_SEC, "0"), 0.0d);
            int fontSize = safeParseInt(prefs.getString(KEY_FONT_SIZE, ""));
            int storedPort = prefs.getInt(KEY_SHELL_PORT, normalizedFallbackPort);
            boolean darkTheme = prefs.getBoolean(KEY_UI_DARK_THEME, false);
            int dialogStyle = prefs.getInt(KEY_DIALOG_STYLE, DIALOG_STYLE_CENTER);
            int corePort = 0;
            String coreToken = "";
            SharedPreferences remotePrefs = getRemotePreferencesOrNull();
            if (remotePrefs != null) {
                injectionEnabled = remotePrefs.getBoolean(KEY_INJECTION_ENABLED, injectionEnabled);
                autoPush = remotePrefs.getBoolean(KEY_AUTO_PUSH_ENABLED, autoPush);
                offset = safeParseDouble(remotePrefs.getString(KEY_OFFSET_SEC, formatOffsetSeconds(offset)), offset);
                fontSize = safeParseInt(remotePrefs.getString(KEY_FONT_SIZE, fontSize > 0 ? String.valueOf(fontSize) : ""));
                storedPort = remotePrefs.getInt(KEY_SHELL_PORT, storedPort);
                darkTheme = remotePrefs.getBoolean(KEY_UI_DARK_THEME, darkTheme);
                dialogStyle = remotePrefs.getInt(KEY_DIALOG_STYLE, dialogStyle);
                corePort = remotePrefs.getInt(KEY_CORE_PORT, 0);
                coreToken = normalizeToken(remotePrefs.getString(KEY_CORE_TOKEN, ""));
            }
            int port = storedPort > 0 && storedPort <= 65535 ? storedPort : normalizedFallbackPort;
            return new InjectionSettings(injectionEnabled, autoPush, offset, fontSize > 0 ? fontSize : -1, port, darkTheme, corePort, coreToken, dialogStyle);
        } catch (Throwable throwable) {
            return new InjectionSettings(true, true, 0.0d, -1, normalizedFallbackPort, false, 0, "", DIALOG_STYLE_CENTER);
        }
    }

    private boolean saveInjectionSettings(Context context, InjectionSettings settings) {
        if (context == null || settings == null) return false;
        try {
            String formattedOffset = formatOffsetSeconds(settings.offsetSec);
            String formattedFontSize = settings.fontSize > 0 ? String.valueOf(settings.fontSize) : "";
            SharedPreferences localPrefs = context.getSharedPreferences(PREFS_INJECTION, Context.MODE_PRIVATE);
            boolean localOk = commitInjectionSettings(localPrefs, formattedOffset, formattedFontSize, settings);
            boolean remoteAttempted = false;
            boolean remoteOk = false;
            SharedPreferences remotePrefs = null;
            try {
                remotePrefs = getRemotePreferencesOrNull();
            } catch (Throwable remoteEx) {
                log(Log.WARN, TAG, "save injection settings remote prefs unavailable: " + remoteEx.getMessage());
            }
            if (remotePrefs != null) {
                remoteAttempted = true;
                try {
                    remoteOk = commitInjectionSettings(remotePrefs, formattedOffset, formattedFontSize, settings);
                    if (!remoteOk) log(Log.WARN, TAG, "save injection settings failed: remote commit returned false");
                } catch (Throwable remoteEx) {
                    log(Log.WARN, TAG, "save injection settings remote write failed: " + remoteEx.getMessage());
                }
            }
            if (!localOk) log(Log.WARN, TAG, "save injection settings failed: local commit returned false");
            if (localOk) return true;
            return remoteAttempted && remoteOk;
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "save injection settings failed: " + throwable.getMessage());
            return false;
        }
    }

    private boolean readEpisodeShowTitles(Context context) {
        if (context == null) return false;
        try {
            SharedPreferences remotePrefs = getRemotePreferencesOrNull();
            if (remotePrefs != null && remotePrefs.contains(KEY_EPISODE_SHOW_TITLES)) {
                return remotePrefs.getBoolean(KEY_EPISODE_SHOW_TITLES, false);
            }
            return context.getSharedPreferences(PREFS_INJECTION, Context.MODE_PRIVATE)
                .getBoolean(KEY_EPISODE_SHOW_TITLES, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean saveEpisodeShowTitles(Context context, boolean showTitles) {
        if (context == null) return false;
        try {
            SharedPreferences localPrefs = context.getSharedPreferences(PREFS_INJECTION, Context.MODE_PRIVATE);
            boolean localOk = commitEpisodeShowTitles(localPrefs, showTitles);
            boolean remoteAttempted = false;
            boolean remoteOk = false;
            SharedPreferences remotePrefs = null;
            try {
                remotePrefs = getRemotePreferencesOrNull();
            } catch (Throwable remoteEx) {
                log(Log.WARN, TAG, "save episode show titles remote prefs unavailable: " + remoteEx.getMessage());
            }
            if (remotePrefs != null) {
                remoteAttempted = true;
                try {
                    remoteOk = commitEpisodeShowTitles(remotePrefs, showTitles);
                    if (!remoteOk) log(Log.WARN, TAG, "save episode show titles failed: remote commit returned false");
                } catch (Throwable remoteEx) {
                    log(Log.WARN, TAG, "save episode show titles remote write failed: " + remoteEx.getMessage());
                }
            }
            if (!localOk) log(Log.WARN, TAG, "save episode show titles failed: local commit returned false");
            if (localOk) return true;
            return remoteAttempted && remoteOk;
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "save episode show titles failed: " + throwable.getMessage());
            return false;
        }
    }

    private boolean commitInjectionSettings(SharedPreferences prefs, String formattedOffset, String formattedFontSize, InjectionSettings settings) {
        if (prefs == null || settings == null) return false;
        return prefs.edit()
            .putString(KEY_OFFSET_SEC, formattedOffset)
            .putString(KEY_FONT_SIZE, formattedFontSize)
            .putInt(KEY_SHELL_PORT, settings.shellPort)
            .putBoolean(KEY_UI_DARK_THEME, settings.darkTheme)
            .putInt(KEY_DIALOG_STYLE, settings.dialogStyle)
            .commit();
    }

    private boolean commitEpisodeShowTitles(SharedPreferences prefs, boolean showTitles) {
        if (prefs == null) return false;
        return prefs.edit()
            .putBoolean(KEY_EPISODE_SHOW_TITLES, showTitles)
            .commit();
    }

    private void showInjectionSettingsDialog(Activity activity, View backgroundAnchor, int[] shellPortRef) {
        showInjectionSettingsOverlay(activity, backgroundAnchor, shellPortRef);
    }

    private void showInjectionSettingsOverlay(Activity activity, View backgroundAnchor, int[] shellPortRef) {

        try {
            ViewGroup hostContent = findHostContentRoot(activity);
            if (hostContent == null) {
                Toast.makeText(activity, "打开设置失败：找不到宿主容器", Toast.LENGTH_SHORT).show();
                return;
            }
            installSettingsOverlayBackInterceptor(activity);
            removeExistingSettingsOverlay(hostContent);

            int fallbackPort = shellPortRef != null && shellPortRef.length > 0 ? shellPortRef[0] : 9978;
            InjectionSettings current = readInjectionSettings(activity, fallbackPort);
            final boolean[] darkTheme = new boolean[]{current.darkTheme};
            final boolean[] showTitles = new boolean[]{readEpisodeShowTitles(activity)};
            final double[] offset = new double[]{current.offsetSec};
            final int[] fontSize = new int[]{current.fontSize};
            final int[] shellPort = new int[]{current.shellPort};
            final int[] dialogStyle = new int[]{current.dialogStyle};

            TextView templateText = findFirstTextView(backgroundAnchor);
            Drawable rowTemplateBackground = resolveRowTemplateBackground(backgroundAnchor);
            View hostPageContainer = findHostPageContainer(backgroundAnchor, hostContent);
            View hostPageRoot = findHostPageRootForOverlay(backgroundAnchor, hostContent);
            Drawable pageBackground = resolveHostPageBackground(activity, backgroundAnchor, hostPageContainer);

            FrameLayout root = new FrameLayout(activity);
            SettingsOverlayState overlayState = new SettingsOverlayState(hostPageContainer, hostPageRoot, backgroundAnchor);
            root.setTag(overlayState);
            root.setClickable(true);
            root.setFocusable(true);
            root.setFocusableInTouchMode(true);
            if (pageBackground != null) {
                root.setBackground(pageBackground);
            } else {
                root.setBackgroundColor(Color.TRANSPARENT);
            }

            ScrollView scroll = new ScrollView(activity);
            scroll.setFillViewport(true);
            scroll.setVerticalScrollBarEnabled(true);
            scroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
            scroll.setBackgroundColor(Color.TRANSPARENT);

            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            int padH = dp(activity, 16);
            int padTop = dp(activity, 12);
            int navSafeBottom = hostPageContainer != null ? 0 : getNavigationBarSafeInset(activity);
            content.setPadding(padH, padTop, padH, dp(activity, 24) + navSafeBottom);
            scroll.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ));

            TextView title = new TextView(activity);
            if (templateText != null) {
                copyTextStyle(title, templateText, true, true);
            } else {
                copyTextStyle(title, null, true, true);
            }
            title.setText("APP弹幕");
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            if (templateText != null) {
                float hostSizePx = templateText.getTextSize();
                float scaled = hostSizePx * 1.25f;
                float minSp = dp(activity, 14);
                if (scaled < minSp) scaled = minSp;
                title.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaled);
            } else {
                title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
            }
            title.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            final int rowGap = resolveSettingsRowGap(backgroundAnchor, dp(activity, 16));
            // 标题和第一项之间只保留一份官方 row 间距：第一项自身会在 addSettingsRow() 中应用 topMargin。
            titleLp.bottomMargin = 0;
            content.addView(title, titleLp);

            final InjectionSettings base = current;

            SettingsRowViews themeRow = createSettingsRow(activity, backgroundAnchor, rowTemplateBackground, templateText,
                "界面主题", darkTheme[0] ? "黑色" : "白色");
            themeRow.row.setOnClickListener(v -> {
                boolean next = !darkTheme[0];
                InjectionSettings updated = new InjectionSettings(
                    base.injectionEnabled,
                    base.autoPushEnabled,
                    offset[0],
                    fontSize[0],
                    shellPort[0],
                    next,
                    base.corePort,
                    base.coreToken,
                    dialogStyle[0]
                );
                if (saveInjectionSettings(activity, updated)) {
                    darkTheme[0] = next;
                    themeRow.value.setText(next ? "黑色" : "白色");
                    Toast.makeText(activity, "已保存界面主题", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, "保存界面主题失败", Toast.LENGTH_SHORT).show();
                }
            });
            addSettingsRow(content, themeRow.row, backgroundAnchor, rowGap);

            SettingsRowViews titleModeRow = createSettingsRow(activity, backgroundAnchor, rowTemplateBackground, templateText,
                "集详情显示", showTitles[0] ? "带标题" : "数字格");
            titleModeRow.row.setOnClickListener(v -> {
                boolean next = !showTitles[0];
                if (saveEpisodeShowTitles(activity, next)) {
                    showTitles[0] = next;
                    titleModeRow.value.setText(next ? "带标题" : "数字格");
                    Toast.makeText(activity, "已保存集详情显示", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, "保存集详情显示失败", Toast.LENGTH_SHORT).show();
                }
            });
            addSettingsRow(content, titleModeRow.row, backgroundAnchor, rowGap);

            SettingsRowViews offsetRow = createSettingsRow(activity, backgroundAnchor, rowTemplateBackground, templateText,
                "时间轴偏移", formatOffsetSeconds(offset[0]));
            offsetRow.row.setOnClickListener(v -> showOffsetInputDialog(activity, offset[0], value -> {
                Double parsed = parseNullableDouble(value);
                if (parsed == null) {
                    Toast.makeText(activity, "时间轴偏移请输入数字，可为负", Toast.LENGTH_SHORT).show();
                    return;
                }
                double next = parsed;
                InjectionSettings updated = new InjectionSettings(
                    base.injectionEnabled,
                    base.autoPushEnabled,
                    next,
                    fontSize[0],
                    shellPort[0],
                    darkTheme[0],
                    base.corePort,
                    base.coreToken,
                    dialogStyle[0]
                );
                if (saveInjectionSettings(activity, updated)) {
                    offset[0] = next;
                    offsetRow.value.setText(formatOffsetSeconds(next));
                    Toast.makeText(activity, "已保存时间轴偏移", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, "保存时间轴偏移失败", Toast.LENGTH_SHORT).show();
                }
            }));
            addSettingsRow(content, offsetRow.row, backgroundAnchor, rowGap);

            SettingsRowViews fontRow = createSettingsRow(activity, backgroundAnchor, rowTemplateBackground, templateText,
                "弹幕大小", fontSize[0] > 0 ? String.valueOf(fontSize[0]) : "默认");
            fontRow.row.setOnClickListener(v -> showFontSizeChooser(activity, fontSize[0], value -> {
                int next = value;
                InjectionSettings updated = new InjectionSettings(
                    base.injectionEnabled,
                    base.autoPushEnabled,
                    offset[0],
                    next,
                    shellPort[0],
                    darkTheme[0],
                    base.corePort,
                    base.coreToken,
                    dialogStyle[0]
                );
                if (saveInjectionSettings(activity, updated)) {
                    fontSize[0] = next;
                    fontRow.value.setText(next > 0 ? String.valueOf(next) : "默认");
                    Toast.makeText(activity, "已保存弹幕大小", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, "保存弹幕大小失败", Toast.LENGTH_SHORT).show();
                }
            }));
            addSettingsRow(content, fontRow.row, backgroundAnchor, rowGap);

            SettingsRowViews portRow = createSettingsRow(activity, backgroundAnchor, rowTemplateBackground, templateText,
                "影视壳端口", String.valueOf(shellPort[0]));
            portRow.row.setOnClickListener(v -> showIntegerInputDialog(activity, "影视壳端口",
                "请输入 1-65535", String.valueOf(shellPort[0]), value -> {
                    int parsed = safeParseInt(value);
                    if (parsed <= 0 || parsed > 65535) {
                        Toast.makeText(activity, "影视壳端口范围应为 1-65535", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int next = parsed;
                    InjectionSettings updated = new InjectionSettings(
                        base.injectionEnabled,
                        base.autoPushEnabled,
                        offset[0],
                        fontSize[0],
                        next,
                        darkTheme[0],
                        base.corePort,
                        base.coreToken,
                        dialogStyle[0]
                    );
                    if (saveInjectionSettings(activity, updated)) {
                        shellPort[0] = next;
                        if (shellPortRef != null && shellPortRef.length > 0) shellPortRef[0] = next;
                        portRow.value.setText(String.valueOf(next));
                        Toast.makeText(activity, "已保存影视壳端口", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(activity, "保存影视壳端口失败", Toast.LENGTH_SHORT).show();
                    }
                }));
            addSettingsRow(content, portRow.row, backgroundAnchor, rowGap);

            SettingsRowViews dialogStyleRow = createSettingsRow(activity, backgroundAnchor, rowTemplateBackground, templateText,
                "弹窗样式", dialogStyle[0] == DIALOG_STYLE_CENTER ? "居中" : "底部抽屉");
            dialogStyleRow.row.setOnClickListener(v -> {
                int next = dialogStyle[0] == DIALOG_STYLE_CENTER ? DIALOG_STYLE_BOTTOM_SHEET : DIALOG_STYLE_CENTER;
                InjectionSettings updated = new InjectionSettings(
                    base.injectionEnabled,
                    base.autoPushEnabled,
                    offset[0],
                    fontSize[0],
                    shellPort[0],
                    darkTheme[0],
                    base.corePort,
                    base.coreToken,
                    next
                );
                if (saveInjectionSettings(activity, updated)) {
                    dialogStyle[0] = next;
                    dialogStyleRow.value.setText(next == DIALOG_STYLE_CENTER ? "居中" : "底部抽屉");
                    Toast.makeText(activity, "已保存弹窗样式", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, "保存弹窗样式失败", Toast.LENGTH_SHORT).show();
                }
            });
            addSettingsRow(content, dialogStyleRow.row, backgroundAnchor, rowGap);

            hostContent.addView(root, buildSettingsOverlayLayoutParams(hostContent, hostPageContainer));
            root.bringToFront();
            root.requestFocus();
            attachHostNavigationCloseGuard(root, overlayState);
        } catch (Throwable throwable) {
            Toast.makeText(activity, "打开设置失败：" + throwable.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
            log(Log.WARN, TAG, "show injection settings failed: " + throwable.getMessage());
        }
    }

    private void installSettingsOverlayBackInterceptor(Activity activity) {
        try {
            if (activity == null) return;
            Method method = findHostBackHandlerMethod(activity.getClass());
            if (method == null) return;
            String key = method.getDeclaringClass().getName() + "#" + method.getName();
            if (settingsOverlayBackHooks.putIfAbsent(key, Boolean.TRUE) != null) return;
            hook(method).intercept(chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof Activity && closeActiveSettingsOverlay((Activity) thisObject)) {
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "install settings overlay back interceptor failed: " + throwable.getMessage());
        }
    }

    private Method findHostBackHandlerMethod(Class<?> cls) {
        if (cls == null || !Activity.class.isAssignableFrom(cls)) return null;
        String[] methodNames = {"p0", "q"};
        for (String methodName : methodNames) {
            try {
                Method method = cls.getDeclaredMethod(methodName);
                if (method.getParameterTypes().length == 0) {
                    method.setAccessible(true);
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable throwable) {
                log(Log.WARN, TAG, "find host back handler failed: " + throwable.getMessage());
            }
        }
        return null;
    }

    private boolean closeActiveSettingsOverlay(Activity activity) {
        ViewGroup content = findHostContentRoot(activity);
        if (content == null) return false;
        for (int i = content.getChildCount() - 1; i >= 0; i--) {
            View child = content.getChildAt(i);
            if (isSettingsOverlayView(child)) {
                closeSettingsOverlay(child);
                return true;
            }
        }
        return false;
    }

    private boolean isSettingsOverlayView(View child) {
        if (child == null) return false;
        Object tag = child.getTag();
        return tag instanceof SettingsOverlayState || SETTINGS_OVERLAY_TAG.equals(tag);
    }

    private ViewGroup findHostContentRoot(Activity activity) {
        if (activity == null) return null;
        View content = activity.findViewById(android.R.id.content);
        return content instanceof ViewGroup ? (ViewGroup) content : null;
    }

    private View findHostPageContainer(View backgroundAnchor, ViewGroup hostContent) {
        if (backgroundAnchor == null || hostContent == null) return null;
        View current = backgroundAnchor;
        while (current != null) {
            ViewParent parent = current.getParent();
            if (!(parent instanceof View)) return null;
            View parentView = (View) parent;
            if (isHostFragmentContainer(parentView)) return parentView;
            if (parentView == hostContent) return null;
            current = parentView;
        }
        return null;
    }

    private View findHostPageRootForOverlay(View backgroundAnchor, ViewGroup hostContent) {
        if (backgroundAnchor == null || hostContent == null) return null;
        View current = backgroundAnchor;
        while (current != null) {
            ViewParent parent = current.getParent();
            if (!(parent instanceof View)) return null;
            View parentView = (View) parent;
            if (isHostFragmentContainer(parentView)) return current;
            if (parentView == hostContent) return null;
            current = parentView;
        }
        return null;
    }

    private boolean isHostFragmentContainer(View view) {
        if (view == null) return false;
        String className = view.getClass().getName();
        if (className != null && className.contains("FragmentContainerView")) return true;
        String entryName = safeResourceEntryName(view);
        return "container".equals(entryName);
    }

    private String safeResourceEntryName(View view) {
        if (view == null || view.getId() == View.NO_ID) return "";
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void removeExistingSettingsOverlay(ViewGroup hostContent) {
        if (hostContent == null) return;
        for (int i = hostContent.getChildCount() - 1; i >= 0; i--) {
            View child = hostContent.getChildAt(i);
            if (child == null) continue;
            Object tag = child.getTag();
            if (tag instanceof SettingsOverlayState) {
                SettingsOverlayState state = (SettingsOverlayState) tag;
                detachHostNavigationCloseGuard(child, state);
                hostContent.removeViewAt(i);
            } else if (SETTINGS_OVERLAY_TAG.equals(tag)) {
                hostContent.removeViewAt(i);
            }
        }
    }

    private void closeSettingsOverlay(View view) {
        if (view == null) return;
        Object tag = view.getTag();
        if (tag instanceof SettingsOverlayState) {
            SettingsOverlayState state = (SettingsOverlayState) tag;
            detachHostNavigationCloseGuard(view, state);
        }
        removeViewFromParent(view);
    }

    private void removeViewFromParent(View view) {
        if (view == null) return;
        ViewParent parent = view.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(view);
        }
    }

    private ViewGroup.LayoutParams buildSettingsOverlayLayoutParams(ViewGroup hostContent, View hostPageContainer) {
        if (hostContent instanceof FrameLayout) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            );
            if (hostPageContainer != null && hostPageContainer.getWidth() > 0 && hostPageContainer.getHeight() > 0) {
                int[] hostLoc = new int[2];
                int[] containerLoc = new int[2];
                hostContent.getLocationOnScreen(hostLoc);
                hostPageContainer.getLocationOnScreen(containerLoc);
                lp.width = hostPageContainer.getWidth();
                lp.height = hostPageContainer.getHeight();
                lp.leftMargin = containerLoc[0] - hostLoc[0];
                lp.topMargin = containerLoc[1] - hostLoc[1];
                return lp;
            }
            lp.bottomMargin = findBottomNavigationHeight(hostContent);
            return lp;
        }
        return new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    private int findBottomNavigationHeight(View root) {
        View nav = findBottomNavigationView(root);
        return nav != null && nav.getVisibility() == View.VISIBLE ? Math.max(0, nav.getHeight()) : 0;
    }

    private View findBottomNavigationView(View root) {
        if (root == null) return null;
        ArrayList<View> stack = new ArrayList<>();
        stack.add(root);
        while (!stack.isEmpty()) {
            View view = stack.remove(stack.size() - 1);
            if (view == null || view.getVisibility() != View.VISIBLE) continue;
            String className = view.getClass().getName();
            String entryName = safeResourceEntryName(view);
            if ((className != null && className.contains("BottomNavigationView")) || "navigation".equals(entryName)) {
                return view;
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    stack.add(group.getChildAt(i));
                }
            }
        }
        return null;
    }

    private void attachHostNavigationCloseGuard(FrameLayout overlayRoot, SettingsOverlayState state) {
        if (overlayRoot == null || state == null) return;
        ViewTreeObserver observer = overlayRoot.getViewTreeObserver();
        ViewTreeObserver.OnPreDrawListener preDrawGuard = () -> {
            if (overlayRoot.getParent() == null) return true;
            if (shouldCloseSettingsOverlayForHostChange(state)) {
                closeSettingsOverlay(overlayRoot);
            }
            return true;
        };
        state.preDrawGuard = preDrawGuard;
        if (observer != null && observer.isAlive()) {
            observer.addOnPreDrawListener(preDrawGuard);
        }
        Runnable guard = new Runnable() {
            @Override
            public void run() {
                if (overlayRoot.getParent() == null) return;
                if (shouldCloseSettingsOverlayForHostChange(state)) {
                    closeSettingsOverlay(overlayRoot);
                    return;
                }
                overlayRoot.postDelayed(this, SETTINGS_OVERLAY_NAV_GUARD_INTERVAL_MS);
            }
        };
        state.hostChangeGuard = guard;
        overlayRoot.post(guard);
    }

    private void detachHostNavigationCloseGuard(View overlayRoot, SettingsOverlayState state) {
        if (overlayRoot == null || state == null) return;
        if (state.hostChangeGuard != null) {
            overlayRoot.removeCallbacks(state.hostChangeGuard);
            state.hostChangeGuard = null;
        }
        if (state.preDrawGuard != null) {
            ViewTreeObserver observer = overlayRoot.getViewTreeObserver();
            if (observer != null && observer.isAlive()) {
                observer.removeOnPreDrawListener(state.preDrawGuard);
            }
            state.preDrawGuard = null;
        }
    }

    private boolean shouldCloseSettingsOverlayForHostChange(SettingsOverlayState state) {
        if (state == null) return false;
        View pageRoot = state.hostPageRoot;
        View container = state.hostPageContainer;
        if (container != null && !container.isAttachedToWindow()) return true;
        if (pageRoot == null) return false;
        if (!pageRoot.isAttachedToWindow()) return true;
        if (pageRoot.getVisibility() != View.VISIBLE || !pageRoot.isShown()) return true;
        if (container != null && !isDescendantOf(pageRoot, container)) return true;
        if (state.backgroundAnchor != null) {
            if (!state.backgroundAnchor.isAttachedToWindow()) return true;
            if (!isDescendantOf(state.backgroundAnchor, pageRoot)) return true;
            if (state.backgroundAnchor.getVisibility() != View.VISIBLE || !state.backgroundAnchor.isShown()) return true;
        }
        if (container instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) container;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child != null && child != pageRoot && child.getVisibility() == View.VISIBLE && child.isShown()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isDescendantOf(View child, View ancestor) {
        if (child == null || ancestor == null) return false;
        View current = child;
        while (current != null) {
            if (current == ancestor) return true;
            ViewParent parent = current.getParent();
            if (!(parent instanceof View)) return false;
            current = (View) parent;
        }
        return false;
    }

    private int getNavigationBarSafeInset(Activity activity) {
        if (activity == null) return 0;
        int orientation = activity.getResources().getConfiguration().orientation;
        if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) return 0;
        int resourceId = activity.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return resourceId > 0 ? activity.getResources().getDimensionPixelSize(resourceId) : 0;
    }

    private void addSettingsRow(LinearLayout content, View row, View rowTemplateAnchor, int fallbackTopMarginPx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int topMargin = resolveSettingsRowGap(rowTemplateAnchor, fallbackTopMarginPx);
        lp.topMargin = topMargin;
        content.addView(row, lp);
    }

    private int resolveSettingsRowGap(View rowTemplateAnchor, int fallbackPx) {
        int gap = fallbackPx;
        if (rowTemplateAnchor != null) {
            ViewGroup.LayoutParams source = rowTemplateAnchor.getLayoutParams();
            if (source instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams src = (ViewGroup.MarginLayoutParams) source;
                if (src.topMargin > 0) {
                    gap = src.topMargin;
                } else if (src.bottomMargin > 0) {
                    gap = src.bottomMargin;
                }
            }
        }
        return gap;
    }

    private SettingsRowViews createSettingsRow(Activity activity, View rowTemplateAnchor, Drawable rowTemplateBackground, TextView textTemplate, String title, String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        Drawable clonedBg = cloneDrawable(rowTemplateBackground);
        if (clonedBg != null) row.setBackground(clonedBg);

        int padLeft = rowTemplateAnchor != null ? rowTemplateAnchor.getPaddingLeft() : 0;
        int padTop = rowTemplateAnchor != null ? rowTemplateAnchor.getPaddingTop() : 0;
        int padRight = rowTemplateAnchor != null ? rowTemplateAnchor.getPaddingRight() : 0;
        int padBottom = rowTemplateAnchor != null ? rowTemplateAnchor.getPaddingBottom() : 0;
        if (padLeft <= 0 && textTemplate != null) padLeft = textTemplate.getPaddingLeft();
        if (padTop <= 0 && textTemplate != null) padTop = textTemplate.getPaddingTop();
        if (padRight <= 0 && textTemplate != null) padRight = textTemplate.getPaddingRight();
        if (padBottom <= 0 && textTemplate != null) padBottom = textTemplate.getPaddingBottom();
        if (padLeft <= 0) padLeft = dp(activity, 16);
        if (padTop <= 0) padTop = dp(activity, 8);
        if (padRight <= 0) padRight = dp(activity, 16);
        if (padBottom <= 0) padBottom = dp(activity, 8);
        row.setPadding(padLeft, padTop, padRight, padBottom);

        int minHeight = rowTemplateAnchor != null ? rowTemplateAnchor.getHeight() : 0;
        if (minHeight <= 0 && rowTemplateAnchor != null) {
            ViewGroup.LayoutParams source = rowTemplateAnchor.getLayoutParams();
            if (source != null && source.height > 0) {
                minHeight = source.height;
            }
        }
        if (minHeight <= 0 && textTemplate != null) minHeight = textTemplate.getHeight();
        if (minHeight <= 0) minHeight = dp(activity, 48);
        row.setMinimumHeight(minHeight);

        TextView label = new TextView(activity);
        copyTextStyle(label, textTemplate, false, false);
        label.setText(title);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(label, labelLp);

        TextView valueView = new TextView(activity);
        copyTextStyle(valueView, textTemplate, false, true);
        valueView.setText(value);
        valueView.setSingleLine(true);
        valueView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        valueView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(valueView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        return new SettingsRowViews(row, valueView);
    }

    private void copyTextStyle(TextView target, TextView template, boolean bold, boolean alignEnd) {
        if (target == null) return;
        if (template != null) {
            target.setTextSize(TypedValue.COMPLEX_UNIT_PX, template.getTextSize());
            target.setTextColor(template.getTextColors());
            target.setTypeface(template.getTypeface());
            target.setIncludeFontPadding(template.getIncludeFontPadding());
            target.setLetterSpacing(template.getLetterSpacing());
        } else {
            target.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            target.setTextColor(Color.WHITE);
        }
        if (bold) target.setTypeface(Typeface.DEFAULT_BOLD);
        target.setGravity((alignEnd ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);
        target.setSingleLine(true);
    }

    private Drawable resolveHostPageBackground(Activity activity, View backgroundAnchor, View cropAnchor) {
        Drawable wall = captureHostWallpaperBackground(activity, cropAnchor);
        if (wall != null) return wall;
        Drawable resourceWall = captureHostWallpaperResourceBackground(activity, cropAnchor);
        if (resourceWall != null) return resourceWall;
        Drawable fromTree = findMeaningfulAncestorBackgroundDrawable(backgroundAnchor);
        if (fromTree != null) return fromTree;
        return buildFallbackHostBackground(activity);
    }

    private Drawable resolveRowTemplateBackground(View backgroundAnchor) {
        Drawable background = backgroundAnchor == null ? null : backgroundAnchor.getBackground();
        if (background != null) {
            Drawable cloned = cloneDrawable(background);
            if (cloned != null) return cloned;
        }
        return buildFallbackRowBackground(backgroundAnchor == null ? null : backgroundAnchor.getContext());
    }

    private Drawable findMeaningfulAncestorBackgroundDrawable(View backgroundAnchor) {
        ViewParent parent = backgroundAnchor == null ? null : backgroundAnchor.getParent();
        while (parent instanceof View) {
            View view = (View) parent;
            Drawable background = view.getBackground();
            int w = view.getWidth();
            int h = view.getHeight();
            if (background != null && w > 0 && h > 0 && HostBackgroundColorPolicy.isMeaningfulDrawable(background)) {
                Drawable cloned = cloneDrawable(background);
                if (cloned != null) return cloned;
            }
            parent = view.getParent();
        }
        return null;
    }

    private Drawable captureHostWallpaperBackground(Activity activity, View cropAnchor) {
        try {
            if (activity == null) return null;
            View target = resolveBackgroundCaptureTarget(activity, cropAnchor);
            if (target == null || target.getWidth() <= 0 || target.getHeight() <= 0) return null;
            View wall = findHostWallpaperView(activity);
            if (wall == null || wall.getWidth() <= 0 || wall.getHeight() <= 0) {
                return captureWindowBackground(activity, target);
            }
            Bitmap bitmap = Bitmap.createBitmap(target.getWidth(), target.getHeight(), Bitmap.Config.ARGB_8888);
            bitmap.setDensity(activity.getResources().getDisplayMetrics().densityDpi);
            Canvas canvas = new Canvas(bitmap);
            if (target != wall) {
                int[] wallLoc = new int[2];
                int[] targetLoc = new int[2];
                wall.getLocationOnScreen(wallLoc);
                target.getLocationOnScreen(targetLoc);
                canvas.translate(wallLoc[0] - targetLoc[0], wallLoc[1] - targetLoc[1]);
            }
            wall.draw(canvas);
            BitmapDrawable drawable = new BitmapDrawable(activity.getResources(), bitmap);
            drawable.setTargetDensity(activity.getResources().getDisplayMetrics());
            drawable.setGravity(Gravity.FILL);
            return drawable;
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "capture host wallpaper failed: " + throwable.getMessage());
            return null;
        }
    }

    private View resolveBackgroundCaptureTarget(Activity activity, View cropAnchor) {
        if (cropAnchor != null && cropAnchor.getWidth() > 0 && cropAnchor.getHeight() > 0) return cropAnchor;
        View content = activity == null ? null : activity.findViewById(android.R.id.content);
        if (content != null && content.getWidth() > 0 && content.getHeight() > 0) return content;
        Window window = activity == null ? null : activity.getWindow();
        View decor = window == null ? null : window.getDecorView();
        return decor != null && decor.getWidth() > 0 && decor.getHeight() > 0 ? decor : null;
    }

    private Drawable captureWindowBackground(Activity activity, View target) {
        try {
            if (activity == null || target == null || target.getWidth() <= 0 || target.getHeight() <= 0) return null;
            Window window = activity.getWindow();
            View decor = window == null ? null : window.getDecorView();
            if (decor == null || decor.getWidth() <= 0 || decor.getHeight() <= 0) return null;
            Drawable background = decor.getBackground();
            if (background == null || !HostBackgroundColorPolicy.isMeaningfulDrawable(background)) {
                background = findMeaningfulWindowBackgroundDrawable(decor);
            }
            if (background == null || !HostBackgroundColorPolicy.isMeaningfulDrawable(background)) return null;
            return captureScreenAlignedDrawable(activity, background, decor, target);
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "capture window background failed: " + throwable.getMessage());
            return null;
        }
    }

    private Drawable findMeaningfulWindowBackgroundDrawable(View root) {
        if (root == null) return null;
        ArrayList<View> stack = new ArrayList<>();
        stack.add(root);
        while (!stack.isEmpty()) {
            View view = stack.remove(stack.size() - 1);
            if (view == null || view.getVisibility() != View.VISIBLE) continue;
            if (SETTINGS_OVERLAY_TAG.equals(view.getTag()) || view.getTag() instanceof SettingsOverlayState) continue;
            Drawable background = view.getBackground();
            if (background != null && view.getWidth() > 0 && view.getHeight() > 0 && HostBackgroundColorPolicy.isMeaningfulDrawable(background)) {
                return background;
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = group.getChildCount() - 1; i >= 0; i--) {
                    stack.add(group.getChildAt(i));
                }
            }
        }
        return null;
    }

    private Drawable captureHostWallpaperResourceBackground(Activity activity, View cropAnchor) {
        try {
            if (activity == null) return null;
            Drawable resourceWall = loadHostWallpaperResource(activity);
            if (resourceWall == null) return null;
            View target = resolveBackgroundCaptureTarget(activity, cropAnchor);
            if (target == null || target.getWidth() <= 0 || target.getHeight() <= 0) return null;
            Window window = activity.getWindow();
            View decor = window == null ? null : window.getDecorView();
            View boundsAnchor = decor != null && decor.getWidth() > 0 && decor.getHeight() > 0 ? decor : target;
            return captureScreenAlignedDrawable(activity, resourceWall, boundsAnchor, target);
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "capture wallpaper resource failed: " + throwable.getMessage());
            return null;
        }
    }

    private Drawable captureScreenAlignedDrawable(Activity activity, Drawable source, View boundsAnchor, View target) {
        if (activity == null || source == null || boundsAnchor == null || target == null) return null;
        if (boundsAnchor.getWidth() <= 0 || boundsAnchor.getHeight() <= 0 || target.getWidth() <= 0 || target.getHeight() <= 0) return null;
        Bitmap bitmap = Bitmap.createBitmap(target.getWidth(), target.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.setDensity(activity.getResources().getDisplayMetrics().densityDpi);
        Canvas canvas = new Canvas(bitmap);
        int[] boundsLoc = new int[2];
        int[] targetLoc = new int[2];
        boundsAnchor.getLocationOnScreen(boundsLoc);
        target.getLocationOnScreen(targetLoc);
        canvas.translate(boundsLoc[0] - targetLoc[0], boundsLoc[1] - targetLoc[1]);

        Drawable drawable = cloneDrawable(source);
        boolean drawingOriginal = drawable == null;
        if (drawingOriginal) drawable = source;
        Rect oldBounds = drawingOriginal ? drawable.copyBounds() : null;
        try {
            drawable.setBounds(0, 0, boundsAnchor.getWidth(), boundsAnchor.getHeight());
            drawable.draw(canvas);
        } finally {
            if (drawingOriginal && oldBounds != null) {
                drawable.setBounds(oldBounds);
            }
        }
        BitmapDrawable bitmapDrawable = new BitmapDrawable(activity.getResources(), bitmap);
        bitmapDrawable.setTargetDensity(activity.getResources().getDisplayMetrics());
        bitmapDrawable.setGravity(Gravity.FILL);
        return bitmapDrawable;
    }

    private View findHostWallpaperView(Activity activity) {
        try {
            ViewGroup content = activity == null ? null : activity.findViewById(android.R.id.content);
            View directLayer = findDirectHostWallpaperLayer(content);
            return directLayer;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private View findDirectHostWallpaperLayer(ViewGroup content) {
        if (content == null) return null;
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE) continue;
            String className = child.getClass().getName();
            if (isKnownHostWallpaperLayerClass(className)) return child;
        }
        return null;
    }

    private boolean isKnownHostWallpaperLayerClass(String className) {
        return "s30".equals(className) || "Q3.k".equals(className);
    }

    private Drawable loadHostWallpaperResource(Activity activity) {
        if (activity == null) return null;
        try {
            String pkg = activity.getPackageName();
            int id = activity.getResources().getIdentifier("wallpaper_1", "drawable", pkg);
            if (id == 0) return null;
            return activity.getResources().getDrawable(id, activity.getTheme());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Drawable buildFallbackHostBackground(Context context) {
        GradientDrawable d = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{0xFFD8F7F1, 0xFFA7E4E0, 0xFF7DCBC1, 0xFF45CFA4}
        );
        d.setShape(GradientDrawable.RECTANGLE);
        return d;
    }

    private Drawable buildFallbackRowBackground(Context context) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x4DFFFFFF);
        if (context != null) {
            float density = context.getResources().getDisplayMetrics().density;
            d.setCornerRadius(16f * density);
        } else {
            d.setCornerRadius(16f);
        }
        d.setStroke(1, 0x22FFFFFF);
        return d;
    }

    private void showOffsetInputDialog(Activity activity, double currentValue, StringValueCallback callback) {
        showTextInputDialog(activity, "时间轴偏移", "可输入正负小数，例如 -0.5", formatOffsetSeconds(currentValue),
            InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED,
            callback);
    }

    private void showIntegerInputDialog(Activity activity, String title, String hint, String initialValue, StringValueCallback callback) {
        showTextInputDialog(activity, title, hint, initialValue, InputType.TYPE_CLASS_NUMBER, callback);
    }

    private void showTextInputDialog(Activity activity, String title, String hint, String initialValue, int inputType, StringValueCallback callback) {
        if (activity == null || callback == null) return;
        EditText input = new EditText(activity);
        input.setText(initialValue == null ? "" : initialValue);
        input.setHint(hint == null ? "" : hint);
        input.setSingleLine(true);
        input.setInputType(inputType);
        input.setSelectAllOnFocus(true);
        int pad = dp(activity, 16);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(input)
            .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
            .setPositiveButton("保存", (dialog, which) -> {
                String value = input.getText() == null ? "" : input.getText().toString().trim();
                callback.onValue(value);
            })
            .show();
    }

    private void showFontSizeChooser(Activity activity, int currentFontSize, IntValueCallback callback) {
        if (activity == null || callback == null) return;
        final String[] labels = new String[]{"默认", "20", "24", "28", "32", "自定义"};
        int checked = 0;
        for (int i = 1; i < labels.length - 1; i++) {
            if (currentFontSize == safeParseInt(labels[i])) {
                checked = i;
                break;
            }
        }
        final int initialChecked = checked;
        new AlertDialog.Builder(activity)
            .setTitle("弹幕大小")
            .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                dialog.dismiss();
                if (which == labels.length - 1) {
                    showCustomFontSizeInput(activity, currentFontSize, callback);
                } else {
                    int value = which == 0 ? -1 : safeParseInt(labels[which]);
                    callback.onValue(value);
                }
            })
            .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
            .show();
    }

    private void showCustomFontSizeInput(Activity activity, int currentFontSize, IntValueCallback callback) {
        if (activity == null || callback == null) return;
        String initial = currentFontSize > 0 ? String.valueOf(currentFontSize) : "";
        showTextInputDialog(activity, "自定义弹幕大小", "请输入 8-80 之间的整数", initial,
            InputType.TYPE_CLASS_NUMBER, value -> {
                int parsed = safeParseInt(value);
                if (parsed < 8 || parsed > 80) {
                    Toast.makeText(activity, "弹幕大小范围应为 8-80", Toast.LENGTH_SHORT).show();
                    return;
                }
                callback.onValue(parsed);
            });
    }

    private interface StringValueCallback {
        void onValue(String value);
    }

    private interface IntValueCallback {
        void onValue(int value);
    }

    private interface FilterSelectListener {
        void onSelect(String source);
    }

    private static final class SettingsOverlayState {
        final View hostPageContainer;
        final View hostPageRoot;
        final View backgroundAnchor;
        Runnable hostChangeGuard;
        ViewTreeObserver.OnPreDrawListener preDrawGuard;

        SettingsOverlayState(View hostPageContainer, View hostPageRoot, View backgroundAnchor) {
            this.hostPageContainer = hostPageContainer;
            this.hostPageRoot = hostPageRoot;
            this.backgroundAnchor = backgroundAnchor;
        }
    }

    private static final class SettingsRowViews {
        final LinearLayout row;
        final TextView value;

        SettingsRowViews(LinearLayout row, TextView value) {
            this.row = row;
            this.value = value;
        }
    }


    private String formatInjectionSettings(InjectionSettings settings) {
        if (settings == null) settings = new InjectionSettings(true, true, 0.0d, -1, 9978, false, 0, "");
        StringBuilder sb = new StringBuilder("设置：注入");
        sb.append(settings.injectionEnabled ? "开" : "关");
        sb.append(" · 自动推送");
        sb.append(settings.autoPushEnabled ? "开" : "关");
        sb.append(" · 时间轴 ").append(formatOffsetSeconds(settings.offsetSec)).append("s");
        sb.append(" · 大小 ").append(settings.fontSize > 0 ? settings.fontSize : "默认");
        sb.append(" · 端口 ").append(settings.shellPort);
        sb.append(" · 主题 ").append(settings.darkTheme ? "深色" : "白色");
        return sb.toString();
    }

    private String applyDanmakuParams(String danmakuUrl, InjectionSettings settings) throws Exception {
        String url = danmakuUrl == null ? "" : danmakuUrl.trim();
        if (settings == null || url.isEmpty()) return url;
        ArrayList<String> params = new ArrayList<>();
        if (Math.abs(settings.offsetSec) > 1e-6) {
            params.add("offset=" + urlEncode(formatOffsetSeconds(settings.offsetSec)));
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

    private String formatOffsetSeconds(double value) {
        if (Math.abs(value) < 1e-6) return "0";
        String formatted = String.format(Locale.US, "%.3f", value);
        while (formatted.contains(".") && formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        if (formatted.endsWith(".")) formatted = formatted.substring(0, formatted.length() - 1);
        return formatted;
    }

    private Double parseNullableDouble(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0.0d;
        try {
            return Double.parseDouble(raw.trim());
        } catch (Throwable throwable) {
            return null;
        }
    }

    private double safeParseDouble(String raw, double fallback) {
        try {
            return raw == null || raw.trim().isEmpty() ? fallback : Double.parseDouble(raw.trim());
        } catch (Throwable throwable) {
            return fallback;
        }
    }

    private String formatError(Throwable throwable) {
        if (throwable == null) return "未知错误";
        String message = throwable.getMessage();
        if (message == null || message.trim().isEmpty()) return throwable.getClass().getSimpleName();
        return throwable.getClass().getSimpleName() + " " + message;
    }

    private void startAutoPushLoopOnce(Activity activity) {
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
            while (moduleGenerationActive) {
                long delayMs = AUTO_POLL_FAST_MS;
                try {
                    if (!isPlaybackActivityVisible()) {
                        sleepAutoLoopQuietly(AUTO_POLL_DISABLED_MS);
                        continue;
                    }
                    InjectionSettings settings = readInjectionSettings(appContext, 9978);
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
                        log((pushed || recentSkipped || inFlightSkipped || notReady) ? Log.INFO : Log.WARN, TAG, row.message);
                        delayMs = (!notReady && !inFlightSkipped && !pushed && !recentSkipped)
                            ? AUTO_POLL_ERROR_MS
                            : selectAutoPollDelay(notReady && hasFreshPendingAutoPush(signature), pushed || recentSkipped, inFlightSkipped);
                    } else {
                        delayMs = selectAutoPollDelay(false, false, false);
                    }
                    sleepAutoLoopQuietly(delayMs);
                } catch (Throwable throwable) {
                    log(Log.WARN, TAG, "auto push loop failed: " + throwable.getMessage());
                    sleepAutoLoopQuietly(AUTO_POLL_ERROR_MS);
                }
            }
            synchronized (DanmuXposedModule.this) {
                autoLoopStarted = false;
            }
            log(Log.INFO, TAG, "xposed auto push loop stopped");
        }, "DanmuAutoPushLoop");
        loop.setDaemon(true);
        loop.start();
        log(Log.INFO, TAG, "xposed auto push loop started");
    }

    private void markActivityResumed(Activity activity) {
        if (activity == null) return;
        foregroundActivityIdentity = System.identityHashCode(activity);
    }

    private void markPlaybackActivity(Activity activity) {
        if (activity == null) return;
        int identity = System.identityHashCode(activity);
        playbackActivityVisible = true;
        if (identity != 0 && identity != lastPlaybackActivityIdentity) {
            lastPlaybackActivityIdentity = identity;
            cachedShellMediaPort = -1;
            cachedShellMediaPortUpdatedAtMs = 0L;
            resetAutoSignature("new playback activity");
        }
        autoLoopActivity = new WeakReference<>(activity);
        requestFastAutoPoll("playback activity active");
    }

    private void markActivityPaused(Activity activity) {
        if (activity == null) return;
        int identity = System.identityHashCode(activity);
        if (identity == foregroundActivityIdentity) foregroundActivityIdentity = 0;
        clearInjectionWatch(activity);
        if (identity == lastPlaybackActivityIdentity) {
            playbackActivityVisible = false;
            clearPendingAutoPush("");
            wakeAutoLoop();
            log(Log.INFO, TAG, "playback activity paused; keep successful auto signature");
        }
    }

    private void markActivityDestroyed(Activity activity) {
        if (activity == null) return;
        int identity = System.identityHashCode(activity);
        if (identity == foregroundActivityIdentity) foregroundActivityIdentity = 0;
        clearInjectionWatch(activity);
        if (identity == lastPlaybackActivityIdentity) {
            playbackActivityVisible = false;
            autoLoopActivity = null;
            lastPlaybackActivityIdentity = 0;
            cachedShellMediaPort = -1;
            cachedShellMediaPortUpdatedAtMs = 0L;
            resetAutoSignature("playback activity destroyed");
            wakeAutoLoop();
        }
    }

    private boolean isActivityActiveForInjection(Activity activity) {
        if (activity == null || activity.isFinishing()) return false;
        if (activity.isDestroyed()) return false;
        if (System.identityHashCode(activity) != foregroundActivityIdentity) return false;
        Window window = activity.getWindow();
        View decor = window == null ? null : window.getDecorView();
        // 短文本锚点扫描不依赖 WindowFocus。FongMi 首帧常在 onResume 后先完成布局、稍后才拿到焦点，
        // 如果这里强制 hasWindowFocus，就会出现“必须点弹窗返回后才出现”的现象。
        return decor != null && decor.isShown();
    }

    private boolean isPlaybackActivityVisible() {
        if (!playbackActivityVisible) return false;
        WeakReference<Activity> ref = autoLoopActivity;
        Activity activity = ref == null ? null : ref.get();
        if (activity == null || activity.isFinishing()) return false;
        if (activity.isDestroyed()) return false;
        int identity = System.identityHashCode(activity);
        return identity == foregroundActivityIdentity && (lastPlaybackActivityIdentity == 0 || identity == lastPlaybackActivityIdentity);
    }

    private void requestFastAutoPoll(String reason) {
        long until = System.currentTimeMillis() + AUTO_POLL_FAST_WINDOW_MS;
        if (until > autoLoopFastUntilMs) autoLoopFastUntilMs = until;
        wakeAutoLoop();
    }

    private void wakeAutoLoop() {
        synchronized (autoLoopWakeLock) {
            autoLoopWakeLock.notifyAll();
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
        log(Log.INFO, TAG, "auto push signature reset: " + reason);
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
            for (String key : new ArrayList<>(inFlightPushes.keySet())) {
                Long startedAt = inFlightPushes.get(key);
                if (startedAt == null || now - startedAt > PUSH_IN_FLIGHT_TTL_MS) inFlightPushes.remove(key);
            }
            for (String key : new ArrayList<>(recentPushes.keySet())) {
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

    private void recordLastPush(Context context, String message, String url) {
        lastPushInfo = message == null ? "" : message;
        lastPushUrl = url == null ? "" : url;
        lastPushAtMs = System.currentTimeMillis();
        lastPushSummary = buildPushSummary(message);
        synchronized (pushHistory) {
            pushHistory.addFirst(message == null ? "" : message);
            while (pushHistory.size() > MAX_PUSH_HISTORY) pushHistory.removeLast();
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

    private String headerStageTitle(int stage) {
        switch (stage) {
            case STAGE_DRAMA:
                return "选择匹配来源";
            case STAGE_EPISODE:
                return "选择剧集推送";
            case STAGE_SEARCH:
            default:
                return "搜索当前播放";
        }
    }

    private String formatPushSummary() {
        if (lastPushAtMs <= 0L || lastPushSummary.isEmpty()) return "";
        long agoMs = System.currentTimeMillis() - lastPushAtMs;
        long agoSec = agoMs / 1000L;
        String ago;
        if (agoSec < 60L) ago = "刚刚";
        else if (agoSec < 3600L) ago = (agoSec / 60L) + "分钟前";
        else ago = (agoSec / 3600L) + "小时前";
        return ago + " " + lastPushSummary;
    }

    private String formatPushTimeChip() {
        if (lastPushAtMs <= 0L || lastPushSummary.isEmpty()) return "暂无推送";
        long agoMs = Math.max(0L, System.currentTimeMillis() - lastPushAtMs);
        long agoSec = agoMs / 1000L;
        if (agoSec < 60L) return "刚刚";
        if (agoSec < 3600L) return (agoSec / 60L) + "分钟前";
        return (agoSec / 3600L) + "小时前";
    }

    private String formatPushSummaryCompact() {
        if (lastPushAtMs <= 0L || lastPushSummary.isEmpty()) return "暂无推送记录";
        long agoMs = Math.max(0L, System.currentTimeMillis() - lastPushAtMs);
        long agoSec = agoMs / 1000L;
        String ago;
        if (agoSec < 60L) ago = "刚刚";
        else if (agoSec < 3600L) ago = (agoSec / 60L) + "分钟前";
        else ago = (agoSec / 3600L) + "小时前";

        String summary = lastPushSummary == null ? "" : lastPushSummary.trim();
        summary = summary.replaceFirst("^已匹配", "").trim();
        String countText = "";
        Matcher countMatcher = Pattern.compile("（([^（）]*弹幕[^（）]*)）").matcher(summary);
        if (countMatcher.find()) {
            countText = countMatcher.group(1).replace("弹幕数", "弹幕").trim();
            summary = countMatcher.replaceFirst("").trim();
        }
        int sourceDetail = summary.indexOf("·【");
        if (sourceDetail < 0) sourceDetail = summary.indexOf(" · 【");
        if (sourceDetail > 0) summary = summary.substring(0, sourceDetail).trim();
        summary = summary.replaceAll("\\s*·\\s*", " · ").trim();
        if (summary.isEmpty()) summary = "已推送";
        if (!countText.isEmpty()) summary = summary + " · " + countText;
        return ago + "  " + summary;
    }

    private String formatLastPushInfo() {
        return formatLastPushInfo(null);
    }

    private String formatLastPushInfo(Context context) {
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

    private void showPushHistoryDialog(Activity activity, DanmuTheme t, View notifyButton, TextView notifyDot) {
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

        List<String> entries;
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

    private void notifyAutoPush(String message) {
        WeakReference<Activity> ref = autoLoopActivity;
        Activity activity = ref == null ? null : ref.get();
        if (activity == null || activity.isFinishing()) return;
        if (activity.isDestroyed()) return;
        activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
    }

    private ShellMedia readShellMedia(int preferredPort) {
        ArrayList<Integer> ports = new ArrayList<>();
        int preferred = preferredPort > 0 && preferredPort <= 65535 ? preferredPort : -1;
        if (preferred > 0) ports.add(preferred);
        int cachedPort = cachedShellMediaPort;
        long now = System.currentTimeMillis();
        if (cachedPort >= 9978 && cachedPort <= 9998 && now - cachedShellMediaPortUpdatedAtMs <= AUTO_MEDIA_PORT_CACHE_TTL_MS && cachedPort != preferred) {
            ports.add(cachedPort);
        }
        for (int port = 9978; port <= 9998; port++) {
            if (!ports.contains(port)) ports.add(port);
        }
        ShellMedia urlOnly = null;
        for (int port : ports) {
            ShellMedia media = readShellMediaFromPort(port);
            if (media == null) continue;
            if (!media.title.isEmpty()) {
                cachedShellMediaPort = port;
                cachedShellMediaMisses = 0;
                cachedShellMediaPortUpdatedAtMs = now;
                return media;
            }
            if (urlOnly == null) urlOnly = media;
        }
        if (cachedPort > 0) cachedShellMediaMisses++;
        if (cachedShellMediaMisses > 1) {
            cachedShellMediaPort = -1;
            cachedShellMediaPortUpdatedAtMs = 0L;
        }
        return urlOnly;
    }

    private ShellMedia readShellMediaFromPort(int port) {
        try {
            String body = httpGet("http://127.0.0.1:" + port + "/media", 700, 1500);
            JSONObject root = new JSONObject(body);
            String title = readString(root, "title", "name", "vodName", "vod_name");
            String episodeText = readString(root, "artist", "episodeTitle", "episodeName", "remarks", "remark", "subtitle");
            int episodeNumber = readInt(root, "episode", "episodeNumber", "number", "sort", "index");
            String url = readString(root, "url", "path", "playUrl", "play_url");
            if (episodeNumber <= 0) episodeNumber = extractEpisodeNumber(episodeText);
            if (episodeNumber <= 0) episodeNumber = extractEpisodeNumber(title);
            if (episodeNumber <= 0) episodeNumber = extractEpisodeNumber(url);
            String cleanedTitle = normalizeSearchTitle(title);
            if (!cleanedTitle.isEmpty()) title = cleanedTitle;
            int state = readPlaybackState(root);
            long position = readLong(root, "position", "currentPosition", "current_position", "pos");
            long duration = readLong(root, "duration", "totalDuration", "total_duration");
            if (!title.isEmpty()) return new ShellMedia(port, title, episodeText, episodeNumber, url, state, position, duration);
            if (!url.isEmpty()) return new ShellMedia(port, title, episodeText, episodeNumber, url, state, position, duration);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String httpGet(String url, int connectTimeoutMs, int readTimeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setUseCaches(false);
        try {
            int code = conn.getResponseCode();
            InputStream input = code >= 200 && code <= 299 ? conn.getInputStream() : conn.getErrorStream();
            String body = readAll(input);
            if (code < 200 || code > 299) throw new IllegalStateException("HTTP " + code + ": " + body);
            return body;
        } finally {
            conn.disconnect();
        }
    }

    private String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private boolean looksLikePlaybackPage(Activity activity, ViewGroup decor, Anchor anchor) {
        if (anchor == null || anchor.parent == null) return false;
        String className = activity.getClass().getName();
        if (isKnownPlaybackActivityName(className)) return true;
        if (!looksLikeShellControlRow(anchor.parent)) return false;
        if (decor.getWidth() > decor.getHeight() && decor.getWidth() > dp(activity, 560)) return true;
        int orientation = activity.getResources().getConfiguration().orientation;
        if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) return true;
        int flags = activity.getWindow() != null ? activity.getWindow().getAttributes().flags : 0;
        return (flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0 ||
            activity.getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
            activity.getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
    }

    private boolean isKnownPlaybackActivityName(String className) {
        if (className == null || className.isEmpty()) return false;
        if (className.endsWith(".VideoActivity")) return true;
        String lower = className.toLowerCase(Locale.ROOT);
        for (String hint : ACTIVITY_HINTS) {
            if (lower.contains(hint) && lower.contains("activity")) return true;
        }
        return false;
    }

    private View createButton(Activity activity, View anchorView) {
        TextView button = new TextView(activity);
        button.setTag(BUTTON_TAG);
        button.setClickable(true);
        button.setSoundEffectsEnabled(true);
        button.setFocusable(true);
        button.setContentDescription("APP弹幕");
        button.setText("APP弹幕");
        button.setSingleLine(true);
        button.setGravity(Gravity.CENTER);
        copyHostControlStyle(activity, button, anchorView);
        button.setElevation(0f);
        button.setStateListAnimator(null);
        button.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        button.setId(View.generateViewId());
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setTranslationY(0f);
        return button;
    }


    private void copyHostControlStyle(Activity activity, TextView button, View anchorView) {
        button.setTextSize(TypedValue.COMPLEX_UNIT_PX, resolveAnchorTextSizePx(activity, anchorView));
        button.setTextColor(resolveIconColor(anchorView));
        button.setIncludeFontPadding(false);
        button.setEllipsize(null);
        int fallbackPadding = dp(activity, 8);
        button.setPadding(fallbackPadding, fallbackPadding, fallbackPadding, fallbackPadding);
        if (anchorView != null) {
            Drawable background = cloneDrawable(anchorView.getBackground());
            if (background != null) {
                button.setBackground(background);
            } else {
                applyBorderlessControlBackground(activity, button);
            }
            button.setPadding(anchorView.getPaddingLeft(), anchorView.getPaddingTop(), anchorView.getPaddingRight(), anchorView.getPaddingBottom());
            button.setTextAlignment(anchorView.getTextAlignment());
        } else {
            applyBorderlessControlBackground(activity, button);
        }
        if (anchorView instanceof TextView) {
            TextView anchorText = (TextView) anchorView;
            button.setTextSize(TypedValue.COMPLEX_UNIT_PX, anchorText.getTextSize());
            button.setTextColor(anchorText.getCurrentTextColor());
            button.setIncludeFontPadding(anchorText.getIncludeFontPadding());
            if (anchorText.getTypeface() != null) button.setTypeface(anchorText.getTypeface());
            button.setGravity(anchorText.getGravity());
            button.setShadowLayer(anchorText.getShadowRadius(), anchorText.getShadowDx(), anchorText.getShadowDy(), anchorText.getShadowColor());
            int maxLines = anchorText.getMaxLines();
            if (maxLines > 0 && maxLines < Integer.MAX_VALUE) button.setMaxLines(maxLines);
            int maxEms = anchorText.getMaxEms();
            if (maxEms > 0 && maxEms < Integer.MAX_VALUE) button.setMaxEms(maxEms);
            android.text.TextUtils.TruncateAt ellipsize = anchorText.getEllipsize();
            if (ellipsize != null) button.setEllipsize(ellipsize);
        } else {
            button.setGravity(Gravity.CENTER);
            button.setSingleLine(true);
        }
    }

    private Drawable cloneDrawable(Drawable drawable) {
        if (drawable == null) return null;
        try {
            Drawable.ConstantState state = drawable.getConstantState();
            if (state != null) return state.newDrawable().mutate();
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void applyBorderlessControlBackground(Activity activity, View view) {
        try {
            TypedValue outValue = new TypedValue();
            if (activity.getTheme() != null && activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true) && outValue.resourceId != 0) {
                view.setBackgroundResource(outValue.resourceId);
            }
        } catch (Throwable ignored) {
        }
    }

    private int resolveIconColor(View anchorView) {
        if (anchorView instanceof TextView) {
            try {
                int color = ((TextView) anchorView).getCurrentTextColor();
                int alpha = Color.alpha(color);
                if (alpha > 32) return color;
            } catch (Throwable ignored) {
            }
        }
        return Color.WHITE;
    }

    private float resolveAnchorTextSizePx(Activity activity, View anchorView) {
        if (anchorView instanceof TextView) {
            try {
                float sizePx = ((TextView) anchorView).getTextSize();
                if (sizePx > 0f) return sizePx;
            } catch (Throwable ignored) {
            }
        }
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, activity.getResources().getDisplayMetrics());
    }

    private boolean addButtonToDecor(ViewGroup decor, View button, Anchor anchor) {
        Activity activity = (Activity) button.getContext();
        if (anchor != null && anchor.parent != null && anchor.view != null) {
            ViewGroup parent = anchor.parent;
            ViewGroup.LayoutParams lp = cloneLayoutParamsForInsert(activity, anchor.view, button);
            int index = parent.indexOfChild(anchor.view);
            parent.addView(button, index >= 0 ? Math.min(index + 1, parent.getChildCount()) : parent.getChildCount(), lp);
            log(Log.INFO, TAG, "APP danmu button injected near anchor: " + anchor.text);
            return true;
        } else {
            log(Log.WARN, TAG, "no valid control-bar anchor, wait for control row");
            return false;
        }
    }

    private ViewGroup.LayoutParams cloneLayoutParamsForInsert(Activity activity, View anchorView, View insertedView) {
        ViewGroup.LayoutParams source = anchorView.getLayoutParams();
        boolean textButton = insertedView instanceof TextView;
        int width = textButton && source != null ? source.width : ViewGroup.LayoutParams.WRAP_CONTENT;
        int height = textButton && source != null ? source.height : ViewGroup.LayoutParams.WRAP_CONTENT;
        if (!textButton) {
            int fallback = dp(activity, 28);
            if (source != null && source.height > 0) fallback = clamp(source.height, dp(activity, 22), dp(activity, 32));
            width = fallback;
            height = fallback;
        }
        ViewParent parent = anchorView.getParent();
        ViewGroup.LayoutParams lp;
        if (source instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams src = (LinearLayout.LayoutParams) source;
            LinearLayout.LayoutParams out = new LinearLayout.LayoutParams(width, height);
            out.setMargins(src.leftMargin, src.topMargin, src.rightMargin, src.bottomMargin);
            out.gravity = src.gravity;
            out.weight = textButton ? 0f : src.weight;
            lp = out;
        } else if (source instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams src = (FrameLayout.LayoutParams) source;
            FrameLayout.LayoutParams out = new FrameLayout.LayoutParams(width, height);
            out.gravity = src.gravity;
            out.setMargins(src.leftMargin, src.topMargin, src.rightMargin, src.bottomMargin);
            lp = out;
        } else if (parent instanceof LinearLayout) {
            LinearLayout.LayoutParams out = new LinearLayout.LayoutParams(width, height);
            out.setMargins(0, 0, 0, 0);
            lp = out;
        } else {
            lp = new ViewGroup.LayoutParams(width, height);
        }
        return lp;
    }

    private View findTaggedButton(View root) {
        if (root == null) return null;
        Object tag = root.getTag();
        if (BUTTON_TAG.equals(tag)) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findTaggedButton(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private Anchor findAnchor(View root) {
        if (!(root instanceof ViewGroup)) return null;
        // FongMi / OK影视 的底栏按钮都有稳定资源 ID，优先命中同一条底部控制行。
        Anchor byControlId = findShellControlIdAnchor((ViewGroup) root);
        if (byControlId != null) return byControlId;
        // 兜底：如果某个壳只暴露 bottom 容器，继续在容器内按短文本控制项找同父锚点。
        Anchor byContainer = findContainerAnchor((ViewGroup) root);
        if (byContainer != null) return byContainer;
        // 最后兜底：短文本遍历，要求同父容器内至少 3 个控制项，避免跑到左侧/顶部。
        return findBestControlBarAnchor(root, 0, null);
    }

    private Anchor findShellControlIdAnchor(ViewGroup root) {
        Context context = root.getContext();
        String pkg = context == null ? "" : context.getPackageName();
        for (String name : SHELL_CONTROL_ANCHOR_IDS) {
            try {
                int id = root.getResources().getIdentifier(name, "id", pkg);
                if (id == 0) continue;
                View found = root.findViewById(id);
                if (!(found instanceof TextView)) continue;
                if (!(found.getParent() instanceof ViewGroup)) continue;
                if (found.getVisibility() != View.VISIBLE || !found.isShown()) continue;
                ViewGroup parent = (ViewGroup) found.getParent();
                if (!looksLikeShellControlRow(parent)) continue;
                Rect rect = new Rect();
                found.getGlobalVisibleRect(rect);
                String text = readViewText(found);
                if (text.isEmpty()) text = name;
                return new Anchor(found, parent, rect, text, anchorPriority(text));
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private boolean looksLikeShellControlRow(ViewGroup parent) {
        if (parent == null || parent.getVisibility() != View.VISIBLE || !parent.isShown()) return false;
        String className = parent.getClass().getName();
        if (className.contains("RecyclerView") || parent instanceof ListView || parent instanceof GridView || parent instanceof GridLayout) return false;
        return countDirectShellControlIds(parent) >= 3 || countControlBarTexts(parent, 0) >= 3;
    }

    private int countDirectShellControlIds(ViewGroup parent) {
        if (parent == null) return 0;
        int count = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (isShellControlId(parent.getChildAt(i))) count++;
        }
        return count;
    }

    private boolean isShellControlId(View view) {
        if (view == null || view.getId() == View.NO_ID) return false;
        try {
            String name = view.getResources().getResourceEntryName(view.getId());
            for (String controlId : SHELL_CONTROL_ANCHOR_IDS) {
                if (controlId.equals(name)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private Anchor findContainerAnchor(ViewGroup root) {
        Context context = root.getContext();
        String pkg = context == null ? "" : context.getPackageName();
        for (String name : CONTAINER_ANCHOR_IDS) {
            try {
                int id = root.getResources().getIdentifier(name, "id", pkg);
                if (id == 0) continue;
                View found = root.findViewById(id);
                if (!(found instanceof ViewGroup)) continue;
                if (found.getVisibility() != View.VISIBLE || !found.isShown()) continue;
                Anchor nested = findBestControlBarAnchor(found, 0, null);
                if (nested != null) return nested;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private Anchor findBestControlBarAnchor(View root, int depth, Anchor best) {
        if (root == null || depth > 12 || root.getVisibility() != View.VISIBLE || !root.isShown()) return best;
        if (root instanceof TextView) {
            TextView textView = (TextView) root;
            String text = readViewText(textView);
            if (isAnchorText(text) && text.length() < 10 && textView.getParent() instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) textView.getParent();
                if (countSiblingAnchorTexts(parent) >= 3) {
                    Rect rect = new Rect();
                    textView.getGlobalVisibleRect(rect);
                    int priority = anchorPriority(text);
                    Anchor candidate = new Anchor(textView, parent, rect, text, priority);
                    if (best == null || candidate.priority > best.priority ||
                        (candidate.priority == best.priority && candidate.rect.bottom > best.rect.bottom)) {
                        best = candidate;
                    }
                    if (priority >= 5) return best;
                }
            }
            return best;
        }
        if (root instanceof ViewGroup) {
            String className = root.getClass().getName();
            if (className.contains("RecyclerView") || root instanceof ListView || root instanceof GridView || root instanceof GridLayout) return best;
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                best = findBestControlBarAnchor(group.getChildAt(i), depth + 1, best);
                if (best != null && best.priority >= 5) return best;
            }
        }
        return best;
    }

    private int countSiblingAnchorTexts(ViewGroup parent) {
        if (parent == null) return 0;
        int count = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof TextView && isAnchorText(readViewText(child))) count++;
        }
        return count;
    }

    private int countControlBarTexts(View root, int depth) {
        if (root == null || depth > 4 || root.getVisibility() != View.VISIBLE || !root.isShown()) return 0;
        if (root instanceof TextView) {
            return isControlBarText(readViewText(root)) ? 1 : 0;
        }
        if (!(root instanceof ViewGroup)) return 0;
        ViewGroup group = (ViewGroup) root;
        String className = group.getClass().getName();
        if (className.contains("RecyclerView") || group instanceof ListView || group instanceof GridView || group instanceof GridLayout) return 0;
        int count = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            count += countControlBarTexts(group.getChildAt(i), depth + 1);
        }
        return count;
    }

    private boolean isAnchorText(String text) {
        String value = normalizeControlText(text);
        if (value.isEmpty()) return false;
        for (String anchor : ANCHOR_TEXTS) {
            if (value.equals(anchor) || value.startsWith(anchor + "(")) return true;
        }
        return false;
    }

    private boolean isControlBarText(String text) {
        String value = normalizeControlText(text);
        if (value.isEmpty()) return false;
        for (String control : CONTROL_BAR_TEXTS) {
            if (value.equals(control) || value.startsWith(control + "(") || value.startsWith(control + "：")) {
                return true;
            }
        }
        return false;
    }

    private String normalizeControlText(String text) {
        if (text == null) return "";
        return text.trim().replace('（', '(').replace('）', ')');
    }

    private int anchorPriority(String text) {
        String value = normalizeControlText(text);
        if ("弹幕".equals(value) || "弹幕搜索".equals(value) || value.startsWith("弹幕(")) return 5;
        if ("片尾".equals(value)) return 4;
        if ("选集".equals(value)) return 3;
        if ("更多".equals(value)) return 2;
        if ("下集".equals(value)) return 1;
        return 0;
    }

    private String readViewText(View view) {
        if (!(view instanceof TextView)) return "";
        CharSequence raw = ((TextView) view).getText();
        return raw == null ? "" : raw.toString().trim();
    }

    private String readString(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (!obj.has(key)) continue;
            String value = obj.optString(key, "").trim();
            if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) return value;
        }
        return "";
    }

    private int readInt(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (!obj.has(key)) continue;
            Object raw = obj.opt(key);
            if (raw instanceof Number) return ((Number) raw).intValue();
            if (raw instanceof String) {
                try {
                    return Integer.parseInt(((String) raw).trim());
                } catch (Throwable ignored) {
                    // try next key
                }
            }
        }
        return -1;
    }


    private int readPlaybackState(JSONObject obj) {
        int state = readInt(obj, "state", "playState", "play_state", "status");
        if (state >= 0) return state;
        if (readBoolean(obj, "isPlaying", "playing", "play", "is_playing")) return 3;
        String label = readString(obj, "state", "playState", "play_state", "status").toLowerCase(Locale.ROOT);
        if (label.contains("playing") || label.contains("play") || label.contains("播放中")) return 3;
        if (label.contains("buffer") || label.contains("缓冲")) return 6;
        if (label.contains("pause") || label.contains("paused") || label.contains("暂停")) return 1;
        if (label.contains("ready") || label.contains("prepare") || label.contains("准备")) return 2;
        if (label.contains("stop") || label.contains("idle") || label.contains("未播放")) return 0;
        return state;
    }

    private boolean readBoolean(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (!obj.has(key)) continue;
            Object raw = obj.opt(key);
            if (raw instanceof Boolean) return (Boolean) raw;
            if (raw instanceof Number) return ((Number) raw).intValue() != 0;
            if (raw instanceof String) {
                String value = ((String) raw).trim();
                if ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value)) return true;
            }
        }
        return false;
    }

    private String normalizeToken(String value) {
        String token = value == null ? "" : value.trim();
        if (token.equalsIgnoreCase("null") || token.equalsIgnoreCase("undefined")) return "";
        while (token.startsWith("/")) token = token.substring(1);
        return token.trim();
    }

    private long readLong(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (!obj.has(key)) continue;
            Object raw = obj.opt(key);
            if (raw instanceof Number) return ((Number) raw).longValue();
            if (raw instanceof String) {
                try {
                    return Long.parseLong(((String) raw).trim());
                } catch (Throwable ignored) {
                    // try next key
                }
            }
        }
        return -1L;
    }

    private String joinNonBlank(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(part.trim());
        }
        return sb.toString();
    }

    private int dp(Activity activity, int value) {
        float density = activity.getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
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

    private static final class Anchor {
        final View view;
        final ViewGroup parent;
        final Rect rect;
        final String text;
        final int priority;

        Anchor(View view, ViewGroup parent, Rect rect, String text, int priority) {
            this.view = view;
            this.parent = parent;
            this.rect = rect == null ? new Rect() : rect;
            this.text = text == null ? "" : text;
            this.priority = priority;
        }
    }

    private static final class ShellMedia {
        final int port;
        final String title;
        final String episodeText;
        final int episodeNumber;
        final String url;
        final int state;
        final long position;
        final long duration;

        ShellMedia(int port, String title, String episodeText, int episodeNumber, String url, int state, long position, long duration) {
            this.port = port;
            this.title = title == null ? "" : title;
            this.episodeText = episodeText == null ? "" : episodeText;
            this.episodeNumber = episodeNumber;
            this.url = url == null ? "" : url;
            this.state = state;
            this.position = position;
            this.duration = duration;
        }

        ShellMedia withPort(int newPort) {
            return new ShellMedia(newPort, title, episodeText, episodeNumber, url, state, position, duration);
        }

        String displayEpisode() {
            if (!episodeText.isEmpty()) return episodeText;
            return episodeNumber > 0 ? "第" + episodeNumber + "集" : "";
        }

        String matchSignature() {
            return title.trim() + "|" + displayEpisode().trim() + "|" + episodeNumber;
        }

        String signature() {
            return matchSignature();
        }

        boolean isReadyForDanmaku() {
            if (state == 3) return true;
            return state < 0 && position > 0L;
        }

        String playbackStateLabel() {
            String label;
            if (state == 3) label = "播放中";
            else if (state == 6) label = "缓冲中";
            else if (state == 1) label = "暂停";
            else if (state == 0) label = "未播放";
            else if (state == 2) label = "准备中";
            else label = state >= 0 ? "状态" + state : "等待播放";
            if (position >= 0L && duration > 0L) return label + " " + (position / 1000L) + "/" + (duration / 1000L) + "s";
            if (position >= 0L) return label + " " + (position / 1000L) + "s";
            return label;
        }
    }

    private static final class PendingAutoPush {
        final String matchSignature;
        final EpisodeCandidate candidate;
        final int shellPort;
        final long createdAtMs;

        PendingAutoPush(String matchSignature, EpisodeCandidate candidate, int shellPort, long createdAtMs) {
            this.matchSignature = matchSignature == null ? "" : matchSignature;
            this.candidate = candidate;
            this.shellPort = shellPort;
            this.createdAtMs = createdAtMs;
        }
    }

    private static final class CandidateHandle {
        final int type;
        final String handle;
        final String label;
        final String source;

        CandidateHandle(int type, String handle, String label) {
            this(type, handle, label, "");
        }

        CandidateHandle(int type, String handle, String label, String source) {
            this.type = type;
            this.handle = handle;
            this.label = label;
            this.source = source == null ? "" : source;
        }
    }

    private static final class SourceFilter {
        final String source;
        final String label;
        int count;

        SourceFilter(String source, String label, int count) {
            this.source = source == null ? "" : source;
            this.label = label == null ? "" : label;
            this.count = count;
        }

        String displayName() {
            return label.isEmpty() ? source : label;
        }
    }

    private static final class DirectSearch {
        final String coreBase;
        final List<AnimeRef> animes;

        DirectSearch(String coreBase, List<AnimeRef> animes) {
            this.coreBase = coreBase == null ? "" : coreBase;
            this.animes = animes == null ? new ArrayList<>() : animes;
        }
    }

    private static final class AnimeRef {
        final String coreBase;
        final String animeId;
        final String bangumiId;
        final String title;
        final int episodeCount;
        final String source;
        final String type;

        AnimeRef(String coreBase, String animeId, String bangumiId, String title, int episodeCount, String source, String type) {
            this.coreBase = coreBase == null ? "" : coreBase;
            this.animeId = animeId == null ? "" : animeId;
            this.bangumiId = bangumiId == null ? "" : bangumiId;
            this.title = title == null ? "" : title;
            this.episodeCount = episodeCount;
            this.source = source == null ? "" : source;
            this.type = type == null ? "" : type;
        }
    }

    private static final class EpisodeCandidate {
        final String name;
        final String episode;
        final int episodeNumber;
        final String source;
        final String url;

        EpisodeCandidate(String name, String episode, int episodeNumber, String source, String url) {
            this.name = name == null ? "" : name;
            this.episode = episode == null ? "" : episode;
            this.episodeNumber = episodeNumber;
            this.source = source == null ? "" : source;
            this.url = url == null ? "" : url;
        }

        String displayLabel() {
            return joinStatic(name, episode, source);
        }
    }

    private static String joinStatic(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(part.trim());
        }
        return sb.toString();
    }

    private static final class PushGuard {
        final boolean allowed;
        final String globalKey;
        final String sessionKey;
        final String reason;

        PushGuard(boolean allowed, String globalKey, String sessionKey, String reason) {
            this.allowed = allowed;
            this.globalKey = globalKey == null ? "" : globalKey;
            this.sessionKey = sessionKey == null ? "" : sessionKey;
            this.reason = reason == null ? "" : reason;
        }
    }

    private static final class InjectionSettings {
        final boolean injectionEnabled;
        final boolean autoPushEnabled;
        final double offsetSec;
        final int fontSize;
        final int shellPort;
        final boolean darkTheme;
        final int corePort;
        final String coreToken;
        final int dialogStyle;

        InjectionSettings(boolean injectionEnabled, boolean autoPushEnabled, double offsetSec, int fontSize, int shellPort, boolean darkTheme) {
            this(injectionEnabled, autoPushEnabled, offsetSec, fontSize, shellPort, darkTheme, 0, "", DIALOG_STYLE_CENTER);
        }

        InjectionSettings(boolean injectionEnabled, boolean autoPushEnabled, double offsetSec, int fontSize, int shellPort, boolean darkTheme, int corePort, String coreToken) {
            this(injectionEnabled, autoPushEnabled, offsetSec, fontSize, shellPort, darkTheme, corePort, coreToken, DIALOG_STYLE_CENTER);
        }

        InjectionSettings(boolean injectionEnabled, boolean autoPushEnabled, double offsetSec, int fontSize, int shellPort, boolean darkTheme, int corePort, String coreToken, int dialogStyle) {
            this.injectionEnabled = injectionEnabled;
            this.autoPushEnabled = autoPushEnabled;
            this.offsetSec = Math.abs(offsetSec) < 1e-6 ? 0.0d : offsetSec;
            this.fontSize = fontSize > 0 ? fontSize : -1;
            this.shellPort = shellPort > 0 && shellPort <= 65535 ? shellPort : 9978;
            this.darkTheme = darkTheme;
            this.corePort = corePort > 0 && corePort <= 65535 ? corePort : 0;
            this.coreToken = coreToken == null ? "" : coreToken.trim();
            this.dialogStyle = dialogStyle == DIALOG_STYLE_BOTTOM_SHEET ? DIALOG_STYLE_BOTTOM_SHEET : DIALOG_STYLE_CENTER;
        }

        String pushParamHint() {
            StringBuilder sb = new StringBuilder();
            if (Math.abs(offsetSec) > 1e-6) sb.append("偏移").append(formatOffsetStatic(offsetSec)).append("s");
            if (fontSize > 0) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append("大小").append(fontSize);
            }
            return sb.toString();
        }
    }

    private static String formatOffsetStatic(double value) {
        if (Math.abs(value) < 1e-6) return "0";
        String formatted = String.format(Locale.US, "%.3f", value);
        while (formatted.contains(".") && formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        if (formatted.endsWith(".")) formatted = formatted.substring(0, formatted.length() - 1);
        return formatted;
    }

    private static final class BridgeRow {
        final String status;
        final String message;
        final String payload;

        BridgeRow(String status, String message, String payload) {
            this.status = status == null ? "error" : status;
            this.message = message == null ? "" : message;
            this.payload = payload == null ? "" : payload;
        }
    }

    private static final class BridgeResult {
        boolean ok;
        String message = "";
        int selectedIndex = 0;
        final List<CandidateHandle> candidates = new ArrayList<>();
        final List<SourceFilter> filters = new ArrayList<>();
    }
}
