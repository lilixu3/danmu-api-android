package com.example.danmuapiapp.xposed;

import static com.example.danmuapiapp.xposed.DanmuXposedHttp.httpGet;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.extractEpisodeNumber;

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
    private static final String TVBOX_PLAYBACK_ACTIVITY =
        "com.github.tvbox.osc.ui.activity.DetailActivity";
    private static final String TVBOX_VOD_INFO_CLASS =
        "com.github.tvbox.osc.bean.VodInfo";
    private static final String TVBOX_PLAY_FRAGMENT_CLASS =
        "com.github.tvbox.osc.ui.fragment.PlayFragment";
    private static final String TVBOX_PLAYER_CLASS =
        "com.github.tvbox.osc.player.MyVideoView";
    private static final String FONGMI_PLAYBACK_ACTIVITY =
        "com.fongmi.android.tv.ui.activity.VideoActivity";
    private static final String FONGMI_HISTORY_CLASS =
        "com.fongmi.android.tv.bean.History";

    private volatile int cachedShellMediaPort = -1;
    private volatile int cachedShellMediaMisses = 0;
    private volatile long cachedShellMediaPortUpdatedAtMs = 0L;

    ShellMedia read(Activity activity, int preferredPort) {
        DanmuXposedHostShell.Target target = DanmuXposedHostShell.resolve(activity, preferredPort);
        ShellMedia activityMedia = readActivityMedia(activity, target.port);
        String packageName = activity == null ? "" : activity.getPackageName();
        if (DanmuXposedHostShell.isTvBoxFamilyPackage(packageName)) {
            return activityMedia == null ? null : activityMedia.withPort(target.port);
        }
        boolean fongMi = DanmuXposedHostShell.isFongMiFamilyPackage(packageName);
        if (activityMedia != null && !activityMedia.title.isEmpty() && (!fongMi || activityMedia.state >= 0)) {
            return activityMedia.withPort(target.port);
        }
        ShellMedia endpointMedia = target.authoritative
            ? readFromPort(target.port) : read(target.port);
        return mergeMedia(endpointMedia, activityMedia);
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
            String vodFlag = readString(root, "vodFlag", "vod_flag");
            if (episodeNumber <= 0) episodeNumber = extractEpisodeNumber(episodeText);
            if (episodeNumber <= 0) episodeNumber = extractEpisodeNumber(title);
            if (episodeNumber <= 0) episodeNumber = extractEpisodeNumber(url);
            int state = readPlaybackState(root);
            long position = readLong(root, "position", "currentPosition", "current_position", "pos");
            long duration = readLong(root, "duration", "totalDuration", "total_duration");
            if (!title.isEmpty()) return new ShellMedia(port, title, episodeText, episodeNumber, url, vodFlag, state, position, duration);
            if (!url.isEmpty()) return new ShellMedia(port, title, episodeText, episodeNumber, url, vodFlag, state, position, duration);
        } catch (Throwable ignored) {
        }
        return null;
    }

    ShellMedia readActivityMedia(Activity activity, int preferredPort) {
        if (activity == null || !isSupportedPlaybackActivity(activity.getClass().getName())) {
            return null;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return readActivityMediaOnMainThread(activity, preferredPort);
        }

        AtomicReference<ShellMedia> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        try {
            activity.runOnUiThread(() -> {
                try {
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        result.set(readActivityMediaOnMainThread(activity, preferredPort));
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

    private boolean isSupportedPlaybackActivity(String className) {
        return TVBOX_PLAYBACK_ACTIVITY.equals(className) || FONGMI_PLAYBACK_ACTIVITY.equals(className);
    }

    private ShellMedia readActivityMediaOnMainThread(Activity activity, int preferredPort) {
        if (FONGMI_PLAYBACK_ACTIVITY.equals(activity.getClass().getName())) {
            return readFongMiMediaOnMainThread(activity, preferredPort);
        }
        return readTvBoxMediaOnMainThread(activity, preferredPort);
    }

    private ShellMedia readTvBoxMediaOnMainThread(Activity activity, int preferredPort) {
        ShellMedia modelMedia = readNewBoxVodInfo(activity, preferredPort);
        ShellMedia labelMedia = null;
        View titleView = findViewByName(activity, "tv_info_name1");
        if (titleView instanceof TextView) {
            CharSequence raw = ((TextView) titleView).getText();
            labelMedia = parseNewBoxPlaybackLabel(raw == null ? "" : raw.toString(), preferredPort);
        }

        ShellMedia base = mergeNewBoxMedia(modelMedia, labelMedia);
        if (base == null) return null;
        Object player = findViewByName(activity, "mVideoView");
        if (player == null) {
            Object fragment = findFieldValueByTypeName(activity, TVBOX_PLAY_FRAGMENT_CLASS);
            player = findFieldValueByTypeName(fragment, TVBOX_PLAYER_CLASS);
        }
        if (player == null) player = readFirstFieldValue(activity, "mVideoView", "videoView", "player");
        Boolean playing = invokeBoolean(player, "isPlaying");
        Integer rawState = invokeInteger(player, "getCurrentPlayState");
        long position = sanitizeTime(invokeLong(player, "getCurrentPosition"));
        long duration = sanitizeTime(invokeLong(player, "getDuration"));
        int state = mapTvBoxPlaybackState(rawState, playing, position, duration);
        return new ShellMedia(
            base.port, base.title, base.episodeText, base.episodeNumber, base.url, base.vodFlag,
            state, position, duration);
    }

    private ShellMedia readFongMiMediaOnMainThread(Activity activity, int preferredPort) {
        Object history = findFieldValueByTypeName(activity, FONGMI_HISTORY_CLASS);
        if (history == null) return null;

        String title = invokeString(history, "getVodName");
        String episode = invokeString(history, "getVodRemarks");
        String url = invokeString(history, "getEpisodeUrl");
        String vodFlag = invokeString(history, "getVodFlag");
        if (title.isEmpty()) return null;

        Object playerView = findViewByName(activity, "player");
        if (playerView == null) playerView = findViewByName(activity, "exo");
        Object player = invokeNoArg(playerView, "getPlayer");
        Boolean playing = invokeBoolean(player, "isPlaying");
        Integer rawState = invokeInteger(player, "getPlaybackState");
        long position = sanitizeTime(invokeLong(player, "getCurrentPosition"));
        long duration = sanitizeTime(invokeLong(player, "getDuration"));
        int state = mapMedia3PlaybackState(rawState, playing, position, duration);
        return new ShellMedia(
            validPort(preferredPort), title, episode, extractEpisodeNumber(episode), url, vodFlag,
            state, position, duration);
    }

    static int mapTvBoxPlaybackState(Integer rawState, Boolean playing, long position, long duration) {
        if (Boolean.TRUE.equals(playing)) return 3;
        if (rawState == null) return -1;
        switch (rawState) {
            case 3:
                return 3;
            case 1:
            case 2:
            case 7:
                return 2;
            case 4:
                return 1;
            case 6:
                return 6;
            default:
                return 0;
        }
    }

    static int mapMedia3PlaybackState(Integer rawState, Boolean playing, long position, long duration) {
        if (Boolean.TRUE.equals(playing)) return 3;
        if (rawState == null) return -1;
        if (rawState == 2) return 6;
        if (rawState == 3) return 1;
        return 0;
    }

    static int mapShellEndpointPlaybackState(int rawState) {
        if (rawState == 1) return 6;
        if (rawState == 2) return 1;
        if (rawState == 3) return 3;
        return rawState == 0 ? 0 : -1;
    }

    private ShellMedia readNewBoxVodInfo(Activity activity, int preferredPort) {
        try {
            Object vodInfo = findVodInfo(activity);
            if (vodInfo == null) return null;

            String title = readFirstFieldString(vodInfo, "name", "vodName");
            int year = readFieldInt(vodInfo, "year");
            if (year >= 1900 && year <= 2200 && DanmuXposedTextPolicy.extractTitleMetadataYear(title).isEmpty()) {
                title = title + " (" + year + ")";
            }
            Object series = readCurrentSeries(vodInfo);
            String episode = readFieldString(series, "name");
            if (episode.isEmpty()) episode = readFieldString(vodInfo, "playNote");
            String url = readFirstFieldString(series, "url", "playUrl", "playUrlValue");
            String vodFlag = readFieldString(vodInfo, "playFlag");
            return createNewBoxMedia(title, episode, url, vodFlag, preferredPort);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object readCurrentSeries(Object vodInfo) {
        try {
            Object rawMap = readFieldValue(vodInfo, "seriesMap");
            if (!(rawMap instanceof Map)) return null;
            String playFlag = readFieldString(vodInfo, "playFlag");
            Object rawSeries = ((Map<?, ?>) rawMap).get(playFlag);
            if (!(rawSeries instanceof List)) return null;
            List<?> series = (List<?>) rawSeries;
            int playIndex = readFieldInt(vodInfo, "playIndex");
            if (playIndex < 0 || playIndex >= series.size()) return null;
            return series.get(playIndex);
        } catch (Throwable ignored) {
            return null;
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
            media.port, media.title, media.episodeText, episodeNumber, "", "", -1, -1L, -1L);
    }

    private static ShellMedia createNewBoxMedia(String rawTitle, String episode, int preferredPort) {
        return createNewBoxMedia(rawTitle, episode, "", "", preferredPort);
    }

    private static ShellMedia createNewBoxMedia(
        String rawTitle, String episode, String url, String vodFlag, int preferredPort
    ) {
        String title = rawTitle == null ? "" : rawTitle.trim();
        if (title.isEmpty()) return null;
        String cleanEpisode = episode == null ? "" : episode.trim();
        int episodeNumber = extractEpisodeNumber(cleanEpisode);
        return new ShellMedia(
            validPort(preferredPort), title, cleanEpisode, episodeNumber, url, vodFlag, -1, -1L, -1L);
    }

    private ShellMedia mergeNewBoxMedia(ShellMedia modelMedia, ShellMedia labelMedia) {
        if (modelMedia == null) return labelMedia;
        if (labelMedia == null) return modelMedia;
        String episodeText = modelMedia.episodeText.isEmpty()
            ? labelMedia.episodeText : modelMedia.episodeText;
        int episodeNumber = modelMedia.episodeNumber > 0
            ? modelMedia.episodeNumber : labelMedia.episodeNumber;
        return new ShellMedia(
            modelMedia.port, preferRicherTitle(modelMedia.title, labelMedia.title), episodeText, episodeNumber,
            modelMedia.url, modelMedia.vodFlag, -1, -1L, -1L);
    }

    static ShellMedia mergeMedia(ShellMedia endpointMedia, ShellMedia activityMedia) {
        if (endpointMedia == null) return activityMedia;
        if (activityMedia == null) return endpointMedia;
        return new ShellMedia(
            endpointMedia.port > 0 ? endpointMedia.port : activityMedia.port,
            preferRicherTitle(activityMedia.title, endpointMedia.title),
            prefer(activityMedia.episodeText, endpointMedia.episodeText),
            activityMedia.episodeNumber > 0 ? activityMedia.episodeNumber : endpointMedia.episodeNumber,
            prefer(endpointMedia.url, activityMedia.url),
            prefer(activityMedia.vodFlag, endpointMedia.vodFlag),
            endpointMedia.state >= 0 ? endpointMedia.state : activityMedia.state,
            endpointMedia.position >= 0L ? endpointMedia.position : activityMedia.position,
            endpointMedia.duration >= 0L ? endpointMedia.duration : activityMedia.duration);
    }

    private static String prefer(String primary, String fallback) {
        String value = primary == null ? "" : primary.trim();
        return value.isEmpty() ? (fallback == null ? "" : fallback.trim()) : value;
    }

    private static String preferRicherTitle(String primary, String fallback) {
        String preferred = prefer(primary, fallback);
        String supplement = fallback == null ? "" : fallback.trim();
        if (preferred.isEmpty() || supplement.isEmpty()) return preferred;
        if (!DanmuXposedTextPolicy.titlesMatch(preferred, supplement)) return preferred;
        boolean addsYear = DanmuXposedTextPolicy.extractTitleMetadataYear(preferred).isEmpty()
            && !DanmuXposedTextPolicy.extractTitleMetadataYear(supplement).isEmpty();
        boolean addsSeason = DanmuXposedTextPolicy.extractSeasonNumber(preferred) <= 0
            && DanmuXposedTextPolicy.extractSeasonNumber(supplement) > 0;
        return addsYear || addsSeason ? supplement : preferred;
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
        Field field = findField(target.getClass(), name);
        if (field == null) throw new NoSuchFieldException(name);
        if (!field.isAccessible()) field.setAccessible(true);
        return field.get(target);
    }

    private Object readFirstFieldValue(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try {
                Object value = readFieldValue(target, name);
                if (value != null) return value;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private String readFirstFieldString(Object target, String... names) {
        Object value = readFirstFieldValue(target, names);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Object findVodInfo(Activity activity) {
        Object typed = findFieldValueByTypeName(activity, TVBOX_VOD_INFO_CLASS);
        if (typed != null) return typed;
        return readFirstFieldValue(activity, "mVodInfo", "vodInfo");
    }

    private Object findFieldValueByTypeName(Object target, String typeName) {
        Class<?> type = target == null ? null : target.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!typeName.equals(field.getType().getName())) continue;
                try {
                    if (!field.isAccessible()) field.setAccessible(true);
                    Object value = field.get(target);
                    if (value != null) return value;
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
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
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                if (!method.isAccessible()) method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private Boolean invokeBoolean(Object target, String methodName) {
        Object value = invokeNoArg(target, methodName);
        return value instanceof Boolean ? (Boolean) value : null;
    }

    private Integer invokeInteger(Object target, String methodName) {
        Object value = invokeNoArg(target, methodName);
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    private String invokeString(Object target, String methodName) {
        Object value = invokeNoArg(target, methodName);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private long invokeLong(Object target, String methodName) {
        Object value = invokeNoArg(target, methodName);
        return value instanceof Number ? ((Number) value).longValue() : -1L;
    }

    private static long sanitizeTime(long value) {
        return value >= 0L ? value : -1L;
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
        if (readBoolean(obj, "isPlaying", "playing", "play", "is_playing")) return 3;
        int state = readInt(obj, "state", "playState", "play_state", "status");
        if (state >= 0) return mapShellEndpointPlaybackState(state);
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
