package com.example.danmuapiapp.xposed;

import static com.example.danmuapiapp.xposed.DanmuXposedHttp.httpGet;
import static com.example.danmuapiapp.xposed.DanmuXposedHttp.urlEncode;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.extractEpisodeNumber;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.extractSourceFromTitle;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.joinNonBlank;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.normalizeDisplayTitle;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.normalizeSearchTitle;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.normalizeSourceKey;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class DanmuXposedBridgeClient {
    private final SettingsReader settingsReader;
    private volatile String cachedCoreBase = "";

    DanmuXposedBridgeClient(SettingsReader settingsReader) {
        this.settingsReader = settingsReader;
    }

    interface SettingsReader {
        InjectionSettings read(Context context, int fallbackPort);
    }

    DirectSearch searchAnimeDirect(Context context, String keyword) throws Exception {
        Exception last = null;
        List<String> keywords = buildSearchKeywords(keyword);
        ArrayList<AnimeRef> lastEmpty = null;
        for (String coreBase : coreBaseCandidates(context)) {
            for (String searchKey : keywords) {
                try {
                    String url = coreBase + "/api/v2/search/anime?keyword=" + urlEncode(searchKey);
                    List<AnimeRef> animes = parseAnimeSearch(coreBase, httpGet(url, 1800, 20000));
                    if (!animes.isEmpty()) {
                        cachedCoreBase = coreBase;
                        return new DirectSearch(coreBase, animes);
                    }
                    lastEmpty = new ArrayList<>(animes);
                } catch (Exception e) {
                    last = e;
                }
            }
        }
        if (lastEmpty != null) return new DirectSearch("", lastEmpty);
        throw last == null ? new IllegalStateException("App 端未同步弹幕 API 核心端口，请打开影视壳注入设置页重新检查") : last;
    }

    List<EpisodeCandidate> loadEpisodesForAnime(AnimeRef anime, int targetEpisodeNumber) throws Exception {
        String id = anime.animeId.isEmpty() ? anime.bangumiId : anime.animeId;
        if (id.isEmpty()) return new ArrayList<>();
        String raw = httpGet(anime.coreBase + "/api/v2/bangumi/" + urlEncode(id), 1800, 20000);
        return parseBangumiCandidates(anime.coreBase, raw, anime, targetEpisodeNumber);
    }

    private List<String> buildSearchKeywords(String raw) {
        ArrayList<String> out = new ArrayList<>();
        String cleaned = normalizeSearchTitle(raw);
        addKeyword(out, cleaned);
        String display = normalizeDisplayTitle(raw);
        addKeyword(out, display);
        String original = raw == null ? "" : raw.trim();
        if (original.length() <= 32) addKeyword(out, original);
        if (out.isEmpty()) addKeyword(out, original);
        return out;
    }

    private void addKeyword(ArrayList<String> out, String value) {
        if (value == null) return;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return;
        if (!out.contains(trimmed)) out.add(trimmed);
    }

    private List<String> coreBaseCandidates(Context context) throws Exception {
        ArrayList<String> bases = new ArrayList<>();
        InjectionSettings settings = settingsReader == null ? null : settingsReader.read(context, 9978);
        if (settings == null || settings.corePort <= 0 || settings.corePort > 65535) {
            throw new IllegalStateException("App 端未同步弹幕 API 核心端口，请打开影视壳注入设置页重新检查");
        }
        String base = "http://127.0.0.1:" + settings.corePort;
        String configured = settings.coreToken.isEmpty() ? base : base + "/" + urlEncode(settings.coreToken);
        if (cachedCoreBase != null && !cachedCoreBase.trim().isEmpty() && cachedCoreBase.startsWith(base)) {
            addUnique(bases, cachedCoreBase.trim());
        }
        addUnique(bases, configured);
        return bases;
    }

    private void addUnique(ArrayList<String> values, String value) {
        if (!values.contains(value)) values.add(value);
    }

    private List<AnimeRef> parseAnimeSearch(String coreBase, String raw) throws Exception {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) return new ArrayList<>();
        JSONArray array;
        if (trimmed.startsWith("[")) {
            array = new JSONArray(trimmed);
        } else {
            JSONObject root = new JSONObject(trimmed);
            array = root.optJSONArray("animes");
            if (array == null) array = root.optJSONArray("data");
            if (array == null) array = root.optJSONArray("items");
            if (array == null) array = root.optJSONArray("list");
            if (array == null) array = root.optJSONArray("results");
            if (array == null) array = new JSONArray();
        }
        ArrayList<AnimeRef> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String animeId = readString(item, "animeId", "id");
            String bangumiId = readString(item, "bangumiId", "bangumi_id");
            if (animeId.isEmpty() && bangumiId.isEmpty()) continue;
            String name = readString(item, "animeTitle", "title", "name", "anime", "vod_name");
            if (name.isEmpty()) name = "候选 " + (result.size() + 1);
            String sourceRaw = extractSourceFromTitle(name);
            if (sourceRaw.isEmpty()) sourceRaw = readString(item, "source", "sourceName", "provider", "api", "from");
            String source = normalizeSourceKey(sourceRaw);
            String type = readString(item, "typeDescription", "type");
            int episodeCount = Math.max(0, readInt(item, "episodeCount", "totalEpisodes", "count"));
            result.add(new AnimeRef(coreBase, animeId, bangumiId, name, episodeCount, source, type));
        }
        return result;
    }

    private List<EpisodeCandidate> parseBangumiCandidates(String coreBase, String raw, AnimeRef anime, int targetEpisodeNumber) throws Exception {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) return new ArrayList<>();
        JSONObject root = new JSONObject(trimmed);
        if (root.has("success") && !root.optBoolean("success", false)) return new ArrayList<>();
        JSONObject bangumi = root.optJSONObject("bangumi");
        if (bangumi == null) bangumi = root;
        String animeTitle = readString(bangumi, "animeTitle", "title", "name", "anime", "vod_name");
        if (animeTitle.isEmpty()) animeTitle = anime.title;
        String source = anime.source.isEmpty() ? anime.type : anime.source;
        JSONArray episodes = bangumi.optJSONArray("episodes");
        if (episodes == null) episodes = bangumi.optJSONArray("episodeList");
        if (episodes == null) episodes = bangumi.optJSONArray("items");
        if (episodes == null) episodes = bangumi.optJSONArray("list");
        if (episodes == null) return new ArrayList<>();
        ArrayList<EpisodeCandidate> result = new ArrayList<>();
        for (int i = 0; i < episodes.length(); i++) {
            JSONObject ep = episodes.optJSONObject(i);
            if (ep == null) continue;
            String directUrl = readString(ep, "url", "path", "danmaku", "danmu");
            String episodeId = readString(ep, "episodeId", "id", "commentId", "episode_id");
            String url;
            if (directUrl.startsWith("http://") || directUrl.startsWith("https://")) {
                url = directUrl;
            } else if (!episodeId.isEmpty()) {
                url = coreBase + "/api/v2/comment/" + urlEncode(episodeId) + "?format=xml";
            } else {
                continue;
            }
            String episodeNumber = readString(ep, "episodeNumber", "episodeNo", "episodeIndex", "number", "sort");
            String episodeTitle = readString(ep, "episodeTitle", "title", "name", "episode", "episodeName", "remark", "remarks");
            int parsedEpisodeNumber = extractEpisodeNumber(episodeNumber);
            if (parsedEpisodeNumber <= 0) parsedEpisodeNumber = extractEpisodeNumber(episodeTitle);
            String episodeLabel = buildEpisodeLabel(episodeNumber, episodeTitle, targetEpisodeNumber);
            result.add(new EpisodeCandidate(animeTitle, episodeLabel, parsedEpisodeNumber, source, url));
        }
        return result;
    }

    private String buildEpisodeLabel(String episodeNumber, String episodeTitle, int targetEpisodeNumber) {
        ArrayList<String> parts = new ArrayList<>();
        int number = extractEpisodeNumber(episodeNumber);
        if (number > 0) parts.add("第" + number + "集");
        if (episodeTitle != null && !episodeTitle.trim().isEmpty() && (number <= 0 || !episodeTitle.trim().equals(String.valueOf(number)))) {
            parts.add(episodeTitle.trim());
        }
        if (parts.isEmpty() && targetEpisodeNumber > 0) parts.add("第" + targetEpisodeNumber + "集");
        return joinNonBlank(parts.toArray(new String[0]));
    }

    private String readString(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (!obj.has(key)) continue;
            String value = obj.optString(key, "").trim();
            if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) return value;
        }
        return "";
    }

    private int readInt(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (!obj.has(key)) continue;
            Object raw = obj.opt(key);
            if (raw instanceof Number) return ((Number) raw).intValue();
            if (raw instanceof String) {
                try {
                    return Integer.parseInt(((String) raw).trim());
                } catch (Throwable ignored) {
                    // try next key
                }
            }
        }
        return -1;
    }
}
