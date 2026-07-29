package com.example.danmuapiapp.xposed;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;

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
    final int accentEnd;      // gradient partner for accent
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
            dialogBackground = 0xFF0E1117;
            surface = 0xFF191F29;
            surfaceAlt = 0xFF141A23;
            surfaceActive = 0xFF2A3442;
            stroke = 0x14FFFFFF;
            strokeStrong = 0x2EFFFFFF;
            textPrimary = 0xFFF2F5FA;
            textSecondary = 0xFFA9B4C4;
            textMuted = 0xFF6F7B8C;
            accent = 0xFF4F7CFF;
            accentEnd = 0xFF7C5CFF;
            accentStrong = 0xFF8AA9FF;
            accentText = 0xFFFFFFFF;
            accentSoft = 0x2E4F7CFF;
            accentSoftText = 0xFFA9C2FF;
            focusRing = 0xFFFFC44D;
            focusFill = 0x33FFC44D;
            focusText = 0xFFFFE1A3;
            success = 0xFF22C55E;
            successSoft = 0x2622C55E;
            successText = 0xFF86EFAC;
            danger = 0xFFF87171;
        } else {
            dialogBackground = 0xFFFFFFFF;
            surface = 0xFFFFFFFF;
            surfaceAlt = 0xFFF4F6FA;
            surfaceActive = 0xFFE4E9F2;
            stroke = 0xFFE6EAF2;
            strokeStrong = 0xFFCBD4E3;
            textPrimary = 0xFF0F1626;
            textSecondary = 0xFF525F73;
            textMuted = 0xFF8B96A8;
            accent = 0xFF3B62F6;
            accentEnd = 0xFF7A45E8;
            accentStrong = 0xFF2E4FD6;
            accentText = 0xFFFFFFFF;
            accentSoft = 0xFFEDF1FE;
            accentSoftText = 0xFF2743B8;
            focusRing = 0xFFB45309;
            focusFill = 0xFFFDF3D8;
            focusText = 0xFF7C4A05;
            success = 0xFF16A34A;
            successSoft = 0xFFE7F6EC;
            successText = 0xFF15803D;
            danger = 0xFFDC2626;
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

    /** Diagonal two-stop fill; used for accent surfaces so selection reads as a solid object. */
    GradientDrawable gradientRect(int start, int end, float radiusDp, Context context) {
        GradientDrawable d = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        d.setCornerRadius(dp(context, radiusDp));
        return d;
    }

    GradientDrawable accentRect(float radiusDp, Context context) {
        return gradientRect(accent, accentEnd, radiusDp, context);
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
            selectedFocusLayer(radiusDp, context));
        states.addState(new int[]{android.R.attr.state_focused}, focusLayer(fill, radiusDp, context));
        states.addState(new int[]{android.R.attr.state_selected}, accentRect(radiusDp, context));
        states.addState(
            new int[]{android.R.attr.state_pressed},
            roundRect(surfaceActive, radiusDp, strokeStrong, STROKE_HAIRLINE, context));
        states.addState(new int[]{}, roundRect(fill, radiusDp, strokeColor, STROKE_HAIRLINE, context));
        return states;
    }

    /** Background for the primary action: accent gradient with a focus ring on top. */
    Drawable accentFocusable(float radiusDp, Context context) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, selectedFocusLayer(radiusDp, context));
        states.addState(
            new int[]{android.R.attr.state_pressed},
            roundRect(accentStrong, radiusDp, accentStrong, STROKE_HAIRLINE, context));
        states.addState(new int[]{}, accentRect(radiusDp, context));
        return states;
    }

    private Drawable focusLayer(int fill, float radiusDp, Context context) {
        return roundRect(blend(focusFill, fill), radiusDp, focusRing, STROKE_FOCUS, context);
    }

    private Drawable selectedFocusLayer(float radiusDp, Context context) {
        GradientDrawable d = accentRect(radiusDp, context);
        d.setStroke(dp(context, STROKE_FOCUS), focusRing);
        return d;
    }

    /** Index badge fill: tinted on a plain surface, translucent on a selected accent fill. */
    Drawable badgeFill(float radiusDp, Context context) {
        StateListDrawable states = new StateListDrawable();
        states.addState(
            new int[]{android.R.attr.state_selected},
            roundRect(0x33FFFFFF, radiusDp, context));
        states.addState(new int[]{}, roundRect(accentSoft, radiusDp, context));
        return states;
    }

    /** Text colour that follows selected/focused state, for widgets restyled by state alone. */
    ColorStateList selectableText(int normal) {
        return new ColorStateList(
            new int[][]{
                new int[]{android.R.attr.state_selected},
                new int[]{android.R.attr.state_focused},
                new int[]{}
            },
            new int[]{accentText, focusText, normal});
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
