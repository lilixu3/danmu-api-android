package com.example.danmuapiapp.xposed;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

final class DanmuXposedSettingsRowInjector {
    private static final String SETTINGS_ROW_TAG = "com.example.danmuapiapp.APP_DANMU_SETTINGS_ROW";
    private static final String[] SETTINGS_ROW_ANCHOR_IDS = {"player"};
    private static final String[] SETTINGS_ROW_ANCHOR_TEXTS = {"播放设置"};

    interface Host {
        boolean isActivityActiveForInjection(Activity activity);

        InjectionSettings readInjectionSettings(Context context, int fallbackPort);

        void showInjectionSettingsDialog(Activity activity, View backgroundAnchor, int[] shellPortRef);

        void log(int level, String message);
    }

    private final Host host;

    DanmuXposedSettingsRowInjector(Host host) {
        this.host = host;
    }

    void inject(Activity activity) {
        try {
            if (!host.isActivityActiveForInjection(activity)) return;
            Window window = activity.getWindow();
            if (window == null) return;
            View decorView = window.getDecorView();
            if (!(decorView instanceof ViewGroup)) return;
            ViewGroup decor = (ViewGroup) decorView;

            InjectionSettings injectSettings = host.readInjectionSettings(activity, 9978);
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
            host.log(Log.INFO, "APP danmu settings row injected into " + activity.getClass().getName());
        } catch (Throwable throwable) {
            host.log(Log.WARN, "inject settings row failed: " + throwable.getMessage());
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

        int port = host.readInjectionSettings(activity, 9978).shellPort;
        row.setOnClickListener(v -> host.showInjectionSettingsDialog(activity, anchorRow, new int[]{port}));
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

    private Drawable cloneDrawable(Drawable drawable) {
        if (drawable == null) return null;
        try {
            Drawable.ConstantState state = drawable.getConstantState();
            if (state != null) return state.newDrawable().mutate();
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String readViewText(View view) {
        if (!(view instanceof TextView)) return "";
        CharSequence raw = ((TextView) view).getText();
        return raw == null ? "" : raw.toString().trim();
    }
}
