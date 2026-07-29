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

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless component factory for injected dialogs. Every widget's appearance lives here
 * so dialog controllers only describe layout and behaviour, never raw colours or paddings.
 * Interactive widgets are D-pad focusable by construction.
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

    static TextView title(Context c, DanmuTheme t, String value) {
        return text(c, t, value, DanmuTheme.TEXT_HEADLINE, t.textPrimary, true);
    }

    /** Single-line status text pinned to the foot of a dialog. */
    static TextView statusLine(Context c, DanmuTheme t, String value) {
        TextView tv = text(c, t, value, DanmuTheme.TEXT_CAPTION + 0.5f, t.textMuted, false);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        return tv;
    }

    // ---------------------------------------------------------------- containers

    /** Rounded surface used as the left/right column of the landscape layout. */
    static LinearLayout panel(Context c, DanmuTheme t) {
        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(t.roundRect(
            t.surfaceAlt, DanmuTheme.RADIUS_LG, t.stroke, DanmuTheme.STROKE_HAIRLINE, c));
        int pad = t.dp(c, DanmuTheme.SPACE_3);
        panel.setPadding(pad, pad, pad, pad);
        return panel;
    }

    /** All-caps section label that heads a panel. */
    static TextView sectionLabel(Context c, DanmuTheme t, String value) {
        TextView tv = text(c, t, value, DanmuTheme.TEXT_MICRO, t.textMuted, true);
        tv.setAllCaps(false);
        tv.setLetterSpacing(0.08f);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        return tv;
    }

    // ---------------------------------------------------------------- buttons

    static Button primaryButton(Context c, DanmuTheme t, String label) {
        Button b = baseButton(c, t, label);
        applyPrimary(c, t, b);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        return b;
    }

    static Button secondaryButton(Context c, DanmuTheme t, String label) {
        Button b = baseButton(c, t, label);
        applySecondary(c, t, b);
        return b;
    }

    /** Borderless, low-emphasis button (关闭 / 取消 / 返回). */
    static Button ghostButton(Context c, DanmuTheme t, String label) {
        Button b = baseButton(c, t, label);
        applyGhost(c, t, b);
        return b;
    }

    /** Square glyph button for the header actions (设置 / 记录). */
    static TextView iconButton(Context c, DanmuTheme t, String glyph) {
        TextView b = new TextView(c);
        b.setText(glyph);
        b.setTextSize(DanmuTheme.TEXT_TITLE);
        b.setTextColor(t.textSecondary);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setBackground(t.focusable(t.surfaceAlt, DanmuTheme.RADIUS_PILL, t.stroke, c));
        makeInteractive(b);
        return b;
    }

    static void applyPrimary(Context c, DanmuTheme t, Button b) {
        b.setTextColor(t.accentText);
        b.setBackground(t.accentFocusable(DanmuTheme.RADIUS_SM, c));
    }

    static void applySecondary(Context c, DanmuTheme t, Button b) {
        b.setTextColor(t.textPrimary);
        b.setBackground(t.focusable(t.surfaceAlt, DanmuTheme.RADIUS_SM, t.strokeStrong, c));
    }

    static void applyGhost(Context c, DanmuTheme t, Button b) {
        b.setTextColor(t.textSecondary);
        b.setBackground(t.focusable(t.surfaceAlt, DanmuTheme.RADIUS_SM, t.stroke, c));
    }

    private static Button baseButton(Context c, DanmuTheme t, String label) {
        Button b = new Button(c);
        b.setText(label);
        b.setTextSize(DanmuTheme.TEXT_BODY);
        b.setAllCaps(false);
        int minTouchSize = t.dp(c, 48);
        b.setMinWidth(minTouchSize);
        b.setMinimumWidth(minTouchSize);
        b.setMinHeight(t.dp(c, 40));
        b.setMinimumHeight(t.dp(c, 40));
        b.setPadding(t.dp(c, DanmuTheme.SPACE_3), 0, t.dp(c, DanmuTheme.SPACE_3), 0);
        makeInteractive(b);
        return b;
    }

    /** Shared contract for anything reachable by remote: focusable, flat, audible. */
    static void makeInteractive(View v) {
        v.setClickable(true);
        v.setFocusable(true);
        v.setFocusableInTouchMode(false);
        v.setStateListAnimator(null);
        v.setElevation(0f);
        v.setSoundEffectsEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.setDefaultFocusHighlightEnabled(false);
        }
    }

    /** Unread marker overlaid on the push-history entry point. */
    static TextView notifyDot(Context c, DanmuTheme t) {
        TextView dot = new TextView(c);
        dot.setBackground(t.roundRect(t.danger, DanmuTheme.RADIUS_PILL, c));
        dot.setVisibility(View.GONE);
        return dot;
    }

    // ---------------------------------------------------------------- chips

    /** Small non-interactive status/info chip. */
    static TextView chip(Context c, DanmuTheme t, String value, boolean tinted) {
        TextView chip = new TextView(c);
        chip.setText(value == null ? "" : value);
        chip.setTextSize(DanmuTheme.TEXT_CAPTION);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setGravity(Gravity.CENTER);
        chip.setIncludeFontPadding(false);
        chip.setPadding(t.dp(c, DanmuTheme.SPACE_3), t.dp(c, DanmuTheme.SPACE_1),
            t.dp(c, DanmuTheme.SPACE_3), t.dp(c, DanmuTheme.SPACE_1));
        if (tinted) {
            chip.setTextColor(t.successText);
            chip.setBackground(t.roundRect(t.successSoft, DanmuTheme.RADIUS_PILL, c));
        } else {
            chip.setTextColor(t.textSecondary);
            chip.setBackground(t.roundRect(
                t.surfaceAlt, DanmuTheme.RADIUS_PILL, t.stroke, DanmuTheme.STROKE_HAIRLINE, c));
        }
        return chip;
    }

    /**
     * Selectable platform/source filter chip. Selection is carried by the view's selected
     * state, so re-styling never needs to touch colours.
     */
    static TextView filterChip(Context c, DanmuTheme t, String value, boolean selected) {
        TextView chip = new TextView(c);
        chip.setTextSize(DanmuTheme.TEXT_CAPTION);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setGravity(Gravity.CENTER);
        chip.setIncludeFontPadding(false);
        chip.setPadding(t.dp(c, DanmuTheme.SPACE_3), t.dp(c, DanmuTheme.SPACE_2),
            t.dp(c, DanmuTheme.SPACE_3), t.dp(c, DanmuTheme.SPACE_2));
        chip.setTextColor(t.selectableText(t.textSecondary));
        chip.setBackground(t.selectable(t.surfaceAlt, DanmuTheme.RADIUS_PILL, t.stroke, c));
        chip.setText(value == null ? "" : value);
        chip.setSelected(selected);
        makeInteractive(chip);
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
        e.setBackground(t.focusable(t.surfaceAlt, DanmuTheme.RADIUS_PILL, t.stroke, c));
        e.setPadding(t.dp(c, DanmuTheme.SPACE_4), t.dp(c, DanmuTheme.SPACE_2),
            t.dp(c, DanmuTheme.SPACE_4), t.dp(c, DanmuTheme.SPACE_2));
        return e;
    }

    // ---------------------------------------------------------------- result row

    /**
     * Tappable result row for the drama/source list. Selection lives entirely in the view's
     * selected state; children opt out of state propagation so only the row that is actually
     * selected changes appearance.
     */
    static final class ResultRow {
        final LinearLayout view;
        private final TextView badge;
        private final TextView title;
        private final TextView meta;

        private ResultRow(LinearLayout view, TextView badge, TextView title, TextView meta) {
            this.view = view;
            this.badge = badge;
            this.title = title;
            this.meta = meta;
        }

        void bind(String index, String titleText, String metaText, boolean selected) {
            badge.setText(index == null ? "" : index);
            title.setText(titleText == null ? "" : titleText);
            String value = metaText == null ? "" : metaText.trim();
            meta.setText(value);
            meta.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
            view.setSelected(selected);
        }
    }

    static ResultRow resultRow(Context c, DanmuTheme t) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(t.selectable(t.surface, DanmuTheme.RADIUS_MD, t.stroke, c));
        int padH = t.dp(c, DanmuTheme.SPACE_3);
        int padV = t.dp(c, DanmuTheme.SPACE_3);
        row.setPadding(padH, padV, padH, padV);
        row.setMinimumHeight(t.dp(c, 56));
        makeInteractive(row);

        TextView badge = new TextView(c);
        badge.setTextSize(DanmuTheme.TEXT_CAPTION);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setTextColor(t.selectableText(t.accentSoftText));
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        badge.setBackground(t.badgeFill(DanmuTheme.RADIUS_SM, c));
        int badgeSize = t.dp(c, 26);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(badgeSize, badgeSize);
        badgeLp.rightMargin = t.dp(c, DanmuTheme.SPACE_3);
        row.addView(badge, badgeLp);

        LinearLayout textCol = new LinearLayout(c);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(c, t, "", DanmuTheme.TEXT_LABEL, t.textPrimary, true);
        titleView.setTextColor(t.selectableText(t.textPrimary));
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(titleView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView metaView = text(c, t, "", DanmuTheme.TEXT_CAPTION, t.textMuted, false);
        metaView.setTextColor(t.selectableText(t.textMuted));
        metaView.setSingleLine(true);
        metaView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaLp.topMargin = t.dp(c, 2);
        textCol.addView(metaView, metaLp);
        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        return new ResultRow(row, badge, titleView, metaView);
    }

    // ---------------------------------------------------------------- episode cell

    static TextView episodeCell(Context c, DanmuTheme t) {
        TextView cell = new TextView(c);
        cell.setIncludeFontPadding(false);
        cell.setSingleLine(true);
        cell.setMaxLines(1);
        cell.setEllipsize(TextUtils.TruncateAt.END);
        cell.setTextSize(DanmuTheme.TEXT_BODY);
        cell.setTextColor(t.selectableText(t.textPrimary));
        cell.setBackground(t.selectable(t.surfaceAlt, DanmuTheme.RADIUS_SM, t.stroke, c));
        makeInteractive(cell);
        return cell;
    }

    /** Restyle an episode cell for number/title mode and selected state. */
    static void styleEpisodeCell(Context c, DanmuTheme t, TextView cell, String label, boolean selected, boolean titleMode) {
        cell.setSelected(selected);
        cell.setText(label);
        if (titleMode) {
            cell.setTypeface(Typeface.DEFAULT);
            cell.setGravity(Gravity.CENTER_VERTICAL);
            cell.setPadding(t.dp(c, DanmuTheme.SPACE_3), t.dp(c, DanmuTheme.SPACE_2),
                t.dp(c, DanmuTheme.SPACE_3), t.dp(c, DanmuTheme.SPACE_2));
        } else {
            cell.setTypeface(Typeface.DEFAULT_BOLD);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(t.dp(c, DanmuTheme.SPACE_1), t.dp(c, DanmuTheme.SPACE_2),
                t.dp(c, DanmuTheme.SPACE_1), t.dp(c, DanmuTheme.SPACE_2));
        }
    }

    // ---------------------------------------------------------------- toggle

    static Button toggleChip(Context c, DanmuTheme t, String label, boolean selected) {
        Button b = baseButton(c, t, label);
        b.setTextColor(t.selectableText(t.textSecondary));
        b.setBackground(t.selectable(t.surfaceAlt, DanmuTheme.RADIUS_SM, t.stroke, c));
        b.setSelected(selected);
        return b;
    }

    // ---------------------------------------------------------------- empty state

    static LinearLayout emptyState(Context c, DanmuTheme t, String title, String hint) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(t.dp(c, DanmuTheme.SPACE_5), t.dp(c, DanmuTheme.SPACE_6),
            t.dp(c, DanmuTheme.SPACE_5), t.dp(c, DanmuTheme.SPACE_6));
        TextView titleView = text(c, t, title, DanmuTheme.TEXT_LABEL, t.textSecondary, true);
        titleView.setGravity(Gravity.CENTER);
        box.addView(titleView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (hint != null && !hint.trim().isEmpty()) {
            TextView hintView = text(c, t, hint, DanmuTheme.TEXT_CAPTION, t.textMuted, false);
            hintView.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = t.dp(c, DanmuTheme.SPACE_2);
            box.addView(hintView, lp);
        }
        return box;
    }

    // ---------------------------------------------------------------- focus wiring

    /**
     * Wires explicit next-focus ids across a wrapped grid of cells. Geometric focus search
     * misjudges row boundaries when the grid is built from nested LinearLayouts, so each
     * cell states its four neighbours directly.
     */
    static void wireGridFocus(ViewGroup grid, int columns) {
        if (grid == null || columns <= 0) return;
        List<View> cells = new ArrayList<>();
        collectFocusableCells(grid, cells);
        int count = cells.size();
        for (int i = 0; i < count; i++) {
            View cell = cells.get(i);
            if (cell.getId() == View.NO_ID) cell.setId(View.generateViewId());
        }
        for (int i = 0; i < count; i++) {
            View cell = cells.get(i);
            int column = i % columns;
            int left = column == 0 ? i : i - 1;
            int right = column == columns - 1 || i + 1 >= count ? i : i + 1;
            int up = i - columns < 0 ? i : i - columns;
            int down = i + columns >= count ? i : i + columns;
            cell.setNextFocusLeftId(cells.get(left).getId());
            cell.setNextFocusRightId(cells.get(right).getId());
            cell.setNextFocusUpId(cells.get(up).getId());
            cell.setNextFocusDownId(cells.get(down).getId());
        }
    }

    private static void collectFocusableCells(ViewGroup parent, List<View> out) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ViewGroup && !child.isFocusable()) {
                collectFocusableCells((ViewGroup) child, out);
            } else if (child.isFocusable()) {
                out.add(child);
            }
        }
    }
}
