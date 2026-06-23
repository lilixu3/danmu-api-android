package com.example.danmuapiapp.xposed;

import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.formatOffsetSeconds;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.joinNonBlank;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

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

final class SettingsOverlayState {
    final View hostPageContainer;
    final View hostPageRoot;
    final View backgroundAnchor;
    Runnable hostChangeGuard;
    ViewTreeObserver.OnPreDrawListener preDrawGuard;

    SettingsOverlayState(View hostPageContainer, View hostPageRoot, View backgroundAnchor) {
        this.hostPageContainer = hostPageContainer;
        this.hostPageRoot = hostPageRoot;
        this.backgroundAnchor = backgroundAnchor;
    }
}

final class SettingsRowViews {
    final LinearLayout row;
    final TextView value;

    SettingsRowViews(LinearLayout row, TextView value) {
        this.row = row;
        this.value = value;
    }
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
    final int state;
    final long position;
    final long duration;

    ShellMedia(int port, String title, String episodeText, int episodeNumber, String url, int state, long position, long duration) {
        this.port = port;
        this.title = title == null ? "" : title;
        this.episodeText = episodeText == null ? "" : episodeText;
        this.episodeNumber = episodeNumber;
        this.url = url == null ? "" : url;
        this.state = state;
        this.position = position;
        this.duration = duration;
    }

    ShellMedia withPort(int newPort) {
        return new ShellMedia(newPort, title, episodeText, episodeNumber, url, state, position, duration);
    }

    String displayEpisode() {
        if (!episodeText.isEmpty()) return episodeText;
        return episodeNumber > 0 ? "第" + episodeNumber + "集" : "";
    }

    String matchSignature() {
        return title.trim() + "|" + displayEpisode().trim() + "|" + episodeNumber;
    }

    String signature() {
        return matchSignature();
    }

    boolean isReadyForDanmaku() {
        if (state == 3) return true;
        return state < 0 && position > 0L;
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

final class PendingAutoPush {
    final String matchSignature;
    final EpisodeCandidate candidate;
    final int shellPort;
    final long createdAtMs;

    PendingAutoPush(String matchSignature, EpisodeCandidate candidate, int shellPort, long createdAtMs) {
        this.matchSignature = matchSignature == null ? "" : matchSignature;
        this.candidate = candidate;
        this.shellPort = shellPort;
        this.createdAtMs = createdAtMs;
    }
}

final class CandidateHandle {
    final int type;
    final String handle;
    final String label;
    final String source;

    CandidateHandle(int type, String handle, String label) {
        this(type, handle, label, "");
    }

    CandidateHandle(int type, String handle, String label, String source) {
        this.type = type;
        this.handle = handle;
        this.label = label;
        this.source = source == null ? "" : source;
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
    final int episodeCount;
    final String source;
    final String type;

    AnimeRef(String coreBase, String animeId, String bangumiId, String title, int episodeCount, String source, String type) {
        this.coreBase = coreBase == null ? "" : coreBase;
        this.animeId = animeId == null ? "" : animeId;
        this.bangumiId = bangumiId == null ? "" : bangumiId;
        this.title = title == null ? "" : title;
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
    static final int DIALOG_STYLE_CENTER = 0;
    static final int DIALOG_STYLE_BOTTOM_SHEET = 1;

    final boolean injectionEnabled;
    final boolean autoPushEnabled;
    final double offsetSec;
    final int fontSize;
    final int shellPort;
    final boolean darkTheme;
    final int corePort;
    final String coreToken;
    final int dialogStyle;

    InjectionSettings(boolean injectionEnabled, boolean autoPushEnabled, double offsetSec, int fontSize, int shellPort, boolean darkTheme) {
        this(injectionEnabled, autoPushEnabled, offsetSec, fontSize, shellPort, darkTheme, 0, "", DIALOG_STYLE_CENTER);
    }

    InjectionSettings(boolean injectionEnabled, boolean autoPushEnabled, double offsetSec, int fontSize, int shellPort, boolean darkTheme, int corePort, String coreToken) {
        this(injectionEnabled, autoPushEnabled, offsetSec, fontSize, shellPort, darkTheme, corePort, coreToken, DIALOG_STYLE_CENTER);
    }

    InjectionSettings(boolean injectionEnabled, boolean autoPushEnabled, double offsetSec, int fontSize, int shellPort, boolean darkTheme, int corePort, String coreToken, int dialogStyle) {
        this.injectionEnabled = injectionEnabled;
        this.autoPushEnabled = autoPushEnabled;
        this.offsetSec = Math.abs(offsetSec) < 1e-6 ? 0.0d : offsetSec;
        this.fontSize = fontSize > 0 ? fontSize : -1;
        this.shellPort = shellPort > 0 && shellPort <= 65535 ? shellPort : 9978;
        this.darkTheme = darkTheme;
        this.corePort = corePort > 0 && corePort <= 65535 ? corePort : 0;
        this.coreToken = coreToken == null ? "" : coreToken.trim();
        this.dialogStyle = dialogStyle == DIALOG_STYLE_BOTTOM_SHEET ? DIALOG_STYLE_BOTTOM_SHEET : DIALOG_STYLE_CENTER;
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
