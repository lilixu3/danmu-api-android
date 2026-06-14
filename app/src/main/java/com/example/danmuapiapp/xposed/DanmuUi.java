package com.example.danmuapiapp.xposed;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Stateless component factory for the injection sheets. Every widget's appearance lives
 * here so the sheet controllers only describe layout and behaviour, never raw colours or
 * paddings. All methods take a {@link DanmuTheme} so light/dark is resolved consistently.
 */
final class DanmuUi {

    private DanmuUi() {}

    // ---------------------------------------------------------------- text

    static TextView text(Context c, DanmuTheme t, String value, float sizeSp, int color, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(value == null ? "" : value);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        tv.setIncludeFontPadding(false);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    static TextView sectionLabel(Context c, DanmuTheme t, String value) {
        TextView tv = text(c, t, value, DanmuTheme.TEXT_CAPTION, t.textMuted, true);
        tv.setAllCaps(false);
        tv.setLetterSpacing(0.02f);
        return tv;
    }

    /** Single-line status text used at the foot of the search sheet. */
    static TextView statusLine(Context c, DanmuTheme t, String value) {
        TextView tv = text(c, t, value, DanmuTheme.TEXT_CAPTION + 0.5f, t.textMuted, false);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        return tv;
    }

    // ---------------------------------------------------------------- buttons

    static Button primaryButton(Context c, DanmuTheme t, String label) {
        Button b = baseButton(c, t, label);
        applyPrimary(c, t, b);
        return b;
    }

    static Button secondaryButton(Context c, DanmuTheme t, String label) {
        Button b = baseButton(c, t, label);
        applySecondary(c, t, b);
        return b;
    }

    /** Borderless, low-emphasis button (e.g. 关闭 / 取消 / 返回). */
    static Button ghostButton(Context c, DanmuTheme t, String label) {
        Button b = baseButton(c, t, label);
        applyGhost(c, t, b);
        return b;
    }

    static void applyPrimary(Context c, DanmuTheme t, Button b) {
        b.setTextColor(t.accentText);
        b.setBackground(t.roundRect(t.accent, DanmuTheme.RADIUS_PILL, t.accentStrong, 1, c));
    }

    static void applySecondary(Context c, DanmuTheme t, Button b) {
        b.setTextColor(t.textPrimary);
        b.setBackground(t.roundRect(t.surface, DanmuTheme.RADIUS_PILL, t.strokeStrong, 1, c));
    }

    static void applyGhost(Context c, DanmuTheme t, Button b) {
        b.setTextColor(t.textSecondary);
        b.setBackground(t.roundRect(t.surfaceAlt, DanmuTheme.RADIUS_PILL, t.stroke, 1, c));
    }

    private static Button baseButton(Context c, DanmuTheme t, String label) {
        Button b = new Button(c);
        b.setText(label);
        b.setTextSize(DanmuTheme.TEXT_BODY);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(t.dp(c, DanmuTheme.SPACE_3), 0, t.dp(c, DanmuTheme.SPACE_3), 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            b.setStateListAnimator(null);
            b.setElevation(0f);
        }
        return b;
    }

    // ---------------------------------------------------------------- containers

    static LinearLayout card(Context c, DanmuTheme t, int padHDp, int padVDp) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(t.roundRect(t.surface, DanmuTheme.RADIUS_MD, t.stroke, 1, c));
        card.setPadding(t.dp(c, padHDp), t.dp(c, padVDp), t.dp(c, padHDp), t.dp(c, padVDp));
        return card;
    }

    static View dragHandle(Context c, DanmuTheme t) {
        View h = new View(c);
        h.setBackground(t.roundRect(t.handle, DanmuTheme.RADIUS_PILL, c));
        return h;
    }

    static LinearLayout.LayoutParams dragHandleLp(Context c, DanmuTheme t) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(t.dp(c, 38), t.dp(c, 4));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = t.dp(c, DanmuTheme.SPACE_3);
        return lp;
    }

    // ---------------------------------------------------------------- chips

    /** Small status/info chip. {@code tinted} fills with the accent-soft colour. */
    static TextView chip(Context c, DanmuTheme t, String value, boolean tinted) {
        TextView chip = new TextView(c);
        chip.setText(value == null ? "" : value);
        chip.setTextSize(DanmuTheme.TEXT_CAPTION);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setGravity(Gravity.CENTER);
        chip.setIncludeFontPadding(false);
        chip.setPadding(t.dp(c, DanmuTheme.SPACE_3), t.dp(c, DanmuTheme.SPACE_1), t.dp(c, DanmuTheme.SPACE_3), t.dp(c, DanmuTheme.SPACE_1));
        if (tinted) {
            chip.setTextColor(t.successText);
            chip.setBackground(t.roundRect(t.successSoft, DanmuTheme.RADIUS_PILL, c));
        } else {
            chip.setTextColor(t.textSecondary);
            chip.setBackground(t.roundRect(t.surfaceAlt, DanmuTheme.RADIUS_PILL, t.stroke, 1, c));
        }
        return chip;
    }

    // ---------------------------------------------------------------- input

    static EditText textField(Context c, DanmuTheme t, String hint, String value) {
        EditText e = new EditText(c);
        e.setSingleLine(true);
        e.setHint(hint == null ? "" : hint);
        e.setText(value == null ? "" : value);
        e.setTextSize(DanmuTheme.TEXT_LABEL);
        e.setTextColor(t.textPrimary);
        e.setHintTextColor(t.textMuted);
        e.setGravity(Gravity.CENTER_VERTICAL);
        e.setSelectAllOnFocus(true);
        e.setIncludeFontPadding(false);
        e.setBackground(t.roundRect(t.surfaceAlt, DanmuTheme.RADIUS_SM, t.strokeStrong, 1, c));
        e.setPadding(t.dp(c, DanmuTheme.SPACE_3), t.dp(c, DanmuTheme.SPACE_2), t.dp(c, DanmuTheme.SPACE_3), t.dp(c, DanmuTheme.SPACE_2));
        return e;
    }

    // ---------------------------------------------------------------- list row (drama result)

    /** Tappable result row used for the drama list. */
    static LinearLayout listRow(Context c, DanmuTheme t, String index, String title, String meta) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(t.roundRect(t.surface, DanmuTheme.RADIUS_MD, t.stroke, 1, c));
        row.setPadding(t.dp(c, DanmuTheme.SPACE_3), t.dp(c, DanmuTheme.SPACE_3), t.dp(c, DanmuTheme.SPACE_3), t.dp(c, DanmuTheme.SPACE_3));
        row.setClickable(true);
        row.setFocusable(true);

        TextView badge = new TextView(c);
        badge.setText(index);
        badge.setTextSize(DanmuTheme.TEXT_CAPTION);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setTextColor(t.accentSoftText);
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        badge.setBackground(t.roundRect(t.accentSoft, DanmuTheme.RADIUS_SM, c));
        int badgeSize = t.dp(c, 26);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(badgeSize, badgeSize);
        badgeLp.rightMargin = t.dp(c, DanmuTheme.SPACE_3);
        row.addView(badge, badgeLp);

        LinearLayout textCol = new LinearLayout(c);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(c, t, title, DanmuTheme.TEXT_LABEL, t.textPrimary, false);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (meta != null && !meta.trim().isEmpty()) {
            TextView metaView = text(c, t, meta, DanmuTheme.TEXT_CAPTION, t.textMuted, false);
            metaView.setSingleLine(true);
            metaView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            metaLp.topMargin = t.dp(c, 2);
            textCol.addView(metaView, metaLp);
        }
        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView chevron = text(c, t, "›", DanmuTheme.TEXT_TITLE, t.textMuted, false);
        chevron.setPadding(t.dp(c, DanmuTheme.SPACE_2), 0, 0, 0);
        row.addView(chevron, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    // ---------------------------------------------------------------- episode cell

    static TextView episodeCell(Context c, DanmuTheme t) {
        TextView cell = new TextView(c);
        cell.setClickable(true);
        cell.setFocusable(true);
        cell.setSoundEffectsEnabled(true);
        cell.setIncludeFontPadding(false);
        return cell;
    }

    /** Restyle an episode cell for number/title mode and selected state. */
    static void styleEpisodeCell(Context c, DanmuTheme t, TextView cell, String label, boolean selected, boolean titleMode) {
        cell.setTextColor(selected ? t.accentText : t.textPrimary);
        int fill = selected ? t.accent : t.surface;
        int strokeColor = selected ? t.accentStrong : t.stroke;
        cell.setBackground(t.roundRect(fill, DanmuTheme.RADIUS_SM, strokeColor, selected ? 2 : 1, c));
        cell.setSingleLine(true);
        cell.setMaxLines(1);
        cell.setEllipsize(TextUtils.TruncateAt.END);
        if (titleMode) {
            cell.setText(label);
            cell.setTextSize(DanmuTheme.TEXT_BODY);
            cell.setGravity(Gravity.CENTER_VERTICAL);
            cell.setPadding(t.dp(c, DanmuTheme.SPACE_3), t.dp(c, DanmuTheme.SPACE_2), t.dp(c, DanmuTheme.SPACE_3), t.dp(c, DanmuTheme.SPACE_2));
        } else {
            cell.setText(label);
            cell.setTextSize(DanmuTheme.TEXT_BODY);
            cell.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(t.dp(c, DanmuTheme.SPACE_1), t.dp(c, DanmuTheme.SPACE_2), t.dp(c, DanmuTheme.SPACE_1), t.dp(c, DanmuTheme.SPACE_2));
        }
    }

    // ---------------------------------------------------------------- toggle (segmented choice in settings)

    static Button toggleChip(Context c, DanmuTheme t, String label, boolean selected) {
        Button b = baseButton(c, t, label);
        b.setTextSize(DanmuTheme.TEXT_BODY);
        styleToggleChip(c, t, b, selected);
        return b;
    }

    static void styleToggleChip(Context c, DanmuTheme t, Button b, boolean selected) {
        b.setTextColor(selected ? t.accentText : t.textSecondary);
        b.setBackground(selected
            ? t.roundRect(t.accent, DanmuTheme.RADIUS_PILL, t.accentStrong, 1, c)
            : t.roundRect(t.surfaceAlt, DanmuTheme.RADIUS_PILL, t.stroke, 1, c));
    }

    // ---------------------------------------------------------------- empty state

    static LinearLayout emptyState(Context c, DanmuTheme t, String title, String hint) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(t.dp(c, DanmuTheme.SPACE_5), t.dp(c, DanmuTheme.SPACE_6), t.dp(c, DanmuTheme.SPACE_5), t.dp(c, DanmuTheme.SPACE_6));
        TextView titleView = text(c, t, title, DanmuTheme.TEXT_LABEL, t.textSecondary, true);
        titleView.setGravity(Gravity.CENTER);
        box.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (hint != null && !hint.trim().isEmpty()) {
            TextView hintView = text(c, t, hint, DanmuTheme.TEXT_CAPTION, t.textMuted, false);
            hintView.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = t.dp(c, DanmuTheme.SPACE_2);
            box.addView(hintView, lp);
        }
        return box;
    }

    // ---------------------------------------------------------------- segmented tabs (top navigation)

    /**
     * Three-stage step indicator: compact circle-based progress dots.
     * Completed stages show ✓, active shows number, future stages are dimmed.
     */
    static final class Segmented {
        final LinearLayout view;
        private final Context c;
        private final DanmuTheme t;
        private final TextView[] tabs;
        private final TextView[] connectors;
        private final String[] labels;
        private int reachable;

        Segmented(Context c, DanmuTheme t, String[] labels, OnTabClick onClick) {
            this.c = c;
            this.t = t;
            this.labels = labels;
            this.tabs = new TextView[labels.length];
            this.connectors = new TextView[labels.length > 1 ? labels.length - 1 : 0];
            this.view = new LinearLayout(c);
            view.setOrientation(LinearLayout.HORIZONTAL);
            view.setGravity(Gravity.CENTER_VERTICAL);
            int padH = t.dp(c, DanmuTheme.SPACE_1);
            int padV = t.dp(c, 2);
            view.setPadding(padH, padV, padH, padV);
            for (int i = 0; i < labels.length; i++) {
                final int index = i;
                TextView dot = new TextView(c);
                dot.setTextSize(DanmuTheme.TEXT_CAPTION);
                dot.setSingleLine(true);
                dot.setGravity(Gravity.CENTER);
                dot.setIncludeFontPadding(false);
                dot.setPadding(t.dp(c, 4), t.dp(c, 2), t.dp(c, 4), t.dp(c, 2));
                dot.setOnClickListener(v -> {
                    if (index <= reachable && onClick != null) onClick.onTab(index);
                });
                tabs[i] = dot;
                LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                view.addView(dot, dotLp);
                if (i < labels.length - 1) {
                    TextView conn = new TextView(c);
                    conn.setText(" › ");
                    conn.setTextSize(DanmuTheme.TEXT_CAPTION);
                    conn.setTextColor(t.textMuted);
                    conn.setGravity(Gravity.CENTER);
                    conn.setIncludeFontPadding(false);
                    connectors[i] = conn;
                    LinearLayout.LayoutParams connLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    view.addView(conn, connLp);
                }
            }
        }

        /** @param current active stage index; @param reachable highest stage the user may jump to. */
        void update(int current, int reachable) {
            this.reachable = reachable;
            for (int i = 0; i < tabs.length; i++) {
                TextView tab = tabs[i];
                boolean active = i == current;
                boolean done = i < current;
                boolean enabled = i <= reachable;
                String symbol = done ? "✓" : String.valueOf(i + 1);
                tab.setText(symbol);
                int chipDp = 20;
                if (active) {
                    tab.setTextColor(t.accentText);
                    tab.setTypeface(Typeface.DEFAULT_BOLD);
                    tab.setBackground(t.roundRect(t.accent, DanmuTheme.RADIUS_PILL, c));
                    tab.getLayoutParams().width = t.dp(c, chipDp);
                    tab.getLayoutParams().height = t.dp(c, chipDp);
                } else if (done) {
                    tab.setTextColor(t.successText);
                    tab.setTypeface(Typeface.DEFAULT_BOLD);
                    tab.setBackground(t.roundRect(t.successSoft, DanmuTheme.RADIUS_PILL, c));
                    tab.getLayoutParams().width = t.dp(c, chipDp);
                    tab.getLayoutParams().height = t.dp(c, chipDp);
                } else {
                    tab.setTextColor(t.textMuted);
                    tab.setTypeface(Typeface.DEFAULT);
                    tab.setBackground(t.roundRect(t.surfaceAlt, DanmuTheme.RADIUS_PILL, t.stroke, 1, c));
                    tab.getLayoutParams().width = t.dp(c, chipDp);
                    tab.getLayoutParams().height = t.dp(c, chipDp);
                }
            }
            for (int i = 0; i < connectors.length; i++) {
                connectors[i].setVisibility(i < reachable ? View.VISIBLE : View.INVISIBLE);
            }
        }

        interface OnTabClick {
            void onTab(int index);
        }
    }
}
