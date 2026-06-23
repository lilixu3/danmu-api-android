package com.example.danmuapiapp.xposed;

import static com.example.danmuapiapp.xposed.DanmuXposedHostBackgrounds.resolveHostPageBackground;
import static com.example.danmuapiapp.xposed.DanmuXposedHostBackgrounds.resolveRowTemplateBackground;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.formatOffsetSeconds;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.parseNullableDouble;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.safeParseInt;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class DanmuXposedSettingsOverlay {
    private static final String SETTINGS_OVERLAY_TAG = "com.example.danmuapiapp.APP_DANMU_SETTINGS_OVERLAY";
    private static final long SETTINGS_OVERLAY_NAV_GUARD_INTERVAL_MS = 80L;
    private static final int DIALOG_STYLE_CENTER = InjectionSettings.DIALOG_STYLE_CENTER;
    private static final int DIALOG_STYLE_BOTTOM_SHEET = InjectionSettings.DIALOG_STYLE_BOTTOM_SHEET;

    interface Host {
        InjectionSettings readInjectionSettings(Context context, int fallbackPort);

        boolean saveInjectionSettings(Context context, InjectionSettings settings);

        boolean readEpisodeShowTitles(Context context);

        boolean saveEpisodeShowTitles(Context context, boolean showTitles);

        void installBackInterceptor(Method method, BackCloseHandler handler) throws Throwable;

        void warn(String message);
    }

    interface BackCloseHandler {
        boolean closeActiveSettingsOverlay(Activity activity);
    }

    private final Host host;
    private final Map<String, Boolean> settingsOverlayBackHooks = new ConcurrentHashMap<>();

    DanmuXposedSettingsOverlay(Host host) {
        this.host = host;
    }

    void showInjectionSettingsOverlay(Activity activity, View backgroundAnchor, int[] shellPortRef) {
        try {
            ViewGroup hostContent = findHostContentRoot(activity);
            if (hostContent == null) {
                Toast.makeText(activity, "打开设置失败：找不到宿主容器", Toast.LENGTH_SHORT).show();
                return;
            }
            installSettingsOverlayBackInterceptor(activity);
            removeExistingSettingsOverlay(hostContent);

            int fallbackPort = shellPortRef != null && shellPortRef.length > 0 ? shellPortRef[0] : 9978;
            InjectionSettings current = host.readInjectionSettings(activity, fallbackPort);
            final boolean[] darkTheme = new boolean[]{current.darkTheme};
            final boolean[] showTitles = new boolean[]{host.readEpisodeShowTitles(activity)};
            final double[] offset = new double[]{current.offsetSec};
            final int[] fontSize = new int[]{current.fontSize};
            final int[] shellPort = new int[]{current.shellPort};
            final int[] dialogStyle = new int[]{current.dialogStyle};

            TextView templateText = findFirstTextView(backgroundAnchor);
            Drawable rowTemplateBackground = resolveRowTemplateBackground(backgroundAnchor);
            View hostPageContainer = findHostPageContainer(backgroundAnchor, hostContent);
            View hostPageRoot = findHostPageRootForOverlay(backgroundAnchor, hostContent);
            Drawable pageBackground = resolveHostPageBackground(activity, backgroundAnchor, hostPageContainer, host::warn);

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
                if (host.saveInjectionSettings(activity, updated)) {
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
                if (host.saveEpisodeShowTitles(activity, next)) {
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
                if (host.saveInjectionSettings(activity, updated)) {
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
                if (host.saveInjectionSettings(activity, updated)) {
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
                    if (host.saveInjectionSettings(activity, updated)) {
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
                if (host.saveInjectionSettings(activity, updated)) {
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
            host.warn("show injection settings failed: " + throwable.getMessage());
        }
    }

    private void installSettingsOverlayBackInterceptor(Activity activity) {
        try {
            if (activity == null) return;
            Method method = findHostBackHandlerMethod(activity.getClass());
            if (method == null) return;
            String key = method.getDeclaringClass().getName() + "#" + method.getName();
            if (settingsOverlayBackHooks.putIfAbsent(key, Boolean.TRUE) != null) return;
            host.installBackInterceptor(method, this::closeActiveSettingsOverlay);
        } catch (Throwable throwable) {
            host.warn("install settings overlay back interceptor failed: " + throwable.getMessage());
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
                host.warn("find host back handler failed: " + throwable.getMessage());
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

    private TextView findFirstTextView(View root) {
        if (root instanceof TextView) return (TextView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findFirstTextView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
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

    private Drawable cloneDrawable(Drawable drawable) {
        if (drawable == null) return null;
        try {
            Drawable.ConstantState state = drawable.getConstantState();
            if (state != null) return state.newDrawable().mutate();
        } catch (Throwable ignored) {
        }
        return null;
    }

    private int dp(Activity activity, int value) {
        float density = activity.getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }
}
