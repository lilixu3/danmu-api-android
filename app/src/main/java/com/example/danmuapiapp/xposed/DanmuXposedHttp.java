package com.example.danmuapiapp.xposed;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

final class DanmuXposedHttp {
    private DanmuXposedHttp() {
    }

    static int countDanmaku(String danmakuUrl) {
        try {
            String body = httpGet(danmakuUrl, 1800, 12000);
            String trimmed = body == null ? "" : body.trim();
            if (trimmed.isEmpty()) return -1;
            if (trimmed.startsWith("{")) {
                JSONObject root = new JSONObject(trimmed);
                JSONArray array = root.optJSONArray("comments");
                if (array == null) array = root.optJSONArray("danmus");
                if (array == null) array = root.optJSONArray("data");
                if (array != null) return array.length();
                JSONObject data = root.optJSONObject("data");
                if (data != null) {
                    array = data.optJSONArray("comments");
                    if (array == null) array = data.optJSONArray("danmus");
                    if (array != null) return array.length();
                }
            }
            if (trimmed.startsWith("[")) return new JSONArray(trimmed).length();
            int xmlCount = countOccurrences(trimmed, "<d ") + countOccurrences(trimmed, "<d>");
            return xmlCount > 0 ? xmlCount : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    static String buildShellPushUrl(int shellPort, String danmakuUrl) throws Exception {
        int port = shellPort > 0 && shellPort <= 65535 ? shellPort : 9978;
        return "http://127.0.0.1:" + port + "/action?do=refresh&type=danmaku&path=" + urlEncode(danmakuUrl);
    }

    static String urlEncode(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8").replace("+", "%20");
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

    private static int countOccurrences(String text, String needle) {
        if (text == null || needle == null || needle.isEmpty()) return 0;
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
