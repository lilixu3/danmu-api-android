package com.example.danmuapiapp.xposed;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.GridLayout;
import android.widget.FrameLayout;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.libxposed.api.XposedModule;

/**
 * LSPosed API 101 entry point. It runs inside the hooked OK影视/FongMi process.
 */
public class DanmuXposedModule extends XposedModule {
    private static final String TAG = "DanmuAppXposed";
    private static final String MODULE_PACKAGE = "com.example.danmuapiapp";
    private static final String BUTTON_TAG = "com.example.danmuapiapp.APP_DANMU_BUTTON";
    private static final String PREFS_INJECTION = "app_danmu_injection";
    private static final String KEY_INJECTION_ENABLED = "injection_enabled";
    private static final String KEY_AUTO_PUSH_ENABLED = "auto_push_enabled";
    private static final String KEY_CORE_PORT = "core_port";
    private static final String KEY_CORE_TOKEN = "core_token";
    private static final String KEY_OFFSET_SEC = "offset_sec";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_SHELL_PORT = "shell_port";
    private static final String KEY_UI_DARK_THEME = "ui_dark_theme";
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

    private static final String[] ACTIVITY_HINTS = {
        "video", "player", "playback", "vod"
    };
    private static final String[] ANCHOR_TEXTS = {
        "片尾", "弹幕", "弹幕搜索", "选集", "更多", "下集"
    };
    private static final String[] CONTROL_BAR_TEXTS = {
        "EXO", "硬解", "软解", "字幕", "音轨", "视轨", "原始", "刷新", "循环",
        "片头", "片尾", "弹幕", "弹幕搜索", "选集", "更多", "下集"
    };
    private static final String[] SHELL_CONTROL_ANCHOR_IDS = {
        "ending", "danmaku", "opening", "video", "audio", "text"
    };
    private static final String[] CONTAINER_ANCHOR_IDS = {
        "bottom"
    };

    private String packageName = "";
    private boolean lifecycleHooked = false;
    private volatile boolean autoLoopStarted = false;
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
    private final Map<Integer, android.view.ViewTreeObserver.OnGlobalLayoutListener> injectionWatchers = new ConcurrentHashMap<>();
    private volatile String cachedCoreBase = "";
    private volatile String lastPushInfo = "";
    private volatile String lastPushUrl = "";
    private volatile long lastPushAtMs = 0L;
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
        Method onResume = Activity.class.getDeclaredMethod("onResume");
        hook(onResume).intercept(chain -> {
            Object result = chain.proceed();
            Object thisObject = chain.getThisObject();
            if (thisObject instanceof Activity) {
                markActivityResumed((Activity) thisObject);
                scheduleInject((Activity) thisObject);
            }
            return result;
        });

        Method onWindowFocusChanged = Activity.class.getDeclaredMethod("onWindowFocusChanged", boolean.class);
        hook(onWindowFocusChanged).intercept(chain -> {
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
        hook(onPause).intercept(chain -> {
            Object result = chain.proceed();
            Object thisObject = chain.getThisObject();
            if (thisObject instanceof Activity) {
                markActivityPaused((Activity) thisObject);
            }
            return result;
        });

        Method onDestroy = Activity.class.getDeclaredMethod("onDestroy");
        hook(onDestroy).intercept(chain -> {
            Object result = chain.proceed();
            Object thisObject = chain.getThisObject();
            if (thisObject instanceof Activity) {
                markActivityDestroyed((Activity) thisObject);
            }
            return result;
        });
    }

    private void scheduleInject(Activity activity) {
        try {
            if (activity == null) return;
            installInjectionWatch(activity);
            Window window = activity.getWindow();
            if (window == null) return;
            View decor = window.getDecorView();
            if (decor == null) return;
            decor.post(() -> injectButton(activity));
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
                        if (activity.isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed())) {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) return;
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
                log(Log.INFO, TAG, "APP danmu button injected into " + activity.getClass().getName());
                clearInjectionWatch(activity);
            }
            startAutoPushLoopOnce(activity);
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "inject button failed: " + throwable.getMessage());
        }
    }

    private void showManualSearchDialog(Activity activity) {
        try {
            InjectionSettings bootSettings = readInjectionSettings(activity, 9978);
            final int[] shellPort = new int[]{bootSettings.shellPort};
            final boolean[] darkTheme = new boolean[]{bootSettings.darkTheme};
            final UiPalette palette = resolveUiPalette(darkTheme[0]);

            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(activity, 12);
            root.setPadding(pad, dp(activity, 10), pad, dp(activity, 10));
            root.setBackground(makeRoundRect(palette.rootBg, 24f, 1, palette.panelStroke));

            LinearLayout header = new LinearLayout(activity);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = createStyledText(activity, "APP弹幕", 17f, palette.text, true);
            TextView badge = createInfoChip(activity, "播放页工具", palette.chipBg, palette.chipText, palette.chipStroke);
            Button closeButton = createActionButton(activity, "关闭", false, darkTheme[0]);
            header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            header.addView(badge);
            LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(activity, 58), dp(activity, 30));
            closeLp.setMargins(dp(activity, 6), 0, 0, 0);
            header.addView(closeButton, closeLp);
            root.addView(header, matchWrapWithBottom(activity, 8));

            LinearLayout searchRow = new LinearLayout(activity);
            searchRow.setOrientation(LinearLayout.HORIZONTAL);
            searchRow.setGravity(Gravity.CENTER_VERTICAL);
            EditText keywordInput = createCompactInput(activity, "输入剧名 / 自动读取", "", darkTheme[0]);
            Button searchButton = createActionButton(activity, "搜索", true, darkTheme[0]);
            searchRow.addView(keywordInput, new LinearLayout.LayoutParams(0, dp(activity, 42), 1f));
            LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(dp(activity, 72), dp(activity, 42));
            searchLp.setMargins(dp(activity, 8), 0, 0, 0);
            searchRow.addView(searchButton, searchLp);
            root.addView(searchRow, matchWrapWithBottom(activity, 7));

            LinearLayout chipRow = new LinearLayout(activity);
            chipRow.setOrientation(LinearLayout.HORIZONTAL);
            chipRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView episodeText = createInfoChip(activity, "", palette.secondaryBg, palette.muted, palette.panelStroke);
            episodeText.setVisibility(View.GONE);
            TextView pushInfoText = createInfoChip(activity, formatLastPushInfo(activity), palette.successBg, palette.successText, palette.successStroke);
            chipRow.addView(pushInfoText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 32)));
            root.addView(chipRow, matchWrapWithBottom(activity, 6));

            LinearLayout actions = new LinearLayout(activity);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            Button backButton = createActionButton(activity, "返回", false, darkTheme[0]);
            backButton.setVisibility(View.GONE);
            Button actionButton = createActionButton(activity, "自动推送", true, darkTheme[0]);
            Button settingsButton = createActionButton(activity, "设置", false, darkTheme[0]);
            actions.addView(backButton, compactMainButtonLp(activity, 0.65f));
            actions.addView(actionButton, compactMainButtonLp(activity, 1.05f));
            actions.addView(settingsButton, compactMainButtonLp(activity, 0.65f));
            root.addView(actions, matchWrapWithBottom(activity, 6));

            TextView statusText = createStyledText(activity, "待搜索", 11.5f, palette.muted, false);
            statusText.setSingleLine(true);
            statusText.setPadding(dp(activity, 2), 0, 0, dp(activity, 3));
            root.addView(statusText);

            ScrollView resultsScroll = new ScrollView(activity);
            resultsScroll.setFillViewport(false);
            resultsScroll.setVerticalScrollBarEnabled(true);
            resultsScroll.setScrollbarFadingEnabled(false);
            resultsScroll.setSmoothScrollingEnabled(true);
            resultsScroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
            resultsScroll.setBackground(makeRoundRect(palette.panelBg, 14f, 1, palette.panelStroke));
            resultsScroll.setPadding(dp(activity, 6), dp(activity, 6), dp(activity, 6), 0);
            resultsScroll.setOnTouchListener((v, event) -> {
                ViewParent parent = v.getParent();
                if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                return false;
            });
            LinearLayout resultsContainer = new LinearLayout(activity);
            resultsContainer.setOrientation(LinearLayout.VERTICAL);
            resultsContainer.setPadding(0, 0, 0, dp(activity, 48));
            resultsScroll.addView(resultsContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            root.addView(resultsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                clamp((int) (activity.getResources().getDisplayMetrics().heightPixels * 0.50f), dp(activity, 300), dp(activity, 560))
            ));

            ScrollView episodeScroll = new ScrollView(activity);
            episodeScroll.setVisibility(View.GONE);
            episodeScroll.setFillViewport(false);
            episodeScroll.setVerticalScrollBarEnabled(true);
            episodeScroll.setScrollbarFadingEnabled(false);
            episodeScroll.setSmoothScrollingEnabled(true);
            episodeScroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
            episodeScroll.setBackground(makeRoundRect(palette.panelBg, 14f, 1, palette.panelStroke));
            episodeScroll.setPadding(dp(activity, 6), dp(activity, 6), dp(activity, 6), 0);
            episodeScroll.setOnTouchListener((v, event) -> {
                ViewParent parent = v.getParent();
                if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                return false;
            });
            GridLayout episodeGrid = new GridLayout(activity);
            episodeGrid.setColumnCount(9);
            episodeGrid.setUseDefaultMargins(false);
            episodeGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
            episodeGrid.setPadding(0, 0, 0, dp(activity, 48));
            ArrayList<CandidateHandle> compactHandles = new ArrayList<>();
            episodeScroll.addView(episodeGrid, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            root.addView(episodeScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                clamp((int) (activity.getResources().getDisplayMetrics().heightPixels * 0.26f), dp(activity, 160), dp(activity, 240))
            ));

            ArrayList<String> labels = new ArrayList<>();
            ArrayList<CandidateHandle> handles = new ArrayList<>();
            ArrayList<String> animeLabels = new ArrayList<>();
            ArrayList<CandidateHandle> animeHandles = new ArrayList<>();
            final int[] mode = new int[]{MODE_ANIME};
            final String[] currentEpisode = new String[]{""};
            final int[] selectedEpisodeIndex = new int[]{0};
            TextView settingsText = new TextView(activity);
            settingsText.setVisibility(View.GONE);
            settingsText.setText(formatInjectionSettings(readInjectionSettings(activity, shellPort[0])));
            final Runnable[] renderAnimeResults = new Runnable[1];
            renderAnimeResults[0] = () -> {
                resultsContainer.removeAllViews();
                ArrayList<String> sourceLabels = !animeLabels.isEmpty() ? animeLabels : labels;
                ArrayList<CandidateHandle> sourceHandles = !animeHandles.isEmpty() ? animeHandles : handles;
                if (sourceLabels.isEmpty()) {
                    TextView empty = createStyledText(activity, "无剧名结果，可换关键词再搜", 12f, palette.muted, false);
                    empty.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
                    empty.setBackground(makeRoundRect(palette.secondaryBg, 12f, 1, palette.secondaryStroke));
                    resultsContainer.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    return;
                }
                for (int i = 0; i < sourceLabels.size(); i++) {
                    final CandidateHandle candidate = i < sourceHandles.size() ? sourceHandles.get(i) : null;
                    final String label = sourceLabels.get(i);
                    TextView row = createStyledText(activity, (i + 1) + ". " + label, 12.5f, palette.text, false);
                    row.setSingleLine(false);
                    row.setMinHeight(dp(activity, 36));
                    row.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
                    row.setBackground(makeRoundRect(palette.secondaryBg, 12f, 1, palette.secondaryStroke));
                    row.setOnClickListener(v -> {
                        if (candidate == null) return;
                        if (mode[0] == MODE_ANIME) {
                            loadAnimeDetail(activity, candidate, currentEpisode[0], resultsScroll, resultsContainer, episodeScroll, episodeGrid, compactHandles, selectedEpisodeIndex, shellPort[0], mode, backButton, actionButton, searchButton, statusText, pushInfoText, darkTheme[0]);
                        } else {
                            int index = handles.indexOf(candidate);
                            if (index >= 0) selectedEpisodeIndex[0] = index;
                            pushCandidate(activity, candidate, shellPort[0], statusText, pushInfoText);
                        }
                    });
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.bottomMargin = dp(activity, 6);
                    resultsContainer.addView(row, lp);
                }
                resultsScroll.post(() -> resultsScroll.scrollTo(0, 0));
            };

            Runnable restoreAnimeList = () -> {
                mode[0] = MODE_ANIME;
                compactHandles.clear();
                selectedEpisodeIndex[0] = 0;
                episodeGrid.removeAllViews();
                episodeScroll.setVisibility(View.GONE);
                resultsScroll.setVisibility(View.VISIBLE);
                backButton.setVisibility(View.GONE);
                searchButton.setText("搜索");
                actionButton.setText("自动推送");
                pushInfoText.setText(formatLastPushInfo(activity));
                statusText.setText(animeLabels.isEmpty() ? "待搜索" : "点剧名展开集数");
                renderAnimeResults[0].run();
            };

            AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(root)
                .create();
            closeButton.setOnClickListener(v -> dialog.dismiss());

            Runnable searchAction = () -> {
                String keyword = keywordInput.getText() == null ? "" : keywordInput.getText().toString().trim();
                if (keyword.isEmpty()) {
                    Toast.makeText(activity, "先输入剧名", Toast.LENGTH_SHORT).show();
                    return;
                }
                mode[0] = MODE_ANIME;
                compactHandles.clear();
                selectedEpisodeIndex[0] = 0;
                episodeGrid.removeAllViews();
                episodeScroll.setVisibility(View.GONE);
                resultsScroll.setVisibility(View.VISIBLE);
                backButton.setVisibility(View.GONE);
                statusText.setText("搜索中…");
                searchButton.setEnabled(false);
                actionButton.setText("自动推送");
                new Thread(() -> {
                    BridgeResult result = queryBridgeAnimeSearch(activity, keyword);
                    activity.runOnUiThread(() -> {
                        searchButton.setEnabled(true);
                        statusText.setText(result.message);
                        labels.clear();
                        handles.clear();
                        animeLabels.clear();
                        animeHandles.clear();
                        if (result.ok) {
                            handles.addAll(result.candidates);
                            animeHandles.addAll(result.candidates);
                            for (CandidateHandle candidate : result.candidates) {
                                labels.add(candidate.label);
                                animeLabels.add(candidate.label);
                            }
                        }
                        renderAnimeResults[0].run();
                    });
                }, "DanmuSearchAnime").start();
            };

            backButton.setOnClickListener(v -> restoreAnimeList.run());
            searchButton.setOnClickListener(v -> searchAction.run());
            settingsButton.setOnClickListener(v -> showInjectionSettingsDialog(activity, shellPort, settingsText));
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
                    int height = activity.getResources().getDisplayMetrics().heightPixels;
                    window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                    window.setLayout((int) (width * 0.82f), (int) (height * 0.92f));
                }
                pushInfoText.setText(formatLastPushInfo(activity));
                new Thread(() -> {
                    ShellMedia media = readShellMedia(shellPort[0]);
                    activity.runOnUiThread(() -> {
                        pushInfoText.setText(formatLastPushInfo(activity));
                        if (media != null) {
                            shellPort[0] = media.port;
                            settingsText.setText(formatInjectionSettings(readInjectionSettings(activity, shellPort[0])));
                            currentEpisode[0] = media.displayEpisode();
                            int currentNo = extractEpisodeNumber(currentEpisode[0]);
                            if (keywordInput.getText() == null || keywordInput.getText().toString().trim().isEmpty()) {
                                keywordInput.setText(normalizeDisplayTitle(media.title).isEmpty() ? media.title : normalizeDisplayTitle(media.title));
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
                String label = joinNonBlank(anime.title, countLabel, anime.source, anime.type);
                if (label.isEmpty()) label = "剧名 " + (result.candidates.size() + 1);
                result.candidates.add(new CandidateHandle(MODE_ANIME, handle, label));
            }
        } catch (Throwable throwable) {
            result.ok = false;
            result.message = "弹幕 API 核心搜索失败：" + formatError(throwable);
        }
        return result;
    }

    private void loadAnimeDetail(
        Activity activity,
        CandidateHandle anime,
        String episodeHint,
        ScrollView resultsScroll,
        LinearLayout resultsContainer,
        ScrollView episodeScroll,
        GridLayout episodeGrid,
        ArrayList<CandidateHandle> compactHandles,
        final int[] selectedEpisodeIndex,
        int shellPort,
        int[] mode,
        Button backButton,
        Button actionButton,
        Button searchButton,
        TextView statusText,
        TextView pushInfoText,
        boolean darkTheme
    ) {
        statusText.setText("正在加载剧集：" + anime.label);
        searchButton.setEnabled(false);
        new Thread(() -> {
            BridgeResult result = loadAnimeDetailDirect(anime.handle, episodeHint);
            activity.runOnUiThread(() -> {
                searchButton.setEnabled(true);
                statusText.setText(result.message);
                if (result.ok && !result.candidates.isEmpty()) {
                    mode[0] = MODE_EPISODE;
                    compactHandles.clear();
                    compactHandles.addAll(result.candidates);
                    int targetIndex = clamp(result.selectedIndex, 0, result.candidates.size() - 1);
                    selectedEpisodeIndex[0] = targetIndex;
                    backButton.setVisibility(View.VISIBLE);
                    actionButton.setText("推送" + shortEpisodeLabel(compactHandles.get(targetIndex), targetIndex));
                    pushInfoText.setText(formatLastPushInfo(activity));
                    renderEpisodeGrid(activity, episodeGrid, compactHandles, selectedEpisodeIndex, shellPort, statusText, pushInfoText, actionButton, darkTheme);
                    episodeScroll.setVisibility(View.VISIBLE);
                    resultsScroll.setVisibility(View.GONE);
                    scrollEpisodeGridToIndex(activity, episodeScroll, targetIndex);
                } else {
                    mode[0] = MODE_ANIME;
                    selectedEpisodeIndex[0] = 0;
                    compactHandles.clear();
                    episodeGrid.removeAllViews();
                    episodeScroll.setVisibility(View.GONE);
                    resultsScroll.setVisibility(View.VISIBLE);
                    backButton.setVisibility(View.GONE);
                    actionButton.setText("自动推送");
                }
            });
        }, "DanmuAnimeDetail").start();
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
            recordLastPush(context, message, danmakuUrl);
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
                    List<AnimeRef> animes = parseAnimeSearch(coreBase, httpGet(url, 1800, 12000));
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
        String raw = httpGet(anime.coreBase + "/api/v2/bangumi/" + urlEncode(id), 1800, 12000);
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
            String source = readString(item, "source", "sourceName", "provider", "api", "from");
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


    private LinearLayout createCard(Activity activity, int color, float radiusDp, int strokeWidthDp, int strokeColor, int horizontalPad, int verticalPad) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(makeRoundRect(color, radiusDp, strokeWidthDp, strokeColor));
        card.setPadding(horizontalPad, verticalPad, horizontalPad, verticalPad);
        return card;
    }

    private TextView createStyledText(Activity activity, String text, float sizeSp, int color, boolean bold) {
        TextView tv = new TextView(activity);
        tv.setText(text == null ? "" : text);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        tv.setSingleLine(false);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private Button createActionButton(Activity activity, String text, boolean primary) {
        return createActionButton(activity, text, primary, false);
    }

    private Button createActionButton(Activity activity, String text, boolean primary, boolean darkTheme) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextSize(12.5f);
        button.setTextColor(primary ? 0xFFFFFFFF : (darkTheme ? 0xFFD9E8FF : 0xFF374151));
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setStateListAnimator(null);
            button.setElevation(0f);
        }
        button.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
        if (darkTheme) {
            button.setBackground(makeRoundRect(primary ? 0xFF2E7D32 : 0xFF1B2028, 999f, 2, primary ? 0xFF8BF7A5 : 0xAA8CA3B8));
        } else {
            button.setBackground(makeRoundRect(primary ? 0xFF2563EB : 0xFFFFFFFF, 999f, primary ? 1 : 2, primary ? 0xFF60A5FA : 0xFFD1D5DB));
        }
        return button;
    }

    private Button createPillButton(Activity activity, String text, boolean selected) {
        return createPillButton(activity, text, selected, false);
    }

    private Button createPillButton(Activity activity, String text, boolean selected, boolean darkTheme) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextSize(11.5f);
        button.setTextColor(selected ? 0xFFFFFFFF : (darkTheme ? 0xFFD9E8FF : 0xFF374151));
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setStateListAnimator(null);
            button.setElevation(0f);
        }
        button.setPadding(dp(activity, 4), 0, dp(activity, 4), 0);
        if (darkTheme) {
            button.setBackground(makeRoundRect(selected ? 0xFF2E7D32 : 0xFF1B2028, 999f, 2, selected ? 0xFF8BF7A5 : 0xAA8CA3B8));
        } else {
            button.setBackground(makeRoundRect(selected ? 0xFF2563EB : 0xFFFFFFFF, 999f, selected ? 2 : 2, selected ? 0xFF60A5FA : 0xFFD1D5DB));
        }
        return button;
    }

    private void applyThemeChoiceVisual(Activity activity, Button lightButton, Button darkButton, boolean darkSelected, boolean darkUi) {
        if (lightButton != null) {
            lightButton.setTextColor(darkSelected ? (darkUi ? 0xFFD9E8FF : 0xFF374151) : 0xFF111827);
            lightButton.setBackground(makeRoundRect(darkSelected ? (darkUi ? 0xFF171A20 : 0xFFFFFFFF) : 0xFFFFFFFF, 999f, darkSelected ? 1 : 2, darkSelected ? (darkUi ? 0xAA8CA3B8 : 0xFFD1D5DB) : 0xFF111827));
            lightButton.setAlpha(darkSelected ? 0.72f : 1f);
        }
        if (darkButton != null) {
            darkButton.setTextColor(darkSelected ? 0xFFFFFFFF : (darkUi ? 0xFFD9E8FF : 0xFF374151));
            darkButton.setBackground(makeRoundRect(darkSelected ? 0xFF111827 : (darkUi ? 0xFF171A20 : 0xFFFFFFFF), 999f, darkSelected ? 2 : 2, darkSelected ? 0xFF8CA3B8 : (darkUi ? 0xAA8CA3B8 : 0xFFD1D5DB)));
            darkButton.setAlpha(darkSelected ? 1f : 0.72f);
        }
    }

    private TextView createInfoChip(Activity activity, String text, int bgColor, int textColor, int strokeColor) {
        TextView chip = new TextView(activity);
        chip.setText(text == null ? "" : text);
        chip.setTextSize(11f);
        chip.setTextColor(textColor);
        chip.setSingleLine(true);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(activity, 10), 0, dp(activity, 10), 0);
        chip.setBackground(makeRoundRect(bgColor, 999f, 1, strokeColor));
        return chip;
    }

    private EditText createCompactInput(Activity activity, String hint, String value) {
        return createCompactInput(activity, hint, value, false);
    }

    private EditText createCompactInput(Activity activity, String hint, String value, boolean darkTheme) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint(hint == null ? "" : hint);
        input.setText(value == null ? "" : value);
        input.setTextSize(14.5f);
        input.setTextColor(darkTheme ? 0xFFF9F9F9 : 0xFF111827);
        input.setHintTextColor(darkTheme ? 0xFF7F8792 : 0xFF9CA3AF);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setSelectAllOnFocus(true);
        input.setMinHeight(dp(activity, 38));
        input.setBackground(makeRoundRect(darkTheme ? 0xFF101111 : 0xFFFFFFFF, 12f, 2, darkTheme ? 0x6655B3FF : 0xFF3B82F6));
        input.setPadding(dp(activity, 12), dp(activity, 5), dp(activity, 12), dp(activity, 5));
        return input;
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

    private LinearLayout.LayoutParams buttonRowLp(Activity activity, float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(activity, 34), weight);
        lp.setMargins(dp(activity, 2), 0, dp(activity, 2), 0);
        return lp;
    }

    private LinearLayout.LayoutParams compactMainButtonLp(Activity activity, float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(activity, 34), weight);
        lp.setMargins(dp(activity, 2), 0, dp(activity, 2), 0);
        return lp;
    }

    private GradientDrawable makeRoundRect(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(radiusDp * 1.0f);
        if (strokeWidthDp > 0) bg.setStroke(strokeWidthDp, strokeColor);
        return bg;
    }

    private UiPalette resolveUiPalette(boolean darkTheme) {
        return darkTheme
            ? new UiPalette(
                0xFF07080A,
                0xFF101111,
                0xFF171A20,
                0x668CA3B8,
                0xFFF9F9F9,
                0xFF9C9C9D,
                0xFF2E7D32,
                0xFF6DDC88,
                0xFFFFFFFF,
                0xFF171A20,
                0x668CA3B8,
                0xFFD9E8FF,
                0xFF171A20,
                0x668CA3B8,
                0xFFD9E8FF,
                0x1A22C55E,
                0xFF86EFAC,
                0x334ADE80
            )
            : new UiPalette(
                0xFFF8FAFC,
                0xFFFFFFFF,
                0xFFF8FAFC,
                0xFFE5E7EB,
                0xFF111827,
                0xFF4B5563,
                0xFF2563EB,
                0xFF60A5FA,
                0xFFFFFFFF,
                0xFFF8FAFC,
                0xFFE5E7EB,
                0xFF111827,
                0xFFFFFFFF,
                0xFFD1D5DB,
                0xFF374151,
                0xFFF0FDF4,
                0xFF166534,
                0xFFBBF7D0
            );
    }

    private static final class UiPalette {
        final int windowBg;
        final int rootBg;
        final int panelBg;
        final int panelStroke;
        final int text;
        final int muted;
        final int primary;
        final int primaryStroke;
        final int primaryText;
        final int secondaryBg;
        final int secondaryStroke;
        final int secondaryText;
        final int chipBg;
        final int chipStroke;
        final int chipText;
        final int successBg;
        final int successText;
        final int successStroke;

        UiPalette(int windowBg, int rootBg, int panelBg, int panelStroke, int text, int muted, int primary, int primaryStroke, int primaryText, int secondaryBg, int secondaryStroke, int secondaryText, int chipBg, int chipStroke, int chipText, int successBg, int successText, int successStroke) {
            this.windowBg = windowBg;
            this.rootBg = rootBg;
            this.panelBg = panelBg;
            this.panelStroke = panelStroke;
            this.text = text;
            this.muted = muted;
            this.primary = primary;
            this.primaryStroke = primaryStroke;
            this.primaryText = primaryText;
            this.secondaryBg = secondaryBg;
            this.secondaryStroke = secondaryStroke;
            this.secondaryText = secondaryText;
            this.chipBg = chipBg;
            this.chipStroke = chipStroke;
            this.chipText = chipText;
            this.successBg = successBg;
            this.successText = successText;
            this.successStroke = successStroke;
        }
    }

    private void adjustNumericInput(EditText input, double delta) {
        if (input == null) return;
        String raw = input.getText() == null ? "" : input.getText().toString().trim();
        double value = safeParseDouble(raw, 0.0d);
        input.setText(formatOffsetSeconds(value + delta));
        input.setSelection(input.getText().length());
    }

    private void renderEpisodeGrid(Activity activity, GridLayout episodeGrid, ArrayList<CandidateHandle> compactHandles, final int[] selectedEpisodeIndex, int shellPort, TextView statusText, TextView pushInfoText, Button actionButton, boolean darkTheme) {
        episodeGrid.removeAllViews();
        int columns = 9;
        episodeGrid.setColumnCount(columns);
        int marginPx = dp(activity, 2);
        int containerWidth = episodeGrid.getWidth();
        View parent = null;
        if (episodeGrid.getParent() instanceof View) parent = (View) episodeGrid.getParent();
        if (containerWidth <= 0 && parent != null) containerWidth = parent.getWidth();
        if (containerWidth <= 0) {
            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            containerWidth = (int) (screenWidth * 0.82f) - dp(activity, 36);
        }
        int horizontalPadding = episodeGrid.getPaddingLeft() + episodeGrid.getPaddingRight();
        if (parent != null) horizontalPadding += parent.getPaddingLeft() + parent.getPaddingRight();
        int usableWidth = Math.max(dp(activity, 240), containerWidth - horizontalPadding - dp(activity, 4));
        int totalMargins = marginPx * 2 * columns;
        int chipWidth = Math.max(dp(activity, 30), (usableWidth - totalMargins) / columns);
        int gridWidth = (chipWidth + marginPx * 2) * columns;
        episodeGrid.setMinimumWidth(0);
        episodeGrid.setClipToPadding(false);
        int selected = compactHandles.isEmpty() ? 0 : clamp(selectedEpisodeIndex[0], 0, compactHandles.size() - 1);
        selectedEpisodeIndex[0] = selected;
        for (int i = 0; i < compactHandles.size(); i++) {
            CandidateHandle candidate = compactHandles.get(i);
            final int index = i;
            TextView chip = createEpisodeChip(activity, shortEpisodeLabel(candidate, i), i == selected, darkTheme);
            chip.setContentDescription(i == selected ? "当前播放集 " + shortEpisodeLabel(candidate, i) : shortEpisodeLabel(candidate, i));
            chip.setOnClickListener(v -> {
                selectedEpisodeIndex[0] = index;
                if (actionButton != null) actionButton.setText("推送" + shortEpisodeLabel(candidate, index));
                statusText.setText("已选中" + shortEpisodeLabel(candidate, index) + "，点推送执行");
                renderEpisodeGrid(activity, episodeGrid, compactHandles, selectedEpisodeIndex, shellPort, statusText, pushInfoText, actionButton, darkTheme);
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = chipWidth;
            lp.height = dp(activity, 36);
            lp.setMargins(marginPx, marginPx, marginPx, marginPx);
            episodeGrid.addView(chip, lp);
        }
        ViewGroup.LayoutParams existing = episodeGrid.getLayoutParams();
        if (existing != null) {
            existing.width = Math.min(usableWidth, gridWidth);
            existing.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            episodeGrid.setLayoutParams(existing);
        }
    }

    private void scrollEpisodeGridToIndex(Activity activity, ScrollView episodeScroll, int index) {
        episodeScroll.post(() -> {
            int columns = 9;
            int row = Math.max(0, index) / columns;
            int y = Math.max(0, row * dp(activity, 40) - dp(activity, 24));
            episodeScroll.smoothScrollTo(0, y);
        });
    }

    private TextView createEpisodeChip(Activity activity, String label, boolean primary, boolean darkTheme) {
        TextView chip = new TextView(activity);
        chip.setText(label);
        chip.setTextSize(12f);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setTextColor(primary ? 0xFFFFFFFF : (darkTheme ? 0xFFDADDE3 : 0xFF374151));
        chip.setFocusable(true);
        chip.setClickable(true);
        chip.setSoundEffectsEnabled(true);
        chip.setBackground(makeChipBackground(primary, darkTheme));
        chip.setPadding(dp(activity, 4), dp(activity, 3), dp(activity, 4), dp(activity, 3));
        return chip;
    }

    private GradientDrawable makeChipBackground(boolean primary, boolean darkTheme) {
        GradientDrawable bg = new GradientDrawable();
        if (darkTheme) {
            bg.setColor(primary ? 0xFF2E7D32 : 0xFF171A20);
            bg.setStroke(primary ? 2 : 1, primary ? 0xFF6DDC88 : 0x88FFFFFF);
        } else {
            bg.setColor(primary ? 0xFF2563EB : 0xFFFFFFFF);
            bg.setStroke(primary ? 2 : 2, primary ? 0xFF60A5FA : 0xFFD1D5DB);
        }
        bg.setCornerRadius(18f);
        return bg;
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
        if (context == null) return new InjectionSettings(true, true, 0.0d, -1, normalizedFallbackPort, false, 0, "");
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_INJECTION, Context.MODE_PRIVATE);
            boolean injectionEnabled = true;
            boolean autoPush = true;
            double offset = safeParseDouble(prefs.getString(KEY_OFFSET_SEC, "0"), 0.0d);
            int fontSize = safeParseInt(prefs.getString(KEY_FONT_SIZE, ""));
            int storedPort = prefs.getInt(KEY_SHELL_PORT, normalizedFallbackPort);
            boolean darkTheme = prefs.getBoolean(KEY_UI_DARK_THEME, false);
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
                corePort = remotePrefs.getInt(KEY_CORE_PORT, 0);
                coreToken = normalizeToken(remotePrefs.getString(KEY_CORE_TOKEN, ""));
            }
            int port = storedPort > 0 && storedPort <= 65535 ? storedPort : normalizedFallbackPort;
            return new InjectionSettings(injectionEnabled, autoPush, offset, fontSize > 0 ? fontSize : -1, port, darkTheme, corePort, coreToken);
        } catch (Throwable throwable) {
            return new InjectionSettings(true, true, 0.0d, -1, normalizedFallbackPort, false, 0, "");
        }
    }

    private void saveInjectionSettings(Context context, InjectionSettings settings) {
        if (context == null || settings == null) return;
        try {
            String formattedOffset = formatOffsetSeconds(settings.offsetSec);
            String formattedFontSize = settings.fontSize > 0 ? String.valueOf(settings.fontSize) : "";
            context.getSharedPreferences(PREFS_INJECTION, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_OFFSET_SEC, formattedOffset)
                .putString(KEY_FONT_SIZE, formattedFontSize)
                .putInt(KEY_SHELL_PORT, settings.shellPort)
                .putBoolean(KEY_UI_DARK_THEME, settings.darkTheme)
                .apply();
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "save injection settings failed: " + throwable.getMessage());
        }
    }

    private void showInjectionSettingsDialog(Activity activity, int[] shellPortRef, TextView settingsText) {
        try {
            int fallbackPort = shellPortRef != null && shellPortRef.length > 0 ? shellPortRef[0] : 9978;
            InjectionSettings current = readInjectionSettings(activity, fallbackPort);
            final boolean[] darkTheme = new boolean[]{current.darkTheme};
            UiPalette palette = resolveUiPalette(darkTheme[0]);

            ScrollView settingsScroll = new ScrollView(activity);
            settingsScroll.setFillViewport(false);
            settingsScroll.setVerticalScrollBarEnabled(true);
            settingsScroll.setScrollbarFadingEnabled(false);

            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(activity, 12);
            root.setPadding(pad, dp(activity, 10), pad, dp(activity, 10));
            root.setBackground(makeRoundRect(palette.rootBg, 24f, 1, palette.panelStroke));
            settingsScroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            LinearLayout header = new LinearLayout(activity);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.addView(createStyledText(activity, "APP弹幕设置", 17f, palette.text, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            header.addView(createInfoChip(activity, "仅当前壳", palette.chipBg, palette.chipText, palette.chipStroke));
            root.addView(header, matchWrapWithBottom(activity, 8));

            LinearLayout themeCard = createCard(activity, palette.panelBg, 16f, 1, palette.panelStroke, dp(activity, 10), dp(activity, 8));
            themeCard.addView(createStyledText(activity, "界面主题", 12.5f, palette.muted, true));
            LinearLayout themeRow = new LinearLayout(activity);
            themeRow.setOrientation(LinearLayout.HORIZONTAL);
            themeRow.setGravity(Gravity.CENTER_VERTICAL);
            Button lightButton = createPillButton(activity, "白色", !darkTheme[0], darkTheme[0]);
            Button darkButton = createPillButton(activity, "黑色", darkTheme[0], darkTheme[0]);
            TextView themeHint = createStyledText(activity, darkTheme[0] ? "当前：深色" : "当前：白色", 11f, palette.muted, false);
            LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            themeRow.addView(lightButton, buttonRowLp(activity, 1f));
            themeRow.addView(darkButton, buttonRowLp(activity, 1f));
            themeRow.addView(themeHint, hintLp);
            themeCard.addView(themeRow, matchWrapWithTop(activity, 6));
            Runnable refreshThemeState = () -> {
                applyThemeChoiceVisual(activity, lightButton, darkButton, darkTheme[0], current.darkTheme);
                themeHint.setText(darkTheme[0] ? "当前：深色" : "当前：白色");
            };
            lightButton.setOnClickListener(v -> {
                darkTheme[0] = false;
                refreshThemeState.run();
            });
            darkButton.setOnClickListener(v -> {
                darkTheme[0] = true;
                refreshThemeState.run();
            });
            refreshThemeState.run();
            root.addView(themeCard, matchWrapWithBottom(activity, 7));

            LinearLayout autoCard = createCard(activity, palette.panelBg, 16f, 1, palette.panelStroke, dp(activity, 10), dp(activity, 6));
            autoCard.addView(createStyledText(activity, "自动推送：" + (current.autoPushEnabled ? "开" : "关") + "（仅 App 侧可改）", 13.5f, palette.text, true));
            autoCard.addView(createStyledText(activity, "播放器内只调整弹幕参数；注入和自动匹配开关请回 App 设置页修改", 10.5f, palette.muted, false), matchWrapWithTop(activity, 4));
            root.addView(autoCard, matchWrapWithBottom(activity, 7));

            LinearLayout offsetCard = createCard(activity, palette.panelBg, 16f, 1, palette.panelStroke, dp(activity, 10), dp(activity, 8));
            LinearLayout offsetTitle = new LinearLayout(activity);
            offsetTitle.setGravity(Gravity.CENTER_VERTICAL);
            offsetTitle.addView(createStyledText(activity, "时间轴偏移", 12.5f, palette.muted, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            offsetTitle.addView(createInfoChip(activity, "秒", palette.chipBg, palette.chipText, palette.chipStroke));
            offsetCard.addView(offsetTitle);
            LinearLayout offsetRow = new LinearLayout(activity);
            offsetRow.setOrientation(LinearLayout.HORIZONTAL);
            offsetRow.setGravity(Gravity.CENTER_VERTICAL);
            Button minusButton = createPillButton(activity, "-0.5", false, darkTheme[0]);
            Button resetOffsetButton = createPillButton(activity, "0", false, darkTheme[0]);
            Button plusButton = createPillButton(activity, "+0.5", false, darkTheme[0]);
            EditText offsetInput = createCompactInput(activity, "0", formatOffsetSeconds(current.offsetSec), darkTheme[0]);
            offsetRow.addView(minusButton, buttonRowLp(activity, 0.65f));
            offsetRow.addView(offsetInput, buttonRowLp(activity, 1.2f));
            offsetRow.addView(plusButton, buttonRowLp(activity, 0.65f));
            offsetRow.addView(resetOffsetButton, buttonRowLp(activity, 0.5f));
            offsetCard.addView(offsetRow, matchWrapWithTop(activity, 6));
            minusButton.setOnClickListener(v -> adjustNumericInput(offsetInput, -0.5d));
            plusButton.setOnClickListener(v -> adjustNumericInput(offsetInput, 0.5d));
            resetOffsetButton.setOnClickListener(v -> offsetInput.setText("0"));
            root.addView(offsetCard, matchWrapWithBottom(activity, 7));

            LinearLayout fontCard = createCard(activity, palette.panelBg, 16f, 1, palette.panelStroke, dp(activity, 10), dp(activity, 8));
            fontCard.addView(createStyledText(activity, "弹幕大小", 12.5f, palette.muted, true));
            EditText fontInput = createCompactInput(activity, "默认", current.fontSize > 0 ? String.valueOf(current.fontSize) : "", darkTheme[0]);
            LinearLayout presetRow = new LinearLayout(activity);
            presetRow.setOrientation(LinearLayout.HORIZONTAL);
            String[] presets = new String[]{"默认", "20", "24", "28", "32"};
            for (String preset : presets) {
                Button chip = createPillButton(activity, preset, preset.equals(String.valueOf(current.fontSize)) || (preset.equals("默认") && current.fontSize <= 0), darkTheme[0]);
                chip.setOnClickListener(v -> fontInput.setText("默认".equals(preset) ? "" : preset));
                presetRow.addView(chip, buttonRowLp(activity, 1f));
            }
            fontCard.addView(presetRow, matchWrapWithTop(activity, 6));
            fontCard.addView(fontInput, matchWrapWithTop(activity, 6));
            root.addView(fontCard, matchWrapWithBottom(activity, 7));

            LinearLayout portCard = createCard(activity, palette.panelBg, 16f, 1, palette.panelStroke, dp(activity, 10), dp(activity, 8));
            LinearLayout portTitleRow = new LinearLayout(activity);
            portTitleRow.setOrientation(LinearLayout.HORIZONTAL);
            portTitleRow.setGravity(Gravity.CENTER_VERTICAL);
            portTitleRow.addView(createStyledText(activity, "影视壳端口", 12.5f, palette.muted, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            portTitleRow.addView(createInfoChip(activity, "自动识别", palette.chipBg, palette.chipText, palette.chipStroke));
            portCard.addView(portTitleRow);
            EditText portInput = createCompactInput(activity, "9978", String.valueOf(current.shellPort), darkTheme[0]);
            portCard.addView(portInput, matchWrapWithTop(activity, 6));
            root.addView(portCard, matchWrapWithBottom(activity, 5));

            TextView note = createStyledText(activity, "参数随弹幕 URL 推送，不改弹幕文件", 11f, palette.muted, false);
            note.setPadding(dp(activity, 2), 0, 0, dp(activity, 8));
            root.addView(note);

            LinearLayout footer = new LinearLayout(activity);
            footer.setOrientation(LinearLayout.HORIZONTAL);
            footer.setGravity(Gravity.CENTER_VERTICAL);
            Button cancelButton = createActionButton(activity, "取消", false, darkTheme[0]);
            Button saveButton = createActionButton(activity, "保存", true, darkTheme[0]);
            footer.addView(cancelButton, buttonRowLp(activity, 1f));
            footer.addView(saveButton, buttonRowLp(activity, 1f));
            root.addView(footer, matchWrapWithTop(activity, 4));

            AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(settingsScroll)
                .create();
            cancelButton.setOnClickListener(v -> dialog.dismiss());
            saveButton.setOnClickListener(v -> {
                String offsetRaw = offsetInput.getText() == null ? "" : offsetInput.getText().toString().trim();
                String fontRaw = fontInput.getText() == null ? "" : fontInput.getText().toString().trim();
                String portRaw = portInput.getText() == null ? "" : portInput.getText().toString().trim();
                Double offset = parseNullableDouble(offsetRaw);
                if (offset == null) {
                    Toast.makeText(activity, "时间轴偏移请输入数字，可为负", Toast.LENGTH_SHORT).show();
                    return;
                }
                int fontSize = fontRaw.isEmpty() ? -1 : safeParseInt(fontRaw);
                if (!fontRaw.isEmpty() && fontSize <= 0) {
                    Toast.makeText(activity, "弹幕大小请输入正整数，或留空", Toast.LENGTH_SHORT).show();
                    return;
                }
                int port = portRaw.isEmpty() ? current.shellPort : safeParseInt(portRaw);
                if (port <= 0 || port > 65535) {
                    Toast.makeText(activity, "影视壳端口范围应为 1-65535", Toast.LENGTH_SHORT).show();
                    return;
                }
                InjectionSettings updated = new InjectionSettings(
                    current.injectionEnabled,
                    current.autoPushEnabled,
                    offset,
                    fontSize,
                    port,
                    darkTheme[0],
                    current.corePort,
                    current.coreToken
                );
                saveInjectionSettings(activity, updated);
                if (shellPortRef != null && shellPortRef.length > 0) shellPortRef[0] = updated.shellPort;
                if (settingsText != null) settingsText.setText(formatInjectionSettings(updated));
                Toast.makeText(activity, "APP弹幕设置已保存", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
            dialog.setOnShowListener(d -> {
                Window window = dialog.getWindow();
                if (window != null) {
                    int width = activity.getResources().getDisplayMetrics().widthPixels;
                    int height = activity.getResources().getDisplayMetrics().heightPixels;
                    window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                    window.setLayout((int) (width * 0.72f), (int) (height * 0.80f));
                }
            });
            dialog.show();
        } catch (Throwable throwable) {
            Toast.makeText(activity, "打开设置失败：" + throwable.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
            log(Log.WARN, TAG, "show injection settings failed: " + throwable.getMessage());
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
            while (true) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) return false;
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) return false;
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

    private void notifyAutoPush(String message) {
        WeakReference<Activity> ref = autoLoopActivity;
        Activity activity = ref == null ? null : ref.get();
        if (activity == null || activity.isFinishing()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) return;
        activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
    }

    private ShellMedia readShellMedia() {
        return readShellMedia(0);
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
        String className = activity.getClass().getName().toLowerCase(Locale.ROOT);
        for (String hint : ACTIVITY_HINTS) {
            if (className.contains(hint)) return true;
        }
        if (anchor == null) return false;
        if (decor.getWidth() > decor.getHeight() && decor.getWidth() > dp(activity, 560)) return true;
        int orientation = activity.getResources().getConfiguration().orientation;
        if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) return true;
        int flags = activity.getWindow() != null ? activity.getWindow().getAttributes().flags : 0;
        return (flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0 ||
            activity.getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
            activity.getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
    }

    private View createButton(Activity activity, View anchorView) {
        int textColor = resolveIconColor(anchorView);
        TextView button = new TextView(activity);
        button.setTag(BUTTON_TAG);
        button.setClickable(true);
        button.setSoundEffectsEnabled(true);
        button.setFocusable(true);
        button.setContentDescription("APP弹幕");
        button.setText("APP弹幕");
        button.setTextSize(TypedValue.COMPLEX_UNIT_PX, resolveAnchorTextSizePx(activity, anchorView));
        button.setTextColor(textColor);
        button.setSingleLine(true);
        if (anchorView instanceof TextView) {
            TextView anchorText = (TextView) anchorView;
            button.setIncludeFontPadding(anchorText.getIncludeFontPadding());
            if (anchorText.getTypeface() != null) button.setTypeface(anchorText.getTypeface());
        } else {
            button.setIncludeFontPadding(false);
        }
        button.setEllipsize(null);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setElevation(0f);
            button.setStateListAnimator(null);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            button.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            button.setId(View.generateViewId());
        }
        int height = dp(activity, 28);
        if (anchorView != null && anchorView.getLayoutParams() != null && anchorView.getLayoutParams().height > 0) {
            height = clamp(anchorView.getLayoutParams().height, dp(activity, 24), dp(activity, 34));
        }
        button.setMinHeight(height);
        button.setMinimumHeight(height);
        button.setPadding(dp(activity, 6), 0, dp(activity, 6), 0);
        button.setTranslationY(0f);
        return button;
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
            log(Log.WARN, TAG, "no valid control-bar anchor, skip floating injection");
            return false;
        }
    }

    private ViewGroup.LayoutParams cloneLayoutParamsForInsert(Activity activity, View anchorView, View insertedView) {
        ViewGroup.LayoutParams source = anchorView.getLayoutParams();
        int height = source != null ? source.height : ViewGroup.LayoutParams.WRAP_CONTENT;
        if (height <= 0 || height == ViewGroup.LayoutParams.MATCH_PARENT) height = dp(activity, 28);
        height = clamp(height, dp(activity, 22), dp(activity, 32));
        boolean textButton = insertedView instanceof TextView;
        int width = textButton ? ViewGroup.LayoutParams.WRAP_CONTENT : height;
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
                return new Anchor(found, parent, rect, text, anchorPriority(text), true);
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
                    Anchor candidate = new Anchor(textView, parent, rect, text, priority, false);
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
        if ("片尾".equals(value)) return 5;
        if ("弹幕".equals(value) || "弹幕搜索".equals(value) || value.startsWith("弹幕(")) return 4;
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
        final boolean fromResource;

        Anchor(View view, ViewGroup parent, Rect rect, String text, int priority, boolean fromResource) {
            this.view = view;
            this.parent = parent;
            this.rect = rect == null ? new Rect() : rect;
            this.text = text == null ? "" : text;
            this.priority = priority;
            this.fromResource = fromResource;
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

        CandidateHandle(int type, String handle, String label) {
            this.type = type;
            this.handle = handle;
            this.label = label;
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

        InjectionSettings(boolean injectionEnabled, boolean autoPushEnabled, double offsetSec, int fontSize, int shellPort, boolean darkTheme) {
            this(injectionEnabled, autoPushEnabled, offsetSec, fontSize, shellPort, darkTheme, 0, "");
        }

        InjectionSettings(boolean injectionEnabled, boolean autoPushEnabled, double offsetSec, int fontSize, int shellPort, boolean darkTheme, int corePort, String coreToken) {
            this.injectionEnabled = injectionEnabled;
            this.autoPushEnabled = autoPushEnabled;
            this.offsetSec = Math.abs(offsetSec) < 1e-6 ? 0.0d : offsetSec;
            this.fontSize = fontSize > 0 ? fontSize : -1;
            this.shellPort = shellPort > 0 && shellPort <= 65535 ? shellPort : 9978;
            this.darkTheme = darkTheme;
            this.corePort = corePort > 0 && corePort <= 65535 ? corePort : 0;
            this.coreToken = coreToken == null ? "" : coreToken.trim();
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
    }
}
