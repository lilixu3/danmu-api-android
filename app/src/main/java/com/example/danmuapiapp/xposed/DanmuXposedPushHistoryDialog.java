package com.example.danmuapiapp.xposed;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Read-only list of recent danmaku pushes, opened from the injected search dialog. */
final class DanmuXposedPushHistoryDialog {

    private DanmuXposedPushHistoryDialog() {}

    static void show(Activity activity, DanmuTheme t, List<String> entries) {
        LinearLayout root = DanmuDialog.root(activity, t);
        root.addView(DanmuUi.title(activity, t, "推送记录"),
            DanmuDialog.matchWrapWithBottom(activity, DanmuTheme.SPACE_4));

        if (entries == null || entries.isEmpty()) {
            root.addView(DanmuUi.emptyState(activity, t, "暂无推送", "收到推送后会记录在这里"),
                new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            LinearLayout list = new LinearLayout(activity);
            list.setOrientation(LinearLayout.VERTICAL);
            int shown = 0;
            for (String entry : entries) {
                if (entry == null || entry.trim().isEmpty()) continue;
                shown++;
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackground(t.roundRect(t.surfaceAlt, DanmuTheme.RADIUS_SM,
                    t.stroke, DanmuTheme.STROKE_HAIRLINE, activity));
                int pad = DanmuDialog.dp(activity, DanmuTheme.SPACE_3);
                row.setPadding(pad, pad, pad, pad);

                TextView badge = DanmuUi.text(activity, t, String.valueOf(shown),
                    DanmuTheme.TEXT_CAPTION, t.accentSoftText, true);
                badge.setGravity(Gravity.CENTER);
                badge.setBackground(t.roundRect(t.accentSoft, DanmuTheme.RADIUS_SM, activity));
                int badgeSize = DanmuDialog.dp(activity, 22);
                LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(badgeSize, badgeSize);
                badgeLp.rightMargin = DanmuDialog.dp(activity, DanmuTheme.SPACE_3);
                row.addView(badge, badgeLp);

                row.addView(
                    DanmuUi.text(activity, t, entry, DanmuTheme.TEXT_BODY, t.textPrimary, false),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowLp.bottomMargin = DanmuDialog.dp(activity, DanmuTheme.SPACE_2);
                list.addView(row, rowLp);
            }

            ScrollView scroll = new ScrollView(activity);
            scroll.setFillViewport(false);
            scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            DanmuDialog.limitHeight(scroll, DanmuDialog.maxContentHeight(activity));
            root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        Button close = DanmuUi.ghostButton(activity, t, "关闭");
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeLp.topMargin = DanmuDialog.dp(activity, DanmuTheme.SPACE_4);
        closeLp.gravity = Gravity.END;
        root.addView(close, closeLp);

        AlertDialog dialog = DanmuDialog.create(activity, root);
        close.setOnClickListener(v -> dialog.dismiss());
        DanmuDialog.showCentered(dialog, activity, 520);
        DanmuDialog.focusFirst(dialog, close);
    }
}
