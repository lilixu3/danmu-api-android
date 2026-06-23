package com.example.danmuapiapp.xposed;

import static com.example.danmuapiapp.xposed.DanmuXposedPlaybackControls.*;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private String packageName = "";
    private boolean lifecycleHooked = false;
    private volatile boolean moduleGenerationActive = true;
    private final List<XposedInterface.HookHandle> activityHookHandles = Collections.synchronizedList(new ArrayList<>());
    private final Map<Integer, android.view.ViewTreeObserver.OnGlobalLayoutListener> injectionWatchers = new ConcurrentHashMap<>();
    private final DanmuXposedEpisodeRepository episodeRepository = new DanmuXposedEpisodeRepository(this::readInjectionSettings);
    private final DanmuXposedPushCoordinator pushCoordinator = new DanmuXposedPushCoordinator(new DanmuXposedPushCoordinator.Host() {
        @Override
        public InjectionSettings readInjectionSettings(Context context, int fallbackPort) {
            return DanmuXposedModule.this.readInjectionSettings(context, fallbackPort);
        }

        @Override
        public boolean isModuleGenerationActive() {
            return moduleGenerationActive;
        }

        @Override
        public void log(int level, String message) {
            DanmuXposedModule.this.log(level, TAG, message);
        }

        @Override
        public void log(int level, String message, Throwable throwable) {
            DanmuXposedModule.this.log(level, TAG, message, throwable);
        }
    }, episodeRepository);
    private final DanmuXposedSettingsOverlay settingsOverlay = new DanmuXposedSettingsOverlay(new DanmuXposedSettingsOverlay.Host() {
        @Override
        public InjectionSettings readInjectionSettings(Context context, int fallbackPort) {
            return DanmuXposedModule.this.readInjectionSettings(context, fallbackPort);
        }

        @Override
        public boolean saveInjectionSettings(Context context, InjectionSettings settings) {
            return DanmuXposedModule.this.saveInjectionSettings(context, settings);
        }

        @Override
        public boolean readEpisodeShowTitles(Context context) {
            return DanmuXposedModule.this.readEpisodeShowTitles(context);
        }

        @Override
        public boolean saveEpisodeShowTitles(Context context, boolean showTitles) {
            return DanmuXposedModule.this.saveEpisodeShowTitles(context, showTitles);
        }

        @Override
        public void installBackInterceptor(Method method, DanmuXposedSettingsOverlay.BackCloseHandler handler) throws Throwable {
            hook(method).intercept(chain -> {
                Object thisObject = chain.getThisObject();
                if (thisObject instanceof Activity && handler.closeActiveSettingsOverlay((Activity) thisObject)) {
                    return null;
                }
                return chain.proceed();
            });
        }

        @Override
        public void warn(String message) {
            log(Log.WARN, TAG, message);
        }
    });
    private final DanmuXposedSettingsRowInjector settingsRowInjector = new DanmuXposedSettingsRowInjector(new DanmuXposedSettingsRowInjector.Host() {
        @Override
        public boolean isActivityActiveForInjection(Activity activity) {
            return pushCoordinator.isActivityActiveForInjection(activity);
        }

        @Override
        public InjectionSettings readInjectionSettings(Context context, int fallbackPort) {
            return DanmuXposedModule.this.readInjectionSettings(context, fallbackPort);
        }

        @Override
        public void showInjectionSettingsDialog(Activity activity, View backgroundAnchor, int[] shellPortRef) {
            DanmuXposedModule.this.showInjectionSettingsDialog(activity, backgroundAnchor, shellPortRef);
        }

        @Override
        public void log(int level, String message) {
            DanmuXposedModule.this.log(level, TAG, message);
        }
    });
    private final DanmuXposedManualSearchDialog manualSearchDialog = new DanmuXposedManualSearchDialog(new DanmuXposedManualSearchDialog.Host() {
        @Override
        public InjectionSettings readInjectionSettings(Context context, int fallbackPort) {
            return DanmuXposedModule.this.readInjectionSettings(context, fallbackPort);
        }

        @Override
        public boolean readEpisodeShowTitles(Context context) {
            return DanmuXposedModule.this.readEpisodeShowTitles(context);
        }

        @Override
        public boolean saveEpisodeShowTitles(Context context, boolean showTitles) {
            return DanmuXposedModule.this.saveEpisodeShowTitles(context, showTitles);
        }

        @Override
        public BridgeResult queryBridgeAnimeSearch(Activity activity, String title) {
            return DanmuXposedModule.this.queryBridgeAnimeSearch(activity, title);
        }

        @Override
        public BridgeResult loadAnimeDetailDirect(String animeHandle, String episodeHint) {
            return DanmuXposedModule.this.loadAnimeDetailDirect(animeHandle, episodeHint);
        }

        @Override
        public void pushCandidate(Activity activity, CandidateHandle candidate, int shellPort, TextView statusText, TextView pushInfoText) {
            pushCoordinator.pushCandidate(activity, candidate, shellPort, statusText, pushInfoText);
        }

        @Override
        public void autoPushCurrent(Activity activity, int fallbackPort, TextView statusText, TextView pushInfoText) {
            pushCoordinator.autoPushCurrent(activity, fallbackPort, statusText, pushInfoText);
        }

        @Override
        public ShellMedia readShellMedia(int preferredPort) {
            return pushCoordinator.readShellMedia(preferredPort);
        }

        @Override
        public String formatPushTimeChip() {
            return pushCoordinator.formatPushTimeChip();
        }

        @Override
        public String formatLastPushInfo(Context context) {
            return pushCoordinator.formatLastPushInfo(context);
        }

        @Override
        public boolean hasUnviewedPush() {
            return pushCoordinator.hasUnviewedPush();
        }

        @Override
        public void markPushHistoryViewed() {
            pushCoordinator.markPushHistoryViewed();
        }

        @Override
        public void showPushHistoryDialog(Activity activity, DanmuTheme theme, View notifyButton, TextView notifyDot) {
            pushCoordinator.showPushHistoryDialog(activity, theme, notifyButton, notifyDot);
        }

        @Override
        public EpisodeCandidate episodeCandidate(String handle) {
            return DanmuXposedModule.this.episodeCandidate(handle);
        }

        @Override
        public void logError(String message, Throwable throwable) {
            log(Log.ERROR, TAG, message, throwable);
        }
    });

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "module loaded in " + param.getProcessName());
    }

    @Override
    public boolean onHotReloading(HotReloadingParam param) {
        try {
            moduleGenerationActive = false;
            Activity currentActivity = pushCoordinator.currentActivity();
            if (currentActivity != null) clearInjectionWatch(currentActivity);
            Bundle outState = new Bundle();
            outState.putString("packageName", packageName == null ? "" : packageName);
            pushCoordinator.prepareForHotReload(outState);
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
                pushCoordinator.markActivityResumed((Activity) thisObject);
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
                pushCoordinator.markActivityResumed((Activity) thisObject);
                scheduleInject((Activity) thisObject);
            }
            return result;
        });

        Method onPause = Activity.class.getDeclaredMethod("onPause");
        installActivityHook(onPause, HOOK_ID_ON_PAUSE, chain -> {
            Object result = chain.proceed();
            Object thisObject = chain.getThisObject();
            if (thisObject instanceof Activity) {
                pushCoordinator.markActivityPaused((Activity) thisObject);
                clearInjectionWatch((Activity) thisObject);
            }
            return result;
        });

        Method onDestroy = Activity.class.getDeclaredMethod("onDestroy");
        installActivityHook(onDestroy, HOOK_ID_ON_DESTROY, chain -> {
            Object result = chain.proceed();
            Object thisObject = chain.getThisObject();
            if (thisObject instanceof Activity) {
                pushCoordinator.markActivityDestroyed((Activity) thisObject);
                clearInjectionWatch((Activity) thisObject);
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
            pushCoordinator.restoreHotReloadState(bundle);
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
                            pushCoordinator.startAutoPushLoopOnce(activity);
                            clearInjectionWatch(activity);
                            return;
                        }
                        injectButton(activity);
                        injectSettingsRow(activity);
                        if (findTaggedButton(currentGroup) != null) {
                            pushCoordinator.startAutoPushLoopOnce(activity);
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
            if (!pushCoordinator.isActivityActiveForInjection(activity)) {
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
                pushCoordinator.startAutoPushLoopOnce(activity);
                clearInjectionWatch(activity);
                return;
            }

            Anchor anchor = findAnchor(decor);
            if (!looksLikePlaybackPage(activity, decor, anchor)) {
                return;
            }

            if (anchor == null || anchor.parent == null) {
                pushCoordinator.startAutoPushLoopOnce(activity);
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
            pushCoordinator.startAutoPushLoopOnce(activity);
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
        settingsRowInjector.inject(activity);
    }

    private void showManualSearchDialog(Activity activity) {
        manualSearchDialog.show(activity);
    }

    private EpisodeCandidate episodeCandidate(String handle) {
        return episodeRepository.loadEpisodeCandidate(handle);
    }

    private BridgeResult queryBridgeAnimeSearch(Activity activity, String title) {
        return episodeRepository.queryAnimeSearch(activity, title);
    }

    private BridgeResult loadAnimeDetailDirect(String animeHandle, String episodeHint) {
        return episodeRepository.loadAnimeDetail(animeHandle, episodeHint);
    }

    private SharedPreferences getRemotePreferencesOrNull() {
        try {
            if ((getFrameworkProperties() & PROP_CAP_REMOTE) == 0L) return null;
            return getRemotePreferences(DanmuXposedSettingsStore.PREFS_INJECTION);
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "remote preferences unavailable: " + throwable.getMessage());
            return null;
        }
    }

    private InjectionSettings readInjectionSettings(Context context, int fallbackPort) {
        return DanmuXposedSettingsStore.readInjectionSettings(context, fallbackPort, this::getRemotePreferencesOrNull);
    }

    private boolean saveInjectionSettings(Context context, InjectionSettings settings) {
        return DanmuXposedSettingsStore.saveInjectionSettings(
            context,
            settings,
            this::getRemotePreferencesOrNull,
            message -> log(Log.WARN, TAG, message)
        );
    }

    private boolean readEpisodeShowTitles(Context context) {
        return DanmuXposedSettingsStore.readEpisodeShowTitles(context, this::getRemotePreferencesOrNull);
    }

    private boolean saveEpisodeShowTitles(Context context, boolean showTitles) {
        return DanmuXposedSettingsStore.saveEpisodeShowTitles(
            context,
            showTitles,
            this::getRemotePreferencesOrNull,
            message -> log(Log.WARN, TAG, message)
        );
    }

    private void showInjectionSettingsDialog(Activity activity, View backgroundAnchor, int[] shellPortRef) {
        settingsOverlay.showInjectionSettingsOverlay(activity, backgroundAnchor, shellPortRef);
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

}
