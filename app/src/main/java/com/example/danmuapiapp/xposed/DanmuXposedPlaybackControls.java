package com.example.danmuapiapp.xposed;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Locale;

final class DanmuXposedPlaybackControls {
    private static final String BUTTON_TAG = "com.example.danmuapiapp.APP_DANMU_BUTTON";

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

    private DanmuXposedPlaybackControls() {
    }

    static boolean looksLikePlaybackPage(Activity activity, ViewGroup decor, Anchor anchor) {
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

    static View createButton(Activity activity, View anchorView) {
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

    static ViewGroup.LayoutParams cloneLayoutParamsForInsert(Activity activity, View anchorView, View insertedView) {
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

    static View findTaggedButton(View root) {
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

    static Anchor findAnchor(View root) {
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

    private static boolean isKnownPlaybackActivityName(String className) {
        if (className == null || className.isEmpty()) return false;
        if (className.endsWith(".VideoActivity")) return true;
        String lower = className.toLowerCase(Locale.ROOT);
        for (String hint : ACTIVITY_HINTS) {
            if (lower.contains(hint) && lower.contains("activity")) return true;
        }
        return false;
    }

    private static void copyHostControlStyle(Activity activity, TextView button, View anchorView) {
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

    private static Drawable cloneDrawable(Drawable drawable) {
        if (drawable == null) return null;
        try {
            Drawable.ConstantState state = drawable.getConstantState();
            if (state != null) return state.newDrawable().mutate();
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void applyBorderlessControlBackground(Activity activity, View view) {
        try {
            TypedValue outValue = new TypedValue();
            if (activity.getTheme() != null && activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true) && outValue.resourceId != 0) {
                view.setBackgroundResource(outValue.resourceId);
            }
        } catch (Throwable ignored) {
        }
    }

    private static int resolveIconColor(View anchorView) {
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

    private static float resolveAnchorTextSizePx(Activity activity, View anchorView) {
        if (anchorView instanceof TextView) {
            try {
                float sizePx = ((TextView) anchorView).getTextSize();
                if (sizePx > 0f) return sizePx;
            } catch (Throwable ignored) {
            }
        }
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, activity.getResources().getDisplayMetrics());
    }

    private static Anchor findShellControlIdAnchor(ViewGroup root) {
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

    private static boolean looksLikeShellControlRow(ViewGroup parent) {
        if (parent == null || parent.getVisibility() != View.VISIBLE || !parent.isShown()) return false;
        String className = parent.getClass().getName();
        if (className.contains("RecyclerView") || parent instanceof ListView || parent instanceof GridView || parent instanceof GridLayout) return false;
        return countDirectShellControlIds(parent) >= 3 || countControlBarTexts(parent, 0) >= 3;
    }

    private static int countDirectShellControlIds(ViewGroup parent) {
        if (parent == null) return 0;
        int count = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (isShellControlId(parent.getChildAt(i))) count++;
        }
        return count;
    }

    private static boolean isShellControlId(View view) {
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

    private static Anchor findContainerAnchor(ViewGroup root) {
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

    private static Anchor findBestControlBarAnchor(View root, int depth, Anchor best) {
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

    private static int countSiblingAnchorTexts(ViewGroup parent) {
        if (parent == null) return 0;
        int count = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof TextView && isAnchorText(readViewText(child))) count++;
        }
        return count;
    }

    private static int countControlBarTexts(View root, int depth) {
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

    private static boolean isAnchorText(String text) {
        String value = normalizeControlText(text);
        if (value.isEmpty()) return false;
        for (String anchor : ANCHOR_TEXTS) {
            if (value.equals(anchor) || value.startsWith(anchor + "(")) return true;
        }
        return false;
    }

    private static boolean isControlBarText(String text) {
        String value = normalizeControlText(text);
        if (value.isEmpty()) return false;
        for (String control : CONTROL_BAR_TEXTS) {
            if (value.equals(control) || value.startsWith(control + "(") || value.startsWith(control + "：")) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeControlText(String text) {
        if (text == null) return "";
        return text.trim().replace('（', '(').replace('）', ')');
    }

    private static int anchorPriority(String text) {
        String value = normalizeControlText(text);
        if ("弹幕".equals(value) || "弹幕搜索".equals(value) || value.startsWith("弹幕(")) return 5;
        if ("片尾".equals(value)) return 4;
        if ("选集".equals(value)) return 3;
        if ("更多".equals(value)) return 2;
        if ("下集".equals(value)) return 1;
        return 0;
    }

    private static String readViewText(View view) {
        if (!(view instanceof TextView)) return "";
        CharSequence raw = ((TextView) view).getText();
        return raw == null ? "" : raw.toString().trim();
    }

    private static int dp(Activity activity, int value) {
        float density = activity.getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
