package com.example.danmuapiapp.xposed;

import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.formatOffsetSeconds;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.safeParseDouble;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.safeParseInt;

import android.content.Context;
import android.content.SharedPreferences;

final class DanmuXposedSettingsStore {
    static final String PREFS_INJECTION = "app_danmu_injection";

    private static final String KEY_INJECTION_ENABLED = "injection_enabled";
    private static final String KEY_AUTO_PUSH_ENABLED = "auto_push_enabled";
    private static final String KEY_CORE_PORT = "core_port";
    private static final String KEY_CORE_TOKEN = "core_token";
    private static final String KEY_OFFSET_SEC = "offset_sec";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_SHELL_PORT = "shell_port";
    private static final String KEY_UI_DARK_THEME = "ui_dark_theme";
    private static final String KEY_EPISODE_SHOW_TITLES = "episode_show_titles";
    private static final String KEY_DIALOG_STYLE = "dialog_style";

    private DanmuXposedSettingsStore() {
    }

    interface RemotePreferencesProvider {
        SharedPreferences getRemotePreferencesOrNull();
    }

    interface WarningLogger {
        void warn(String message);
    }

    static InjectionSettings readInjectionSettings(Context context, int fallbackPort, RemotePreferencesProvider remoteProvider) {
        int normalizedFallbackPort = normalizePort(fallbackPort);
        if (context == null) return defaultSettings(normalizedFallbackPort);
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_INJECTION, Context.MODE_PRIVATE);
            boolean injectionEnabled = true;
            boolean autoPush = true;
            double offset = safeParseDouble(prefs.getString(KEY_OFFSET_SEC, "0"), 0.0d);
            int fontSize = safeParseInt(prefs.getString(KEY_FONT_SIZE, ""));
            int storedPort = prefs.getInt(KEY_SHELL_PORT, normalizedFallbackPort);
            boolean darkTheme = prefs.getBoolean(KEY_UI_DARK_THEME, false);
            int dialogStyle = prefs.getInt(KEY_DIALOG_STYLE, InjectionSettings.DIALOG_STYLE_CENTER);
            int corePort = 0;
            String coreToken = "";
            SharedPreferences remotePrefs = remotePreferences(remoteProvider);
            if (remotePrefs != null) {
                injectionEnabled = remotePrefs.getBoolean(KEY_INJECTION_ENABLED, injectionEnabled);
                autoPush = remotePrefs.getBoolean(KEY_AUTO_PUSH_ENABLED, autoPush);
                offset = safeParseDouble(remotePrefs.getString(KEY_OFFSET_SEC, formatOffsetSeconds(offset)), offset);
                fontSize = safeParseInt(remotePrefs.getString(KEY_FONT_SIZE, fontSize > 0 ? String.valueOf(fontSize) : ""));
                storedPort = remotePrefs.getInt(KEY_SHELL_PORT, storedPort);
                darkTheme = remotePrefs.getBoolean(KEY_UI_DARK_THEME, darkTheme);
                dialogStyle = remotePrefs.getInt(KEY_DIALOG_STYLE, dialogStyle);
                corePort = remotePrefs.getInt(KEY_CORE_PORT, 0);
                coreToken = normalizeToken(remotePrefs.getString(KEY_CORE_TOKEN, ""));
            }
            int port = normalizePortOrFallback(storedPort, normalizedFallbackPort);
            return new InjectionSettings(injectionEnabled, autoPush, offset, fontSize > 0 ? fontSize : -1, port, darkTheme, corePort, coreToken, dialogStyle);
        } catch (Throwable throwable) {
            return defaultSettings(normalizedFallbackPort);
        }
    }

    static boolean saveInjectionSettings(
        Context context,
        InjectionSettings settings,
        RemotePreferencesProvider remoteProvider,
        WarningLogger logger
    ) {
        if (context == null || settings == null) return false;
        try {
            String formattedOffset = formatOffsetSeconds(settings.offsetSec);
            String formattedFontSize = settings.fontSize > 0 ? String.valueOf(settings.fontSize) : "";
            SharedPreferences localPrefs = context.getSharedPreferences(PREFS_INJECTION, Context.MODE_PRIVATE);
            boolean localOk = commitInjectionSettings(localPrefs, formattedOffset, formattedFontSize, settings);
            boolean remoteAttempted = false;
            boolean remoteOk = false;
            SharedPreferences remotePrefs = null;
            try {
                remotePrefs = remotePreferences(remoteProvider);
            } catch (Throwable remoteEx) {
                warn(logger, "save injection settings remote prefs unavailable: " + remoteEx.getMessage());
            }
            if (remotePrefs != null) {
                remoteAttempted = true;
                try {
                    remoteOk = commitInjectionSettings(remotePrefs, formattedOffset, formattedFontSize, settings);
                    if (!remoteOk) warn(logger, "save injection settings failed: remote commit returned false");
                } catch (Throwable remoteEx) {
                    warn(logger, "save injection settings remote write failed: " + remoteEx.getMessage());
                }
            }
            if (!localOk) warn(logger, "save injection settings failed: local commit returned false");
            if (localOk) return true;
            return remoteAttempted && remoteOk;
        } catch (Throwable throwable) {
            warn(logger, "save injection settings failed: " + throwable.getMessage());
            return false;
        }
    }

    static boolean readEpisodeShowTitles(Context context, RemotePreferencesProvider remoteProvider) {
        if (context == null) return false;
        try {
            SharedPreferences remotePrefs = remotePreferences(remoteProvider);
            if (remotePrefs != null && remotePrefs.contains(KEY_EPISODE_SHOW_TITLES)) {
                return remotePrefs.getBoolean(KEY_EPISODE_SHOW_TITLES, false);
            }
            return context.getSharedPreferences(PREFS_INJECTION, Context.MODE_PRIVATE)
                .getBoolean(KEY_EPISODE_SHOW_TITLES, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean saveEpisodeShowTitles(
        Context context,
        boolean showTitles,
        RemotePreferencesProvider remoteProvider,
        WarningLogger logger
    ) {
        if (context == null) return false;
        try {
            SharedPreferences localPrefs = context.getSharedPreferences(PREFS_INJECTION, Context.MODE_PRIVATE);
            boolean localOk = commitEpisodeShowTitles(localPrefs, showTitles);
            boolean remoteAttempted = false;
            boolean remoteOk = false;
            SharedPreferences remotePrefs = null;
            try {
                remotePrefs = remotePreferences(remoteProvider);
            } catch (Throwable remoteEx) {
                warn(logger, "save episode show titles remote prefs unavailable: " + remoteEx.getMessage());
            }
            if (remotePrefs != null) {
                remoteAttempted = true;
                try {
                    remoteOk = commitEpisodeShowTitles(remotePrefs, showTitles);
                    if (!remoteOk) warn(logger, "save episode show titles failed: remote commit returned false");
                } catch (Throwable remoteEx) {
                    warn(logger, "save episode show titles remote write failed: " + remoteEx.getMessage());
                }
            }
            if (!localOk) warn(logger, "save episode show titles failed: local commit returned false");
            if (localOk) return true;
            return remoteAttempted && remoteOk;
        } catch (Throwable throwable) {
            warn(logger, "save episode show titles failed: " + throwable.getMessage());
            return false;
        }
    }

    private static boolean commitInjectionSettings(SharedPreferences prefs, String formattedOffset, String formattedFontSize, InjectionSettings settings) {
        if (prefs == null || settings == null) return false;
        return prefs.edit()
            .putString(KEY_OFFSET_SEC, formattedOffset)
            .putString(KEY_FONT_SIZE, formattedFontSize)
            .putInt(KEY_SHELL_PORT, settings.shellPort)
            .putBoolean(KEY_UI_DARK_THEME, settings.darkTheme)
            .putInt(KEY_DIALOG_STYLE, settings.dialogStyle)
            .commit();
    }

    private static boolean commitEpisodeShowTitles(SharedPreferences prefs, boolean showTitles) {
        if (prefs == null) return false;
        return prefs.edit()
            .putBoolean(KEY_EPISODE_SHOW_TITLES, showTitles)
            .commit();
    }

    private static InjectionSettings defaultSettings(int normalizedFallbackPort) {
        return new InjectionSettings(true, true, 0.0d, -1, normalizedFallbackPort, false, 0, "", InjectionSettings.DIALOG_STYLE_CENTER);
    }

    private static SharedPreferences remotePreferences(RemotePreferencesProvider remoteProvider) {
        return remoteProvider == null ? null : remoteProvider.getRemotePreferencesOrNull();
    }

    private static int normalizePort(int port) {
        return port > 0 && port <= 65535 ? port : 9978;
    }

    private static int normalizePortOrFallback(int port, int fallback) {
        return port > 0 && port <= 65535 ? port : fallback;
    }

    private static String normalizeToken(String value) {
        String token = value == null ? "" : value.trim();
        if (token.equalsIgnoreCase("null") || token.equalsIgnoreCase("undefined")) return "";
        while (token.startsWith("/")) token = token.substring(1);
        return token.trim();
    }

    private static void warn(WarningLogger logger, String message) {
        if (logger != null) logger.warn(message);
    }
}
