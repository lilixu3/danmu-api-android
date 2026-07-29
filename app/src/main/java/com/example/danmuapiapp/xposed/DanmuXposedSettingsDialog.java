package com.example.danmuapiapp.xposed;

import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.formatOffsetSeconds;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.parseNullableDouble;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.safeParseInt;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Injected settings, opened from the search dialog. It renders in the module's own dialog
 * style instead of blending into the host settings page, so no host view cloning, back-key
 * hooking or navigation guards are needed.
 */
final class DanmuXposedSettingsDialog {

    private static final int[] FONT_SIZE_OPTIONS = {-1, 20, 24, 28, 32};
    private static final String[] FONT_SIZE_LABELS = {"默认", "20", "24", "28", "32", "自定义"};

    interface Host {
        InjectionSettings readInjectionSettings(Context context, int fallbackPort);

        boolean saveInjectionSettings(Context context, InjectionSettings settings);

        boolean readEpisodeShowTitles(Context context);

        boolean saveEpisodeShowTitles(Context context, boolean showTitles);

        void warn(String message);
    }

    private final Host host;

    DanmuXposedSettingsDialog(Host host) {
        this.host = host;
    }

    /**
     * @param onChanged invoked when a change requires the caller to rebuild itself
     *                  (theme, episode display mode, shell port).
     */
    void show(Activity activity, DanmuTheme theme, int fallbackPort, Runnable onChanged) {
        InjectionSettings settings = host.readInjectionSettings(activity, fallbackPort);
        boolean showTitles = host.readEpisodeShowTitles(activity);
        State state = new State(settings, showTitles);

        LinearLayout root = DanmuDialog.root(activity, theme);
        root.addView(DanmuUi.title(activity, theme, "APP弹幕设置"),
            DanmuDialog.matchWrapWithBottom(activity, DanmuTheme.SPACE_4));

        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.addView(rows, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        DanmuDialog.limitHeight(scroll, DanmuDialog.maxContentHeight(activity));
        root.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Row themeRow = addRow(activity, theme, rows, "界面主题", state.darkTheme ? "黑色" : "白色");
        Row titleModeRow = addRow(activity, theme, rows, "集详情显示", state.showTitles ? "带标题" : "数字格");
        Row offsetRow = addRow(activity, theme, rows, "时间轴偏移", formatOffsetSeconds(state.offsetSec));
        Row fontRow = addRow(activity, theme, rows, "弹幕大小", fontSizeLabel(state.fontSize));
        Row portRow = addRow(activity, theme, rows, "影视壳端口", String.valueOf(state.shellPort));

        Button close = DanmuUi.ghostButton(activity, theme, "关闭");
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeLp.topMargin = DanmuDialog.dp(activity, DanmuTheme.SPACE_4);
        closeLp.gravity = Gravity.END;
        root.addView(close, closeLp);

        AlertDialog dialog = DanmuDialog.create(activity, root);
        close.setOnClickListener(v -> dialog.dismiss());

        themeRow.view.setOnClickListener(v -> {
            boolean next = !state.darkTheme;
            if (!persist(activity, state.withDarkTheme(next), "界面主题")) return;
            state.darkTheme = next;
            themeRow.value.setText(next ? "黑色" : "白色");
            dialog.dismiss();
            if (onChanged != null) onChanged.run();
        });

        titleModeRow.view.setOnClickListener(v -> {
            boolean next = !state.showTitles;
            if (!host.saveEpisodeShowTitles(activity, next)) {
                Toast.makeText(activity, "保存集详情显示失败", Toast.LENGTH_SHORT).show();
                return;
            }
            state.showTitles = next;
            titleModeRow.value.setText(next ? "带标题" : "数字格");
            Toast.makeText(activity, "已保存集详情显示", Toast.LENGTH_SHORT).show();
            if (onChanged != null) onChanged.run();
        });

        offsetRow.view.setOnClickListener(v -> DanmuDialog.showTextInput(
            activity, theme, "时间轴偏移", "可输入正负小数，例如 -0.5",
            formatOffsetSeconds(state.offsetSec),
            InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED,
            value -> parseNullableDouble(value) == null ? "请输入数字，可为负" : "",
            value -> {
                Double parsed = parseNullableDouble(value);
                if (parsed == null) return;
                if (!persist(activity, state.withOffset(parsed), "时间轴偏移")) return;
                state.offsetSec = parsed;
                offsetRow.value.setText(formatOffsetSeconds(parsed));
            }));

        fontRow.view.setOnClickListener(v -> DanmuDialog.showSingleChoice(
            activity, theme, "弹幕大小", FONT_SIZE_LABELS, fontSizeIndex(state.fontSize),
            index -> {
                if (index >= FONT_SIZE_OPTIONS.length) {
                    askCustomFontSize(activity, theme, state, fontRow);
                    return;
                }
                int next = FONT_SIZE_OPTIONS[index];
                if (!persist(activity, state.withFontSize(next), "弹幕大小")) return;
                state.fontSize = next;
                fontRow.value.setText(fontSizeLabel(next));
            }));

        portRow.view.setOnClickListener(v -> DanmuDialog.showTextInput(
            activity, theme, "影视壳端口", "请输入 1-65535", String.valueOf(state.shellPort),
            InputType.TYPE_CLASS_NUMBER,
            value -> isValidPort(safeParseInt(value)) ? "" : "端口范围应为 1-65535",
            value -> {
                int parsed = safeParseInt(value);
                if (!isValidPort(parsed)) return;
                if (!persist(activity, state.withShellPort(parsed), "影视壳端口")) return;
                state.shellPort = parsed;
                portRow.value.setText(String.valueOf(parsed));
                if (onChanged != null) onChanged.run();
            }));

        DanmuDialog.showCentered(dialog, activity, 520);
        DanmuDialog.focusFirst(dialog, themeRow.view);
    }

    private void askCustomFontSize(Activity activity, DanmuTheme theme, State state, Row fontRow) {
        DanmuDialog.showTextInput(
            activity, theme, "自定义弹幕大小", "请输入 8-80 之间的整数",
            state.fontSize > 0 ? String.valueOf(state.fontSize) : "",
            InputType.TYPE_CLASS_NUMBER,
            value -> isValidFontSize(safeParseInt(value)) ? "" : "弹幕大小范围应为 8-80",
            value -> {
                int parsed = safeParseInt(value);
                if (!isValidFontSize(parsed)) return;
                if (!persist(activity, state.withFontSize(parsed), "弹幕大小")) return;
                state.fontSize = parsed;
                fontRow.value.setText(fontSizeLabel(parsed));
            });
    }

    private boolean persist(Activity activity, InjectionSettings updated, String label) {
        boolean saved = host.saveInjectionSettings(activity, updated);
        Toast.makeText(activity, (saved ? "已保存" : "保存失败：") + label, Toast.LENGTH_SHORT).show();
        if (!saved) host.warn("save injection settings failed for " + label);
        return saved;
    }

    private static boolean isValidPort(int port) {
        return port > 0 && port <= 65535;
    }

    private static boolean isValidFontSize(int size) {
        return size >= 8 && size <= 80;
    }

    private static String fontSizeLabel(int fontSize) {
        return fontSize > 0 ? String.valueOf(fontSize) : "默认";
    }

    private static int fontSizeIndex(int fontSize) {
        for (int i = 0; i < FONT_SIZE_OPTIONS.length; i++) {
            if (FONT_SIZE_OPTIONS[i] == fontSize) return i;
        }
        return FONT_SIZE_OPTIONS.length;
    }

    private static Row addRow(Activity activity, DanmuTheme t, LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(t.focusable(t.surface, DanmuTheme.RADIUS_MD, t.stroke, activity));
        int padH = DanmuDialog.dp(activity, DanmuTheme.SPACE_4);
        int padV = DanmuDialog.dp(activity, DanmuTheme.SPACE_3);
        row.setPadding(padH, padV, padH, padV);
        row.setMinimumHeight(DanmuDialog.dp(activity, 52));
        DanmuUi.makeInteractive(row);

        TextView labelView = DanmuUi.text(activity, t, label, DanmuTheme.TEXT_LABEL, t.textPrimary, false);
        row.addView(labelView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = DanmuUi.text(activity, t, value, DanmuTheme.TEXT_BODY, t.accentSoftText, true);
        valueView.setGravity(Gravity.END);
        row.addView(valueView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = DanmuDialog.dp(activity, DanmuTheme.SPACE_2);
        parent.addView(row, rowLp);
        return new Row(row, valueView);
    }

    private static final class Row {
        final View view;
        final TextView value;

        Row(View view, TextView value) {
            this.view = view;
            this.value = value;
        }
    }

    /** Mutable working copy so each row only rewrites the field it owns. */
    private static final class State {
        final InjectionSettings base;
        double offsetSec;
        int fontSize;
        int shellPort;
        boolean darkTheme;
        boolean showTitles;

        State(InjectionSettings base, boolean showTitles) {
            this.base = base;
            this.offsetSec = base.offsetSec;
            this.fontSize = base.fontSize;
            this.shellPort = base.shellPort;
            this.darkTheme = base.darkTheme;
            this.showTitles = showTitles;
        }

        InjectionSettings snapshot(double offset, int font, int port, boolean dark) {
            return new InjectionSettings(
                base.injectionEnabled, base.autoPushEnabled, offset, font, port, dark,
                base.corePort, base.coreToken);
        }

        InjectionSettings withOffset(double value) {
            return snapshot(value, fontSize, shellPort, darkTheme);
        }

        InjectionSettings withFontSize(int value) {
            return snapshot(offsetSec, value, shellPort, darkTheme);
        }

        InjectionSettings withShellPort(int value) {
            return snapshot(offsetSec, fontSize, value, darkTheme);
        }

        InjectionSettings withDarkTheme(boolean value) {
            return snapshot(offsetSec, fontSize, shellPort, value);
        }
    }
}
