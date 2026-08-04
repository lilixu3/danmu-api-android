package com.example.danmuapiapp.xposed;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

final class DanmuXposedHttp {
    private DanmuXposedHttp() {
    }

    static PreparedDanmaku prepareDanmaku(int corePort, String danmakuUrl) throws Exception {
        String prepareUrl = buildDanmakuPrepareUrl(corePort, danmakuUrl);
        return parsePreparedDanmakuResponse(httpGet(prepareUrl, 1800, 25000), corePort);
    }

    static String buildDanmakuPrepareUrl(int corePort, String danmakuUrl) throws Exception {
        if (corePort <= 0 || corePort > 65535) {
            throw new IllegalArgumentException("无效的核心端口：" + corePort);
        }
        String value = danmakuUrl == null ? "" : danmakuUrl.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("弹幕 URL 为空");
        return "http://127.0.0.1:" + corePort + "/__danmaku/prepare?url=" + urlEncode(value);
    }

    static PreparedDanmaku parsePreparedDanmakuResponse(String body, int expectedCorePort) throws Exception {
        String value = body == null ? "" : body.trim();
        if (value.isEmpty()) throw new IllegalStateException("弹幕预取返回空响应");
        JSONObject root = new JSONObject(value);
        if (!root.optBoolean("success", false)) {
            String message = root.optString("errorMessage", "弹幕预取失败").trim();
            throw new IllegalStateException(message.isEmpty() ? "弹幕预取失败" : message);
        }
        String url = root.optString("url", "").trim();
        int count = root.optInt("count", -1);
        if (url.isEmpty() || count < 0) {
            throw new IllegalStateException("弹幕预取响应缺少 URL 或数量");
        }
        URL parsed = new URL(url);
        String host = parsed.getHost() == null ? "" : parsed.getHost().toLowerCase(Locale.ROOT);
        int port = parsed.getPort() > 0 ? parsed.getPort() : parsed.getDefaultPort();
        boolean loopback = "localhost".equals(host) || "::1".equals(host) || host.startsWith("127.");
        if (!"http".equalsIgnoreCase(parsed.getProtocol()) || !loopback || port != expectedCorePort ||
            !parsed.getPath().startsWith("/__danmaku/prepared/")) {
            throw new IllegalStateException("弹幕预取返回了不可信的本地 URL");
        }
        return new PreparedDanmaku(url, count, root.optLong("size", 0L), root.optLong("expiresAt", 0L));
    }

    static String buildShellPushUrl(int shellPort, String danmakuUrl) throws Exception {
        int port = shellPort > 0 && shellPort <= 65535 ? shellPort : 9978;
        return "http://127.0.0.1:" + port + "/action?do=refresh&type=danmaku&path=" + urlEncode(danmakuUrl);
    }

    static String urlEncode(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8").replace("+", "%20");
    }

    static boolean isSuccessfulShellPushResponse(String body) {
        if (body == null) return false;
        String value = body.trim();
        if (value.isEmpty()) return false;
        if (value.startsWith("{")) {
            try {
                JSONObject root = new JSONObject(value);
                if (root.has("ok")) return isSuccessfulJsonValue(root.opt("ok"));
                if (root.has("success")) return isSuccessfulJsonValue(root.opt("success"));
                for (String key : new String[]{"status", "message", "result"}) {
                    if (root.has(key) && isSuccessfulStatusToken(String.valueOf(root.opt(key)))) return true;
                }
            } catch (Throwable ignored) {
            }
            return false;
        }
        if (isSuccessfulStatusToken(value)) return true;
        for (String part : value.split("[&;]")) {
            int equals = part.indexOf('=');
            if (equals >= 0 && isSuccessfulStatusToken(part.substring(equals + 1))) return true;
        }
        return false;
    }

    private static boolean isSuccessfulJsonValue(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() == 1;
        return value != null && isSuccessfulStatusToken(String.valueOf(value));
    }

    private static boolean isSuccessfulStatusToken(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return "ok".equals(value) || value.matches("ok_[a-z0-9_.-]+");
    }

    static String httpGet(String url, int connectTimeoutMs, int readTimeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setUseCaches(false);
        try {
            int code = conn.getResponseCode();
            InputStream input = code >= 200 && code <= 299 ? conn.getInputStream() : conn.getErrorStream();
            String body = readAll(input);
            if (code < 200 || code > 299) throw new IllegalStateException("HTTP " + code + ": " + body);
            return body;
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

}
