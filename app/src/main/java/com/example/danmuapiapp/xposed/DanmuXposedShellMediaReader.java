package com.example.danmuapiapp.xposed;

import static com.example.danmuapiapp.xposed.DanmuXposedHttp.httpGet;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.extractEpisodeNumber;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.normalizeSearchTitle;

final class DanmuXposedShellMediaReader {
    private static final long AUTO_MEDIA_PORT_CACHE_TTL_MS = 30_000L;

    private volatile int cachedShellMediaPort = -1;
    private volatile int cachedShellMediaMisses = 0;
    private volatile long cachedShellMediaPortUpdatedAtMs = 0L;

    ShellMedia read(int preferredPort) {
        java.util.ArrayList<Integer> ports = new java.util.ArrayList<>();
        int preferred = preferredPort > 0 && preferredPort <= 65535 ? preferredPort : -1;
        if (preferred > 0) ports.add(preferred);
        int cachedPort = cachedShellMediaPort;
        long now = System.currentTimeMillis();
        if (cachedPort >= 9978 && cachedPort <= 9998 && now - cachedShellMediaPortUpdatedAtMs <= AUTO_MEDIA_PORT_CACHE_TTL_MS && cachedPort != preferred) {
            ports.add(cachedPort);
        }
        for (int port = 9978; port <= 9998; port++) {
            if (!ports.contains(port)) ports.add(port);
        }
        ShellMedia urlOnly = null;
        for (int port : ports) {
            ShellMedia media = readFromPort(port);
            if (media == null) continue;
            if (!media.title.isEmpty()) {
                cachedShellMediaPort = port;
                cachedShellMediaMisses = 0;
                cachedShellMediaPortUpdatedAtMs = now;
                return media;
            }
            if (urlOnly == null) urlOnly = media;
        }
        if (cachedPort > 0) cachedShellMediaMisses++;
        if (cachedShellMediaMisses > 1) resetCache();
        return urlOnly;
    }

    void resetCache() {
        cachedShellMediaPort = -1;
        cachedShellMediaPortUpdatedAtMs = 0L;
        cachedShellMediaMisses = 0;
    }

    private ShellMedia readFromPort(int port) {
        try {
            String body = httpGet("http://127.0.0.1:" + port + "/media", 700, 1500);
            org.json.JSONObject root = new org.json.JSONObject(body);
            String title = readString(root, "title", "name", "vodName", "vod_name");
            String episodeText = readString(root, "artist", "episodeTitle", "episodeName", "remarks", "remark", "subtitle");
            int episodeNumber = readInt(root, "episode", "episodeNumber", "number", "sort", "index");
            String url = readString(root, "url", "path", "playUrl", "play_url");
            if (episodeNumber <= 0) episodeNumber = extractEpisodeNumber(episodeText);
            if (episodeNumber <= 0) episodeNumber = extractEpisodeNumber(title);
            if (episodeNumber <= 0) episodeNumber = extractEpisodeNumber(url);
            String cleanedTitle = normalizeSearchTitle(title);
            if (!cleanedTitle.isEmpty()) title = cleanedTitle;
            int state = readPlaybackState(root);
            long position = readLong(root, "position", "currentPosition", "current_position", "pos");
            long duration = readLong(root, "duration", "totalDuration", "total_duration");
            if (!title.isEmpty()) return new ShellMedia(port, title, episodeText, episodeNumber, url, state, position, duration);
            if (!url.isEmpty()) return new ShellMedia(port, title, episodeText, episodeNumber, url, state, position, duration);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String readString(org.json.JSONObject obj, String... keys) {
        for (String key : keys) {
            if (!obj.has(key)) continue;
            String value = obj.optString(key, "").trim();
            if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) return value;
        }
        return "";
    }

    private int readInt(org.json.JSONObject obj, String... keys) {
        for (String key : keys) {
            if (!obj.has(key)) continue;
            Object raw = obj.opt(key);
            if (raw instanceof Number) return ((Number) raw).intValue();
            if (raw instanceof String) {
                try {
                    return Integer.parseInt(((String) raw).trim());
                } catch (Throwable ignored) {
                }
            }
        }
        return -1;
    }

    private int readPlaybackState(org.json.JSONObject obj) {
        int state = readInt(obj, "state", "playState", "play_state", "status");
        if (state >= 0) return state;
        if (readBoolean(obj, "isPlaying", "playing", "play", "is_playing")) return 3;
        String label = readString(obj, "state", "playState", "play_state", "status").toLowerCase(java.util.Locale.ROOT);
        if (label.contains("playing") || label.contains("play") || label.contains("播放中")) return 3;
        if (label.contains("buffer") || label.contains("缓冲")) return 6;
        if (label.contains("pause") || label.contains("paused") || label.contains("暂停")) return 1;
        if (label.contains("ready") || label.contains("prepare") || label.contains("准备")) return 2;
        if (label.contains("stop") || label.contains("idle") || label.contains("未播放")) return 0;
        return state;
    }

    private boolean readBoolean(org.json.JSONObject obj, String... keys) {
        for (String key : keys) {
            if (!obj.has(key)) continue;
            Object raw = obj.opt(key);
            if (raw instanceof Boolean) return (Boolean) raw;
            if (raw instanceof Number) return ((Number) raw).intValue() != 0;
            if (raw instanceof String) {
                String value = ((String) raw).trim();
                if ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value)) return true;
            }
        }
        return false;
    }

    private long readLong(org.json.JSONObject obj, String... keys) {
        for (String key : keys) {
            if (!obj.has(key)) continue;
            Object raw = obj.opt(key);
            if (raw instanceof Number) return ((Number) raw).longValue();
            if (raw instanceof String) {
                try {
                    return Long.parseLong(((String) raw).trim());
                } catch (Throwable ignored) {
                }
            }
        }
        return -1L;
    }
}
