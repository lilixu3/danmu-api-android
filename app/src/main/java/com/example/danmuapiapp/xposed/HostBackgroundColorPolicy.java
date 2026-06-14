package com.example.danmuapiapp.xposed;

import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;

import java.lang.reflect.Method;

final class HostBackgroundColorPolicy {
    private HostBackgroundColorPolicy() {
    }

    static boolean isDefaultSolidColor(int color) {
        int alpha = (color >>> 24) & 0xFF;
        if (alpha <= 0x08) return true;

        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;

        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        if (red >= 0xF8 && green >= 0xF8 && blue >= 0xF8) return true;
        if (min >= 0xF0 && max - min <= 0x18) return true;
        return false;
    }

    static boolean isMeaningfulDrawable(Drawable drawable) {
        if (drawable == null) return false;

        Integer solidColor = extractDefaultColor(drawable);
        if (solidColor != null) {
            return !isDefaultSolidColor(solidColor);
        }

        if (drawable instanceof LayerDrawable) {
            LayerDrawable layer = (LayerDrawable) drawable;
            boolean hasDrawableLayer = false;
            for (int i = 0; i < layer.getNumberOfLayers(); i++) {
                Drawable child = layer.getDrawable(i);
                if (child == null) continue;
                hasDrawableLayer = true;
                if (isMeaningfulDrawable(child)) return true;
            }
            if (hasDrawableLayer) return false;
        }

        if (drawable instanceof GradientDrawable && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return true;
        }

        return true;
    }

    private static Integer extractDefaultColor(Drawable drawable) {
        if (drawable instanceof ColorDrawable) {
            return ((ColorDrawable) drawable).getColor();
        }

        if (drawable instanceof GradientDrawable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ColorStateList colors = ((GradientDrawable) drawable).getColor();
                if (colors != null) return colors.getDefaultColor();
            }
            return null;
        }

        for (String methodName : new String[]{"getFillColor", "getColor"}) {
            try {
                Method method = drawable.getClass().getMethod(methodName);
                Object value = method.invoke(drawable);
                if (value instanceof ColorStateList) {
                    return ((ColorStateList) value).getDefaultColor();
                }
                if (value instanceof Integer) {
                    return (Integer) value;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
