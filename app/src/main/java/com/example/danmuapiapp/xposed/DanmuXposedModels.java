package com.example.danmuapiapp.xposed;

import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.formatOffsetSeconds;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.joinNonBlank;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

interface StringValueCallback {
    void onValue(String value);
}

interface IntValueCallback {
    void onValue(int value);
}

interface FilterSelectListener {
    void onSelect(String source);
}

/** Push progress sink, so push logic never holds a reference to a dialog's views. */
interface PushFeedback {
    void onStatus(String status);

    void onPushInfo(String info);
}

final class Anchor {
    final View view;
    final ViewGroup parent;
    final Rect rect;
    final String text;
    final int priority;

    Anchor(View view, ViewGroup parent, Rect rect, String text, int priority) {
        this.view = view;
        this.parent = parent;
        this.rect = rect == null ? new Rect() : rect;
        this.text = text == null ? "" : text;
        this.priority = priority;
    }
}

final class ShellMedia {
    final int port;
    final String title;
    final String episodeText;
    final int episodeNumber;
    final String url;
    final String vodFlag;
    final int state;
    final long position;
    final long duration;

    ShellMedia(int port, String title, String episodeText, int episodeNumber, String url, int state, long position, long duration) {
        this(port, title, episodeText, episodeNumber, url, "", state, position, duration);
    }

    ShellMedia(int port, String title, String episodeText, int episodeNumber, String url, String vodFlag,
               int state, long position, long duration) {
        this.port = port;
        this.title = title == null ? "" : title;
        this.episodeText = episodeText == null ? "" : episodeText;
        this.episodeNumber = episodeNumber;
        this.url = url == null ? "" : url;
        this.vodFlag = vodFlag == null ? "" : vodFlag;
        this.state = state;
        this.position = position;
        this.duration = duration;
    }

    ShellMedia withPort(int newPort) {
        return new ShellMedia(newPort, title, episodeText, episodeNumber, url, vodFlag, state, position, duration);
    }

    String displayEpisode() {
        if (!episodeText.isEmpty()) return episodeText;
        return episodeNumber > 0 ? "第" + episodeNumber + "集" : "";
    }

    String matchSignature() {
        return MediaIdentity.from(this).key();
    }

    String signature() {
        return playbackIdentity();
    }

    String playbackIdentity() {
        return matchSignature() + "|" + url.trim() + "|" + vodFlag.trim();
    }

    String playbackStateBucket() {
        if (state == 3) return "playing";
        if (state == 2) return "preparing";
        if (state == 1) return "paused";
        if (state == 6) return "buffering";
        if (state == 0) return "stopped";
        return state < 0 ? "unknown" : "state:" + state;
    }

    String playbackStateLabel() {
        String label;
        if (state == 3) label = "播放中";
        else if (state == 6) label = "缓冲中";
        else if (state == 1) label = "暂停";
        else if (state == 0) label = "未播放";
        else if (state == 2) label = "准备中";
        else label = state >= 0 ? "状态" + state : "等待播放";
        if (position >= 0L && duration > 0L) return label + " " + (position / 1000L) + "/" + (duration / 1000L) + "s";
        if (position >= 0L) return label + " " + (position / 1000L) + "s";
        return label;
    }
}

final class MediaIdentity {
    final String rawTitle;
    final String baseTitle;
    final String year;
    final int season;
    final int episodeNumber;
    final String varietyEpisode;
    final String episodeIdentity;

    private MediaIdentity(String rawTitle, String baseTitle, String year, int season,
                          int episodeNumber, String varietyEpisode, String episodeIdentity) {
        this.rawTitle = rawTitle == null ? "" : rawTitle.trim();
        this.baseTitle = baseTitle == null ? "" : baseTitle.trim();
        this.year = year == null ? "" : year.trim();
        this.season = season;
        this.episodeNumber = episodeNumber;
        this.varietyEpisode = varietyEpisode == null ? "" : varietyEpisode.trim();
        this.episodeIdentity = episodeIdentity == null ? "" : episodeIdentity.trim();
    }

    static MediaIdentity from(ShellMedia media) {
        if (media == null) return from("", "", -1);
        int episode = media.episodeNumber > 0
            ? media.episodeNumber : DanmuXposedTextPolicy.extractEpisodeNumber(media.displayEpisode());
        return from(media.title, media.displayEpisode(), episode);
    }

    static MediaIdentity from(String title, String episodeText, int episodeNumber) {
        String rawTitle = title == null ? "" : title.trim();
        String rawEpisode = episodeText == null ? "" : episodeText.trim();
        String year = DanmuXposedTextPolicy.extractTitleMetadataYear(rawTitle);
        int season = DanmuXposedTextPolicy.extractSeasonNumber(rawTitle);
        if (season <= 0) season = DanmuXposedTextPolicy.extractSeasonNumber(rawEpisode);
        String variety = DanmuXposedTextPolicy.normalizeVarietyEpisode(rawEpisode);
        String episodeIdentity = !variety.isEmpty()
            ? variety
            : episodeNumber > 0
                ? "episode:" + episodeNumber
                : DanmuXposedTextPolicy.normalizeEpisodeIdentity(rawEpisode);
        return new MediaIdentity(
            rawTitle,
            DanmuXposedTextPolicy.normalizeTitleForMatch(rawTitle),
            year,
            season,
            episodeNumber,
            variety,
            episodeIdentity);
    }

    int comparableSeason() {
        return season > 0 ? season : 1;
    }

    String key() {
        return baseTitle + "|year:" + year + "|season:" + comparableSeason() + "|" + episodeIdentity;
    }
}

final class PlaybackReadinessGate {
    private String playbackIdentity = "";
    private long lastPosition = -1L;

    synchronized boolean isReady(ShellMedia media) {
        if (media == null) {
            reset();
            return false;
        }
        String identity = media.playbackIdentity();
        if (!identity.equals(playbackIdentity)) {
            playbackIdentity = identity;
            lastPosition = -1L;
        }
        if (media.state == 3) {
            lastPosition = media.position;
            return true;
        }
        if (media.state >= 0) {
            lastPosition = media.position;
            return false;
        }
        boolean advancingWithoutState = lastPosition >= 0L && media.position > lastPosition;
        lastPosition = media.position;
        return advancingWithoutState;
    }

    synchronized void reset() {
        playbackIdentity = "";
        lastPosition = -1L;
    }

}

final class PendingAutoPush {
    final String matchSignature;
    final long generation;
    final EpisodeCandidate candidate;
    final int shellPort;
    final long selectionObservedAtMs;
    final long createdAtMs;

    PendingAutoPush(String matchSignature, long generation, EpisodeCandidate candidate, int shellPort,
                    long selectionObservedAtMs, long createdAtMs) {
        this.matchSignature = matchSignature == null ? "" : matchSignature;
        this.generation = generation;
        this.candidate = candidate;
        this.shellPort = shellPort;
        this.selectionObservedAtMs = selectionObservedAtMs;
        this.createdAtMs = createdAtMs;
    }

    boolean isUsable(String expectedSignature, long expectedGeneration, long nowMs, long ttlMs) {
        String signature = expectedSignature == null ? "" : expectedSignature;
        long ageMs = nowMs - createdAtMs;
        return candidate != null && signature.equals(matchSignature) && generation == expectedGeneration &&
            ageMs >= 0L && ageMs <= ttlMs;
    }
}

final class CandidateHandle {
    final int type;
    final String handle;
    final String label;
    final String source;
    final AnimeRef anime;
    final EpisodeCandidate episodeCandidate;

    CandidateHandle(int type, String handle, String label) {
        this(type, handle, label, "");
    }

    CandidateHandle(int type, String handle, String label, String source) {
        this(type, handle, label, source, null, null);
    }

    CandidateHandle(int type, String handle, String label, String source,
                    AnimeRef anime, EpisodeCandidate episodeCandidate) {
        this.type = type;
        this.handle = handle;
        this.label = label;
        this.source = source == null ? "" : source;
        this.anime = anime;
        this.episodeCandidate = episodeCandidate;
    }
}

final class SourceFilter {
    final String source;
    final String label;
    int count;

    SourceFilter(String source, String label, int count) {
        this.source = source == null ? "" : source;
        this.label = label == null ? "" : label;
        this.count = count;
    }

    String displayName() {
        return label.isEmpty() ? source : label;
    }
}

final class DirectSearch {
    final String coreBase;
    final List<AnimeRef> animes;

    DirectSearch(String coreBase, List<AnimeRef> animes) {
        this.coreBase = coreBase == null ? "" : coreBase;
        this.animes = animes == null ? new ArrayList<>() : animes;
    }
}

final class AnimeRef {
    final String coreBase;
    final String animeId;
    final String bangumiId;
    final String title;
    final String year;
    final int episodeCount;
    final String source;
    final String type;

    AnimeRef(String coreBase, String animeId, String bangumiId, String title, String year,
             int episodeCount, String source, String type) {
        this.coreBase = coreBase == null ? "" : coreBase;
        this.animeId = animeId == null ? "" : animeId;
        this.bangumiId = bangumiId == null ? "" : bangumiId;
        this.title = title == null ? "" : title;
        this.year = year == null ? "" : year;
        this.episodeCount = episodeCount;
        this.source = source == null ? "" : source;
        this.type = type == null ? "" : type;
    }
}

final class EpisodeCandidate {
    final String name;
    final String episode;
    final int episodeNumber;
    final String source;
    final String url;

    EpisodeCandidate(String name, String episode, int episodeNumber, String source, String url) {
        this.name = name == null ? "" : name;
        this.episode = episode == null ? "" : episode;
        this.episodeNumber = episodeNumber;
        this.source = source == null ? "" : source;
        this.url = url == null ? "" : url;
    }

    String displayLabel() {
        return joinNonBlank(name, episode, source);
    }
}

final class PreparedDanmaku {
    final String url;
    final int count;
    final long size;
    final long expiresAtMs;

    PreparedDanmaku(String url, int count, long size, long expiresAtMs) {
        this.url = url == null ? "" : url;
        this.count = count;
        this.size = Math.max(0L, size);
        this.expiresAtMs = Math.max(0L, expiresAtMs);
    }
}

final class PushGuard {
    final boolean allowed;
    final String globalKey;
    final String sessionKey;
    final String reason;

    PushGuard(boolean allowed, String globalKey, String sessionKey, String reason) {
        this.allowed = allowed;
        this.globalKey = globalKey == null ? "" : globalKey;
        this.sessionKey = sessionKey == null ? "" : sessionKey;
        this.reason = reason == null ? "" : reason;
    }
}

final class InjectionSettings {
    final boolean injectionEnabled;
    final boolean autoPushEnabled;
    final double offsetSec;
    final int fontSize;
    final int shellPort;
    final DanmuThemeMode themeMode;
    final int corePort;
    final String coreToken;

    InjectionSettings(boolean injectionEnabled, boolean autoPushEnabled, double offsetSec, int fontSize,
                      int shellPort, DanmuThemeMode themeMode) {
        this(injectionEnabled, autoPushEnabled, offsetSec, fontSize, shellPort, themeMode, 0, "");
    }

    InjectionSettings(boolean injectionEnabled, boolean autoPushEnabled, double offsetSec, int fontSize,
                      int shellPort, DanmuThemeMode themeMode, int corePort, String coreToken) {
        this.injectionEnabled = injectionEnabled;
        this.autoPushEnabled = autoPushEnabled;
        this.offsetSec = Math.abs(offsetSec) < 1e-6 ? 0.0d : offsetSec;
        this.fontSize = fontSize > 0 ? fontSize : -1;
        this.shellPort = shellPort > 0 && shellPort <= 65535 ? shellPort : 9978;
        this.themeMode = themeMode == null ? DanmuThemeMode.FOLLOW_HOST : themeMode;
        this.corePort = corePort > 0 && corePort <= 65535 ? corePort : 0;
        this.coreToken = coreToken == null ? "" : coreToken.trim();
    }

    String pushParamHint() {
        StringBuilder sb = new StringBuilder();
        if (Math.abs(offsetSec) > 1e-6) sb.append("偏移").append(formatOffsetSeconds(offsetSec)).append("s");
        if (fontSize > 0) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("大小").append(fontSize);
        }
        return sb.toString();
    }
}

final class BridgeRow {
    final String status;
    final String message;
    final String payload;

    BridgeRow(String status, String message, String payload) {
        this.status = status == null ? "error" : status;
        this.message = message == null ? "" : message;
        this.payload = payload == null ? "" : payload;
    }
}

final class BridgeResult {
    boolean ok;
    String message = "";
    int selectedIndex = 0;
    final List<CandidateHandle> candidates = new ArrayList<>();
    final List<SourceFilter> filters = new ArrayList<>();
}
