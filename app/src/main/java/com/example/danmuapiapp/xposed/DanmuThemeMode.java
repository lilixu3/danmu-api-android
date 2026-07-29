package com.example.danmuapiapp.xposed;

import android.content.Context;
import android.content.res.Configuration;
import android.util.TypedValue;

/** Persisted theme choice for injected dialogs. */
enum DanmuThemeMode {
    FOLLOW_HOST(0, "跟随宿主"),
    LIGHT(1, "亮色"),
    DARK(2, "暗色");

    final int persistedValue;
    final String label;

    DanmuThemeMode(int persistedValue, String label) {
        this.persistedValue = persistedValue;
        this.label = label;
    }

    static DanmuThemeMode fromPersistedValue(int value) {
        for (DanmuThemeMode mode : values()) {
            if (mode.persistedValue == value) return mode;
        }
        return FOLLOW_HOST;
    }

    static DanmuThemeMode fromLegacyDarkTheme(boolean dark) {
        return dark ? DARK : LIGHT;
    }

    static String[] labels() {
        DanmuThemeMode[] modes = values();
        String[] labels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) labels[i] = modes[i].label;
        return labels;
    }

    boolean resolveDark(Context context) {
        if (this == DARK) return true;
        if (this == LIGHT || context == null) return false;

        try {
            TypedValue value = new TypedValue();
            if (context.getTheme() != null && context.getTheme().resolveAttribute(
                android.R.attr.isLightTheme, value, true)) {
                return value.data == 0;
            }
        } catch (Throwable ignored) {
            // Fall through to uiMode when a host theme does not expose isLightTheme safely.
        }

        int nightMode = context.getResources().getConfiguration().uiMode
            & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }
}
