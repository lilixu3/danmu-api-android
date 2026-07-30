package com.example.danmuapiapp.xposed;

import static com.example.danmuapiapp.xposed.DanmuXposedHttp.httpGet;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.extractEpisodeNumber;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.normalizeSearchTitle;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class DanmuXposedShellMediaReader {
    private static final long AUTO_MEDIA_PORT_CACHE_TTL_MS = 30_000L;
    private static final long ACTIVITY_MEDIA_READ_TIMEOUT_MS = 800L;
    private static final int DEFAULT_SHELL_PORT = 9978;
    private static final String NEWBOX_PLAYBACK_ACTIVITY =
        "com.github.tvbox.osc.ui.activity.DetailActivity";
    private static final String NEWBOX_VOD_INFO_CLASS =
        "com.github.tvbox.osc.bean.VodInfo";

    private volatile int cachedShellMediaPort = -1;
    private volatile int cachedShellMediaMisses = 0;
    private volatile long cachedShellMediaPortUpdatedAtMs = 0L;

    ShellMedia read(Activity activity, int preferredPort) {
        ShellMedia activityMedia = readFromActivity(activity, preferredPort);
        if (activityMedia != null && !activityMedia.title.isEmpty()) return activityMedia;
        return read(preferredPort);
    }

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

    private ShellMedia readFromActivity(Activity activity, int preferredPort) {
        if (activity == null || !NEWBOX_PLAYBACK_ACTIVITY.equals(activity.getClass().getName())) {
            return null;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return readNewBoxMediaOnMainThread(activity, preferredPort);
        }

        AtomicReference<ShellMedia> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        try {
            activity.runOnUiThread(() -> {
                try {
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        result.set(readNewBoxMediaOnMainThread(activity, preferredPort));
                    }
                } finally {
                    latch.countDown();
                }
            });
            latch.await(ACTIVITY_MEDIA_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) {
        }
        return result.get();
    }

    private ShellMedia readNewBoxMediaOnMainThread(Activity activity, int preferredPort) {
        ShellMedia modelMedia = readNewBoxVodInfo(activity, preferredPort);
        ShellMedia labelMedia = null;
        View titleView = findViewByName(activity, "tv_info_name1");
        if (titleView instanceof TextView) {
            CharSequence raw = ((TextView) titleView).getText();
            labelMedia = parseNewBoxPlaybackLabel(raw == null ? "" : raw.toString(), preferredPort);
        }

        ShellMedia base = mergeNewBoxMedia(modelMedia, labelMedia);
        if (base == null) return null;
        View player = findViewByName(activity, "mVideoView");
        Boolean playing = invokeBoolean(player, "isPlaying");
        long position = invokeLong(player, "getCurrentPosition");
        long duration = invokeLong(player, "getDuration");
        int state = Boolean.TRUE.equals(playing) ? 3
            : Boolean.FALSE.equals(playing) && duration > 0L ? 1 : -1;
        return new ShellMedia(
            base.port, base.title, base.episodeText, base.episodeNumber, base.url,
            state, position, duration);
    }

    private ShellMedia readNewBoxVodInfo(Activity activity, int preferredPort) {
        try {
            Object vodInfo = null;
            for (Field field : activity.getClass().getFields()) {
                if (!NEWBOX_VOD_INFO_CLASS.equals(field.getType().getName())) continue;
                Object candidate = field.get(activity);
                if (candidate != null) {
                    vodInfo = candidate;
                    break;
                }
            }
            if (vodInfo == null) return null;

            String title = readFieldString(vodInfo, "name");
            String episode = readCurrentSeriesName(vodInfo);
            if (episode.isEmpty()) episode = readFieldString(vodInfo, "playNote");
            return createNewBoxMedia(title, episode, preferredPort);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String readCurrentSeriesName(Object vodInfo) {
        try {
            Object rawMap = readFieldValue(vodInfo, "seriesMap");
            if (!(rawMap instanceof Map)) return "";
            String playFlag = readFieldString(vodInfo, "playFlag");
            Object rawSeries = ((Map<?, ?>) rawMap).get(playFlag);
            if (!(rawSeries instanceof List)) return "";
            List<?> series = (List<?>) rawSeries;
            int playIndex = readFieldInt(vodInfo, "playIndex");
            if (playIndex < 0 || playIndex >= series.size()) return "";
            return readFieldString(series.get(playIndex), "name");
        } catch (Throwable ignored) {
            return "";
        }
    }

    static ShellMedia parseNewBoxPlaybackLabel(String raw, int preferredPort) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return null;
        int separator = value.lastIndexOf(" · ");
        String title = separator > 0 ? value.substring(0, separator).trim() : value;
        String episode = separator > 0 && separator + 3 < value.length()
            ? value.substring(separator + 3).trim() : "";
        ShellMedia media = createNewBoxMedia(title, episode, preferredPort);
        if (media == null) return null;
        int episodeNumber = media.episodeNumber > 0
            ? media.episodeNumber : extractEpisodeNumber(value);
        return new ShellMedia(
            media.port, media.title, media.episodeText, episodeNumber, "", -1, -1L, -1L);
    }

    private static ShellMedia createNewBoxMedia(String rawTitle, String episode, int preferredPort) {
        String title = normalizeSearchTitle(rawTitle);
        if (title.isEmpty()) return null;
        String cleanEpisode = episode == null ? "" : episode.trim();
        int episodeNumber = extractEpisodeNumber(cleanEpisode);
        return new ShellMedia(
            validPort(preferredPort), title, cleanEpisode, episodeNumber, "", -1, -1L, -1L);
    }

    private ShellMedia mergeNewBoxMedia(ShellMedia modelMedia, ShellMedia labelMedia) {
        if (modelMedia == null) return labelMedia;
        if (labelMedia == null) return modelMedia;
        String episodeText = modelMedia.episodeText.isEmpty()
            ? labelMedia.episodeText : modelMedia.episodeText;
        int episodeNumber = modelMedia.episodeNumber > 0
            ? modelMedia.episodeNumber : labelMedia.episodeNumber;
        return new ShellMedia(
            modelMedia.port, modelMedia.title, episodeText, episodeNumber, "", -1, -1L, -1L);
    }

    private View findViewByName(Activity activity, String name) {
        try {
            int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
            return id == 0 ? null : activity.findViewById(id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object readFieldValue(Object target, String name) throws Exception {
        if (target == null) return null;
        Field field = target.getClass().getField(name);
        return field.get(target);
    }

    private String readFieldString(Object target, String name) {
        try {
            Object value = readFieldValue(target, name);
            return value == null ? "" : String.valueOf(value).trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private int readFieldInt(Object target, String name) {
        try {
            Object value = readFieldValue(target, name);
            if (value instanceof Number) return ((Number) value).intValue();
            return Integer.parseInt(String.valueOf(value));
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private Object invokeNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Boolean invokeBoolean(Object target, String methodName) {
        Object value = invokeNoArg(target, methodName);
        return value instanceof Boolean ? (Boolean) value : null;
    }

    private long invokeLong(Object target, String methodName) {
        Object value = invokeNoArg(target, methodName);
        return value instanceof Number ? ((Number) value).longValue() : -1L;
    }

    private static int validPort(int port) {
        return port > 0 && port <= 65535 ? port : DEFAULT_SHELL_PORT;
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
