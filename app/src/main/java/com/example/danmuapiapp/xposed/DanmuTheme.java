package com.example.danmuapiapp.xposed;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;

import com.example.danmuapiapp.ui.theme.DialogColorTokens;

/**
 * Design tokens for injected dialogs: spacing, radii, type scale and the light/dark
 * palettes. Focus colours are separate from accent colours so a D-pad focus ring stays
 * visible on top of an already-selected (accent filled) cell.
 */
final class DanmuTheme {

    // ---- Spacing scale (dp) ----
    static final int SPACE_1 = 4;
    static final int SPACE_2 = 8;
    static final int SPACE_3 = 12;
    static final int SPACE_4 = 16;
    static final int SPACE_5 = 20;
    static final int SPACE_6 = 24;

    // ---- Corner radii (dp) ----
    static final float RADIUS_SM = 10f;
    static final float RADIUS_MD = 14f;
    static final float RADIUS_LG = 20f;
    static final float RADIUS_XL = 26f;
    static final float RADIUS_PILL = 999f;

    // ---- Type scale (sp) ----
    static final float TEXT_MICRO = 10f;
    static final float TEXT_CAPTION = 11f;
    static final float TEXT_BODY = 13f;
    static final float TEXT_LABEL = 14f;
    static final float TEXT_TITLE = 16f;
    static final float TEXT_HEADLINE = 19f;

    // ---- Border widths (dp) ----
    static final int STROKE_HAIRLINE = 1;
    static final int STROKE_FOCUS = 2;

    final boolean dark;

    // ---- Surfaces ----
    final int dialogBackground;
    final int surface;        // card / panel fill
    final int surfaceAlt;     // nested / secondary surface
    final int surfaceActive;  // pressed surface
    final int stroke;         // hairline border
    final int strokeStrong;   // emphasised border

    // ---- Text ----
    final int textPrimary;
    final int textSecondary;
    final int textMuted;

    // ---- Accent (selection / primary action) ----
    final int accent;
    final int accentStrong;
    final int accentText;
    final int accentSoft;
    final int accentSoftText;

    // ---- Focus (D-pad / keyboard traversal only) ----
    final int focusRing;
    final int focusFill;
    final int focusText;

    // ---- Semantic ----
    final int success;
    final int successSoft;
    final int successText;
    final int danger;

    private DanmuTheme(boolean dark) {
        this.dark = dark;
        if (dark) {
            dialogBackground = DialogColorTokens.DARK_DIALOG;
            surface = DialogColorTokens.DARK_SURFACE_CONTAINER;
            surfaceAlt = DialogColorTokens.DARK_SURFACE_HIGH;
            surfaceActive = DialogColorTokens.DARK_SURFACE_ACTIVE;
            stroke = DialogColorTokens.DARK_OUTLINE_VARIANT;
            strokeStrong = DialogColorTokens.DARK_OUTLINE;
            textPrimary = DialogColorTokens.DARK_TEXT_PRIMARY;
            textSecondary = DialogColorTokens.DARK_TEXT_SECONDARY;
            textMuted = DialogColorTokens.DARK_TEXT_MUTED;
            accent = DialogColorTokens.DARK_PRIMARY;
            accentStrong = DialogColorTokens.DARK_PRIMARY_PRESSED;
            accentText = DialogColorTokens.DARK_ON_PRIMARY;
            accentSoft = DialogColorTokens.DARK_PRIMARY_CONTAINER;
            accentSoftText = DialogColorTokens.DARK_ON_PRIMARY_CONTAINER;
            focusRing = DialogColorTokens.DARK_FOCUS_RING;
            focusFill = DialogColorTokens.DARK_FOCUS_FILL;
            focusText = DialogColorTokens.DARK_TEXT_PRIMARY;
            success = DialogColorTokens.DARK_SUCCESS;
            successSoft = DialogColorTokens.DARK_SUCCESS_CONTAINER;
            successText = DialogColorTokens.DARK_SUCCESS;
            danger = DialogColorTokens.DARK_ERROR;
        } else {
            dialogBackground = DialogColorTokens.LIGHT_DIALOG;
            surface = DialogColorTokens.LIGHT_SURFACE_CONTAINER;
            surfaceAlt = DialogColorTokens.LIGHT_SURFACE_HIGH;
            surfaceActive = DialogColorTokens.LIGHT_SURFACE_ACTIVE;
            stroke = DialogColorTokens.LIGHT_OUTLINE_VARIANT;
            strokeStrong = DialogColorTokens.LIGHT_OUTLINE;
            textPrimary = DialogColorTokens.LIGHT_TEXT_PRIMARY;
            textSecondary = DialogColorTokens.LIGHT_TEXT_SECONDARY;
            textMuted = DialogColorTokens.LIGHT_TEXT_MUTED;
            accent = DialogColorTokens.LIGHT_PRIMARY;
            accentStrong = DialogColorTokens.LIGHT_PRIMARY_PRESSED;
            accentText = DialogColorTokens.LIGHT_ON_PRIMARY;
            accentSoft = DialogColorTokens.LIGHT_PRIMARY_CONTAINER;
            accentSoftText = DialogColorTokens.LIGHT_ON_PRIMARY_CONTAINER;
            focusRing = DialogColorTokens.LIGHT_FOCUS_RING;
            focusFill = DialogColorTokens.LIGHT_FOCUS_FILL;
            focusText = DialogColorTokens.LIGHT_TEXT_PRIMARY;
            success = DialogColorTokens.LIGHT_SUCCESS;
            successSoft = DialogColorTokens.LIGHT_SUCCESS_CONTAINER;
            successText = DialogColorTokens.LIGHT_SUCCESS;
            danger = DialogColorTokens.LIGHT_ERROR;
        }
    }

    static DanmuTheme of(boolean dark) {
        return new DanmuTheme(dark);
    }

    int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics()));
    }

    /** Solid rounded rect with optional border. */
    GradientDrawable roundRect(int fill, float radiusDp, int strokeColor, int strokeWidthDp, Context context) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(context, radiusDp));
        if (strokeWidthDp > 0) d.setStroke(dp(context, strokeWidthDp), strokeColor);
        return d;
    }

    GradientDrawable roundRect(int fill, float radiusDp, Context context) {
        return roundRect(fill, radiusDp, 0, 0, context);
    }

    GradientDrawable accentRect(float radiusDp, Context context) {
        return roundRect(accent, radiusDp, context);
    }

    GradientDrawable selectionRect(float radiusDp, Context context) {
        return roundRect(accentSoft, radiusDp, accent, STROKE_HAIRLINE, context);
    }

    /**
     * Background for anything reachable by D-pad but with no selected state of its own
     * (buttons, rows, fields). Focus wins over pressed so the ring is never hidden.
     */
    Drawable focusable(int fill, float radiusDp, int strokeColor, Context context) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focusLayer(fill, radiusDp, context));
        states.addState(
            new int[]{android.R.attr.state_pressed},
            roundRect(surfaceActive, radiusDp, strokeStrong, STROKE_HAIRLINE, context));
        states.addState(new int[]{}, roundRect(fill, radiusDp, strokeColor, STROKE_HAIRLINE, context));
        return states;
    }

    /**
     * Background for widgets that carry their own selected state (chips, episode cells,
     * result rows). Focus is checked first so the ring stays visible on a selected fill.
     */
    Drawable selectable(int fill, float radiusDp, int strokeColor, Context context) {
        StateListDrawable states = new StateListDrawable();
        states.addState(
            new int[]{android.R.attr.state_focused, android.R.attr.state_selected},
            selectionFocusLayer(radiusDp, context));
        states.addState(new int[]{android.R.attr.state_focused}, focusLayer(fill, radiusDp, context));
        states.addState(new int[]{android.R.attr.state_selected}, selectionRect(radiusDp, context));
        states.addState(
            new int[]{android.R.attr.state_pressed},
            roundRect(surfaceActive, radiusDp, strokeStrong, STROKE_HAIRLINE, context));
        states.addState(new int[]{}, roundRect(fill, radiusDp, strokeColor, STROKE_HAIRLINE, context));
        return states;
    }

    /** Background for the primary action: one solid brand color with a focus ring on top. */
    Drawable accentFocusable(float radiusDp, Context context) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, accentFocusLayer(radiusDp, context));
        states.addState(
            new int[]{android.R.attr.state_pressed},
            roundRect(accentStrong, radiusDp, accentStrong, STROKE_HAIRLINE, context));
        states.addState(new int[]{}, accentRect(radiusDp, context));
        return states;
    }

    private Drawable focusLayer(int fill, float radiusDp, Context context) {
        return roundRect(blend(focusFill, fill), radiusDp, focusRing, STROKE_FOCUS, context);
    }

    private Drawable selectionFocusLayer(float radiusDp, Context context) {
        return roundRect(accentSoft, radiusDp, focusRing, STROKE_FOCUS, context);
    }

    private Drawable accentFocusLayer(float radiusDp, Context context) {
        return roundRect(accent, radiusDp, focusRing, STROKE_FOCUS, context);
    }

    /** Index badge fill stays softly tinted in every parent state. */
    Drawable badgeFill(float radiusDp, Context context) {
        return roundRect(accentSoft, radiusDp, context);
    }

    /** Text colour that follows selected/focused state, for widgets restyled by state alone. */
    ColorStateList selectableText(int normal) {
        return new ColorStateList(
            new int[][]{
                new int[]{android.R.attr.state_selected},
                new int[]{android.R.attr.state_focused},
                new int[]{}
            },
            new int[]{accentSoftText, focusText, normal});
    }

    /** Alpha-composites {@code over} onto opaque {@code under}. */
    static int blend(int over, int under) {
        int a = (over >>> 24);
        if (a == 0) return under;
        if (a == 255) return over;
        float ratio = a / 255f;
        int r = Math.round(((over >> 16) & 0xFF) * ratio + ((under >> 16) & 0xFF) * (1f - ratio));
        int g = Math.round(((over >> 8) & 0xFF) * ratio + ((under >> 8) & 0xFF) * (1f - ratio));
        int b = Math.round((over & 0xFF) * ratio + (under & 0xFF) * (1f - ratio));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
