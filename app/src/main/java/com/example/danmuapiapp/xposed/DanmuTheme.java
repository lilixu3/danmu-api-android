package com.example.danmuapiapp.xposed;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;

/**
 * Design tokens for the injection sheets. This is the single source of truth for
 * spacing, corner radii, type scale and the light/dark colour palettes used by the
 * immersive "card" visual style. It has zero dependency on the injection/bridge logic
 * so the visual language can evolve in one place.
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
    static final float RADIUS_SHEET = 26f;
    static final float RADIUS_PILL = 999f;

    // ---- Type scale (sp) ----
    static final float TEXT_CAPTION = 11f;
    static final float TEXT_BODY = 13f;
    static final float TEXT_LABEL = 14f;
    static final float TEXT_TITLE = 16f;
    static final float TEXT_HEADLINE = 19f;

    private final boolean dark;

    // ---- Resolved palette ----
    final int sheetBg;        // sheet backdrop (the drawer surface)
    final int surface;        // card / panel fill
    final int surfaceAlt;     // nested / secondary surface
    final int surfaceActive;  // pressed / hovered surface
    final int stroke;         // hairline border
    final int strokeStrong;   // emphasised border
    final int textPrimary;
    final int textSecondary;
    final int textMuted;
    final int accent;         // brand / primary action
    final int accentStrong;   // primary border glow
    final int accentText;     // text on accent
    final int accentSoft;     // tinted accent fill (chips)
    final int accentSoftText;
    final int success;
    final int successSoft;
    final int successText;
    final int danger;
    final int handle;         // drag handle colour
    final int scrim;          // dim behind sheet

    private DanmuTheme(boolean dark) {
        this.dark = dark;
        if (dark) {
            sheetBg = 0xFF0E1116;
            surface = 0xFF161B22;
            surfaceAlt = 0xFF1C232D;
            surfaceActive = 0xFF243040;
            stroke = 0x1FFFFFFF;
            strokeStrong = 0x3DFFFFFF;
            textPrimary = 0xFFF2F5F9;
            textSecondary = 0xFFB6C0CC;
            textMuted = 0xFF7E8A98;
            accent = 0xFF3B82F6;
            accentStrong = 0xFF60A5FA;
            accentText = 0xFFFFFFFF;
            accentSoft = 0x263B82F6;
            accentSoftText = 0xFF93C5FD;
            success = 0xFF22C55E;
            successSoft = 0x2622C55E;
            successText = 0xFF86EFAC;
            danger = 0xFFF87171;
            handle = 0x33FFFFFF;
            scrim = 0x99000000;
        } else {
            sheetBg = 0xFFF6F8FB;
            surface = 0xFFFFFFFF;
            surfaceAlt = 0xFFF1F4F8;
            surfaceActive = 0xFFE6EBF2;
            stroke = 0xFFE3E8EF;
            strokeStrong = 0xFFCBD5E1;
            textPrimary = 0xFF0F172A;
            textSecondary = 0xFF475569;
            textMuted = 0xFF94A3B8;
            accent = 0xFF2563EB;
            accentStrong = 0xFF3B82F6;
            accentText = 0xFFFFFFFF;
            accentSoft = 0xFFEAF1FE;
            accentSoftText = 0xFF1D4ED8;
            success = 0xFF16A34A;
            successSoft = 0xFFEAF7EE;
            successText = 0xFF15803D;
            danger = 0xFFDC2626;
            handle = 0x33000000;
            scrim = 0x80000000;
        }
    }

    static DanmuTheme of(boolean dark) {
        return new DanmuTheme(dark);
    }

    boolean isDark() {
        return dark;
    }

    int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics()));
    }

    /** Solid rounded rect with optional hairline border. */
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

    /** Sheet surface: only the top corners are rounded (drawer rises from the bottom edge). */
    GradientDrawable topRoundedSheet(Context context) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(sheetBg);
        float r = dp(context, RADIUS_SHEET);
        d.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        return d;
    }
}
