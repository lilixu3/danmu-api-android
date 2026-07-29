package com.example.danmuapiapp.xposed;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared window and form contract for dialogs injected into host applications. */
final class DanmuDialog {
    private static final int DEFAULT_MAX_WIDTH_DP = 560;
    private static final int MIN_WIDTH_DP = 300;
    private static final float MAX_HEIGHT_RATIO = 0.88f;
    private static final float DIM_AMOUNT = 0.62f;

    private DanmuDialog() {}

    interface InputValidator {
        /** Returns an error message, or an empty value when the input is valid. */
        String validate(String value);
    }

    static AlertDialog create(Activity activity, View content) {
        return new AlertDialog.Builder(activity)
            .setView(content)
            .create();
    }

    static boolean isLandscape(Activity activity) {
        return activity.getResources().getConfiguration().orientation
            == Configuration.ORIENTATION_LANDSCAPE;
    }

    static void showCentered(AlertDialog dialog, Activity activity) {
        showCentered(dialog, activity, DEFAULT_MAX_WIDTH_DP);
    }

    static void showCentered(AlertDialog dialog, Activity activity, int maxWidthDp) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) return;
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int horizontalMargin = dp(activity, DanmuTheme.SPACE_4) * 2;
        int availableWidth = Math.max(dp(activity, 240), metrics.widthPixels - horizontalMargin);
        int cap = dp(activity, Math.max(MIN_WIDTH_DP, maxWidthDp));
        int width = Math.min(availableWidth, cap);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setGravity(Gravity.CENTER);
        window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = DIM_AMOUNT;
        window.setAttributes(attributes);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }

    /** Height the dialog window may occupy, before its own padding and chrome. */
    static int maxDialogHeight(Activity activity) {
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        return Math.max(dp(activity, 200), Math.round(screenHeight * MAX_HEIGHT_RATIO));
    }

    /** Height budget for a scrollable body, after subtracting {@code chromeDp} of surrounding UI. */
    static int contentHeight(Activity activity, int chromeDp) {
        int budget = maxDialogHeight(activity)
            - dp(activity, DanmuTheme.SPACE_4) * 2
            - dp(activity, chromeDp);
        return Math.max(dp(activity, 160), budget);
    }

    /** Height budget for scrollable dialog bodies so landscape content stays on screen. */
    static int maxContentHeight(Activity activity) {
        return contentHeight(activity, 96);
    }

    /** Clamps a scrollable body to {@code maxPx} once it has been laid out. */
    static void limitHeight(View view, int maxPx) {
        view.addOnLayoutChangeListener((v, left, top, right, bottom, ol, ot, or, ob) -> {
            int height = bottom - top;
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (height > maxPx && lp != null && lp.height != maxPx) {
                lp.height = maxPx;
                v.setLayoutParams(lp);
            }
        });
    }

    /**
     * Gives the dialog an initial focus target. Without this a remote user opens the dialog
     * with focus nowhere and the first key press is swallowed.
     */
    static void focusFirst(AlertDialog dialog, View target) {
        if (target == null) return;
        Window window = dialog.getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        }
        target.post(() -> {
            // In touch mode a forced focus ring would linger on every tap, so only
            // seed focus for D-pad/keyboard navigation.
            if (target.isInTouchMode()) return;
            target.requestFocus();
        });
    }

    static AlertDialog showTextInput(
        Activity activity,
        DanmuTheme theme,
        String title,
        String hint,
        String initialValue,
        int inputType,
        InputValidator validator,
        StringValueCallback callback
    ) {
        LinearLayout root = root(activity, theme);
        root.addView(DanmuUi.title(activity, theme, title),
            matchWrapWithBottom(activity, DanmuTheme.SPACE_2));

        if (hint != null && !hint.trim().isEmpty()) {
            TextView supporting = DanmuUi.text(
                activity, theme, hint, DanmuTheme.TEXT_BODY, theme.textSecondary, false);
            root.addView(supporting, matchWrapWithBottom(activity, DanmuTheme.SPACE_4));
        }

        EditText input = DanmuUi.textField(activity, theme, hint, initialValue);
        input.setInputType(inputType);
        root.addView(input, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 52)));

        TextView error = DanmuUi.text(
            activity, theme, "", DanmuTheme.TEXT_CAPTION, theme.danger, false);
        error.setVisibility(View.GONE);
        LinearLayout.LayoutParams errorLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        errorLp.topMargin = dp(activity, DanmuTheme.SPACE_2);
        root.addView(error, errorLp);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Button cancel = DanmuUi.ghostButton(activity, theme, "取消");
        Button save = DanmuUi.primaryButton(activity, theme, "保存");
        actions.addView(cancel, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveLp.leftMargin = dp(activity, DanmuTheme.SPACE_2);
        actions.addView(save, saveLp);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(activity, DanmuTheme.SPACE_4);
        root.addView(actions, actionsLp);

        AlertDialog dialog = create(activity, root);
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            String value = input.getText() == null ? "" : input.getText().toString().trim();
            String message = validator == null ? "" : validator.validate(value);
            if (message != null && !message.trim().isEmpty()) {
                error.setText(message);
                error.setVisibility(View.VISIBLE);
                input.setError(message);
                input.requestFocus();
                return;
            }
            error.setVisibility(View.GONE);
            callback.onValue(value);
            dialog.dismiss();
        });
        showCentered(dialog, activity, 480);
        input.requestFocus();
        return dialog;
    }

    static AlertDialog showSingleChoice(
        Activity activity,
        DanmuTheme theme,
        String title,
        String[] labels,
        int checkedIndex,
        IntValueCallback callback
    ) {
        LinearLayout root = root(activity, theme);
        root.addView(DanmuUi.title(activity, theme, title),
            matchWrapWithBottom(activity, DanmuTheme.SPACE_3));

        LinearLayout options = new LinearLayout(activity);
        options.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(activity);
        scroll.setScrollBarSize(0);
        scroll.addView(options, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = create(activity, root);
        View firstFocus = null;
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            Button option = DanmuUi.toggleChip(activity, theme, labels[i], i == checkedIndex);
            option.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            option.setOnClickListener(v -> {
                dialog.dismiss();
                callback.onValue(index);
            });
            LinearLayout.LayoutParams optionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) optionLp.topMargin = dp(activity, DanmuTheme.SPACE_2);
            options.addView(option, optionLp);
            if (i == checkedIndex || firstFocus == null) firstFocus = option;
        }
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scrollLp.weight = 1f;
        root.addView(scroll, scrollLp);

        Button cancel = DanmuUi.ghostButton(activity, theme, "取消");
        cancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelLp.gravity = Gravity.END;
        cancelLp.topMargin = dp(activity, DanmuTheme.SPACE_4);
        root.addView(cancel, cancelLp);

        showCentered(dialog, activity, 480);
        focusFirst(dialog, firstFocus);
        return dialog;
    }

    static LinearLayout root(Activity activity, DanmuTheme theme) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(activity, DanmuTheme.SPACE_5);
        int vertical = dp(activity, DanmuTheme.SPACE_4);
        root.setPadding(horizontal, vertical, horizontal, vertical);
        root.setBackground(theme.roundRect(
            theme.dialogBackground, DanmuTheme.RADIUS_LG,
            theme.strokeStrong, DanmuTheme.STROKE_HAIRLINE, activity));
        return root;
    }

    static LinearLayout.LayoutParams matchWrapWithBottom(Activity activity, int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(activity, bottomDp);
        return lp;
    }

    static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
