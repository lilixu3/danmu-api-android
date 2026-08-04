package com.example.danmuapiapp.xposed;

import android.content.Context;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class DanmuXposedHostShell {
    private static final int DEFAULT_PORT = 9978;
    private static final int TVBOX_PORT_MAX = 9998;
    private static final String NEWBOX_PACKAGE = "com.newbox.mobile";
    private static final String NEWBOX_178_PACKAGE = "com.truthvision.homecare.nb.bn";
    private static final String TVBOX_PACKAGE = "com.github.tvbox.osc";
    private static final String FONGMI_PACKAGE = "com.fongmi.android.tv";
    private static final String FONGMI_TW_PACKAGE = "com.fongmi.android.tw";
    private static final String TVBOX_REMOTE_SERVER_CLASS =
        "com.github.tvbox.osc.server.RemoteServer";
    private static final String TVBOX_REMOTE_SERVER_PORT_FIELD = "n";
    private static final String[] FONGMI_PROXY_CLASSES = {
        "com.github.catvod.Proxy",
        "com.github.catvod.net.Proxy"
    };

    private DanmuXposedHostShell() {
    }

    static Target resolve(Context context, int fallbackPort) {
        int fallback = validPort(fallbackPort);
        if (context == null) return new Target(fallback, false);
        String packageName = context.getPackageName();
        ClassLoader loader = context.getClassLoader();
        if (isFongMiFamilyPackage(packageName)) {
            for (String className : FONGMI_PROXY_CLASSES) {
                try {
                    Class<?> proxy = Class.forName(className, false, loader);
                    int port = resolveFongMiProxyPort(proxy, fallback);
                    if (isShellPort(port)) return new Target(port, true);
                } catch (Throwable ignored) {
                }
            }
            // FongMi owns one port in 9978..9998. Do not mistake another running shell for it.
            return new Target(fallback, true);
        }
        if (!isTvBoxFamilyPackage(packageName)) return new Target(fallback, false);
        try {
            Class<?> remoteServer = Class.forName(TVBOX_REMOTE_SERVER_CLASS, false, loader);
            return new Target(resolveRemoteServerPort(remoteServer, fallback), true);
        } catch (Throwable ignored) {
            // TVBox/NewBox has no /media endpoint, so never scan into another shell process.
            return new Target(fallback, true);
        }
    }

    static int resolveRemoteServerPort(Class<?> remoteServer, int fallbackPort) {
        int fallback = validPort(fallbackPort);
        if (remoteServer == null) return fallback;
        try {
            Field field = remoteServer.getDeclaredField(TVBOX_REMOTE_SERVER_PORT_FIELD);
            int port = readStaticPort(field);
            if (isShellPort(port)) return port;
        } catch (Throwable ignored) {
        }

        int uniquePort = -1;
        for (Field field : remoteServer.getDeclaredFields()) {
            try {
                int port = readStaticPort(field);
                if (!isShellPort(port)) continue;
                if (uniquePort < 0 || uniquePort == port) {
                    uniquePort = port;
                } else {
                    return fallback;
                }
            } catch (Throwable ignored) {
            }
        }
        return uniquePort > 0 ? uniquePort : fallback;
    }

    static int resolveFongMiProxyPort(Class<?> proxy, int fallbackPort) {
        int fallback = validPort(fallbackPort);
        if (proxy == null) return fallback;
        try {
            Method method = proxy.getDeclaredMethod("getPort");
            if (!Modifier.isStatic(method.getModifiers())) return fallback;
            if (!method.isAccessible()) method.setAccessible(true);
            Object value = method.invoke(null);
            int port = value instanceof Number ? ((Number) value).intValue() : -1;
            return isShellPort(port) ? port : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    static boolean isTvBoxFamilyPackage(String packageName) {
        return NEWBOX_PACKAGE.equals(packageName) || NEWBOX_178_PACKAGE.equals(packageName) ||
            TVBOX_PACKAGE.equals(packageName);
    }

    static boolean isFongMiFamilyPackage(String packageName) {
        return FONGMI_PACKAGE.equals(packageName) || FONGMI_TW_PACKAGE.equals(packageName);
    }

    private static int readStaticPort(Field field) throws IllegalAccessException {
        if (field == null || !Modifier.isStatic(field.getModifiers())) return -1;
        if (!field.isAccessible()) field.setAccessible(true);
        Object value = field.get(null);
        return value instanceof Number ? ((Number) value).intValue() : -1;
    }

    private static boolean isShellPort(int port) {
        return port >= DEFAULT_PORT && port <= TVBOX_PORT_MAX;
    }

    private static int validPort(int port) {
        return port > 0 && port <= 65535 ? port : DEFAULT_PORT;
    }

    static final class Target {
        final int port;
        final boolean authoritative;

        Target(int port, boolean authoritative) {
            this.port = port;
            this.authoritative = authoritative;
        }
    }
}
