package com.example.danmuapiapp.xposed;

import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.displaySourceName;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.extractEpisodeNumber;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.joinNonBlank;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.normalizeDisplayTitle;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.normalizeSearchTitle;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.normalizeSourceKey;

import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class DanmuXposedEpisodeRepository {
    static final int MODE_ANIME = 1;
    static final int MODE_EPISODE = 2;
    static final int MAX_RETURN_ANIMES = 60;
    static final int MAX_RETURN_EPISODES = 180;
    static final int MAX_AUTO_DETAIL_REQUESTS = 8;

    private final DanmuXposedBridgeClient bridgeClient;
    private final Map<String, AnimeRef> animeRefs = new ConcurrentHashMap<>();
    private final Map<String, EpisodeCandidate> episodeCandidates = new ConcurrentHashMap<>();

    DanmuXposedEpisodeRepository(DanmuXposedBridgeClient.SettingsReader settingsReader) {
        this.bridgeClient = new DanmuXposedBridgeClient(settingsReader);
    }

    DirectSearch searchAnimeDirect(Context context, String keyword) throws Exception {
        return bridgeClient.searchAnimeDirect(context, keyword);
    }

    List<EpisodeCandidate> loadEpisodesForAnime(AnimeRef anime, int targetEpisodeNumber) throws Exception {
        return bridgeClient.loadEpisodesForAnime(anime, targetEpisodeNumber);
    }

    BridgeResult queryAnimeSearch(Context context, String title) {
        BridgeResult result = new BridgeResult();
        String keyword = normalizeSearchTitle(title);
        if (keyword.isEmpty()) keyword = title == null ? "" : title.trim();
        if (keyword.isEmpty()) {
            result.ok = false;
            result.message = "先输入剧名";
            return result;
        }
        try {
            DirectSearch search = searchAnimeDirect(context, keyword);
            result.ok = true;
            result.message = search.animes.isEmpty()
                ? "未搜到，可换关键词"
                : "搜索到 " + search.animes.size() + " 个结果，点剧名展开";
            animeRefs.clear();
            for (int i = 0; i < Math.min(search.animes.size(), MAX_RETURN_ANIMES); i++) {
                AnimeRef anime = search.animes.get(i);
                String handle = UUID.randomUUID().toString();
                animeRefs.put(handle, anime);
                String countLabel = anime.episodeCount > 0 ? "共" + anime.episodeCount + "集" : "";
                String sourceLabel = displaySourceName(anime.source);
                String titleLabel = normalizeDisplayTitle(anime.title);
                if (titleLabel.isEmpty()) titleLabel = anime.title;
                String label = joinNonBlank(titleLabel, countLabel, sourceLabel, anime.type);
                if (label.isEmpty()) label = "剧名 " + (result.candidates.size() + 1);
                result.candidates.add(new CandidateHandle(MODE_ANIME, handle, label, anime.source));
            }
            result.filters.addAll(buildSourceFilters(result.candidates));
        } catch (Throwable throwable) {
            result.ok = false;
            result.message = "弹幕 API 核心搜索失败：" + formatError(throwable);
        }
        return result;
    }

    BridgeResult loadAnimeDetail(String animeHandle, String episodeHint) {
        BridgeResult result = new BridgeResult();
        AnimeRef anime = animeRefs.get(animeHandle == null ? "" : animeHandle.trim());
        if (anime == null) {
            result.ok = false;
            result.message = "剧名候选已过期，请重新搜索";
            return result;
        }
        try {
            int targetEpisodeNumber = extractEpisodeNumber(episodeHint);
            List<EpisodeCandidate> episodes = rankCandidates(
                dedupeCandidates(loadEpisodesForAnime(anime, targetEpisodeNumber)),
                targetEpisodeNumber
            );
            result.ok = true;
            int selectedIndex = findEpisodeIndex(episodes, targetEpisodeNumber);
            result.selectedIndex = selectedIndex;
            result.message = episodes.isEmpty()
                ? "《" + anime.title + "》暂无可推送剧集"
                : "已展开《" + anime.title + "》，默认选中当前播放" + selectedEpisodeText(episodes, selectedIndex, targetEpisodeNumber) + "，上下滑动查看更多";
            episodeCandidates.clear();
            for (int i = 0; i < Math.min(episodes.size(), MAX_RETURN_EPISODES); i++) {
                EpisodeCandidate episode = episodes.get(i);
                String handle = UUID.randomUUID().toString();
                episodeCandidates.put(handle, episode);
                String label = joinNonBlank(episode.episode, episode.name, episode.source);
                if (label.isEmpty()) label = "剧集 " + (result.candidates.size() + 1);
                result.candidates.add(new CandidateHandle(MODE_EPISODE, handle, label));
            }
        } catch (Throwable throwable) {
            result.ok = false;
            result.message = "弹幕 API 核心详情失败：" + formatError(throwable);
        }
        return result;
    }

    EpisodeCandidate loadEpisodeCandidate(String handle) {
        return episodeCandidates.get(handle == null ? "" : handle.trim());
    }

    EpisodeCandidate selectAutoEpisodeInSearchOrder(List<AnimeRef> animes, int targetEpisodeNumber) throws Exception {
        EpisodeCandidate firstFallback = null;
        int limit = Math.min(animes == null ? 0 : animes.size(), MAX_AUTO_DETAIL_REQUESTS);
        for (int i = 0; i < limit; i++) {
            List<EpisodeCandidate> episodes = rankCandidates(
                dedupeCandidates(loadEpisodesForAnime(animes.get(i), targetEpisodeNumber)),
                targetEpisodeNumber
            );
            if (episodes.isEmpty()) continue;
            EpisodeCandidate selected = selectBestEpisode(episodes, targetEpisodeNumber);
            if (firstFallback == null) firstFallback = selected;
            if (targetEpisodeNumber <= 0 || scoreEpisode(selected, targetEpisodeNumber) >= 3) {
                return selected;
            }
        }
        return firstFallback;
    }

    private List<SourceFilter> buildSourceFilters(List<CandidateHandle> candidates) {
        LinkedHashMap<String, SourceFilter> map = new LinkedHashMap<>();
        if (candidates == null) return new ArrayList<>();
        for (CandidateHandle candidate : candidates) {
            if (candidate == null) continue;
            String source = normalizeSourceKey(candidate.source);
            if (source.isEmpty()) continue;
            SourceFilter existing = map.get(source);
            if (existing == null) {
                map.put(source, new SourceFilter(source, displaySourceName(source), 1));
            } else {
                existing.count += 1;
            }
        }
        return new ArrayList<>(map.values());
    }

    private List<EpisodeCandidate> dedupeCandidates(List<EpisodeCandidate> input) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        ArrayList<EpisodeCandidate> out = new ArrayList<>();
        for (EpisodeCandidate candidate : input) {
            if (seen.add(candidate.url)) out.add(candidate);
        }
        return out;
    }

    private List<EpisodeCandidate> rankCandidates(List<EpisodeCandidate> candidates, int targetEpisodeNumber) {
        candidates.sort((a, b) -> {
            int an = episodeSortNumber(a);
            int bn = episodeSortNumber(b);
            if (an != bn) return Integer.compare(an, bn);
            return a.displayLabel().compareTo(b.displayLabel());
        });
        return candidates;
    }

    private int findEpisodeIndex(List<EpisodeCandidate> episodes, int targetEpisodeNumber) {
        if (episodes == null || episodes.isEmpty()) return 0;
        if (targetEpisodeNumber <= 0) return 0;
        int bestIndex = 0;
        int bestScore = -1;
        for (int i = 0; i < episodes.size(); i++) {
            int score = scoreEpisode(episodes.get(i), targetEpisodeNumber);
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
            if (score >= 3) return i;
        }
        return bestIndex;
    }

    private EpisodeCandidate selectBestEpisode(List<EpisodeCandidate> candidates, int targetEpisodeNumber) {
        if (candidates == null || candidates.isEmpty()) return null;
        if (targetEpisodeNumber <= 0) return candidates.get(0);
        EpisodeCandidate best = null;
        int bestScore = -1;
        for (EpisodeCandidate candidate : candidates) {
            int score = scoreEpisode(candidate, targetEpisodeNumber);
            if (score > bestScore || (score == bestScore && episodeSortNumber(candidate) < episodeSortNumber(best))) {
                best = candidate;
                bestScore = score;
            }
        }
        return best == null ? candidates.get(0) : best;
    }

    private int episodeSortNumber(EpisodeCandidate candidate) {
        if (candidate == null) return Integer.MAX_VALUE;
        if (candidate.episodeNumber > 0) return candidate.episodeNumber;
        int parsed = extractEpisodeNumber(candidate.episode);
        return parsed > 0 ? parsed : Integer.MAX_VALUE;
    }

    private int scoreEpisode(EpisodeCandidate candidate, int targetEpisodeNumber) {
        if (candidate == null || targetEpisodeNumber <= 0) return 0;
        if (DanmuXposedTextPolicy.isTrailerText(candidate.episode) || DanmuXposedTextPolicy.isTrailerText(candidate.name)) return 0;
        int parsed = episodeSortNumber(candidate);
        if (parsed == targetEpisodeNumber) return 4;
        if (episodeMatches(candidate.episode, targetEpisodeNumber)) return 3;
        if (episodeMatches(candidate.displayLabel(), targetEpisodeNumber)) return 2;
        return 0;
    }

    private boolean episodeMatches(String text, int targetEpisodeNumber) {
        if (text == null || targetEpisodeNumber <= 0 || DanmuXposedTextPolicy.isTrailerText(text)) return false;
        String target = String.valueOf(targetEpisodeNumber);
        String normalized = text.trim();
        if (normalized.isEmpty()) return false;
        String two = targetEpisodeNumber < 10 ? "0" + targetEpisodeNumber : target;
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("_" + target + "_") || lower.contains("_" + two + "_") ||
            lower.contains("_" + target + ".") || lower.contains("_" + two + ".")) return true;
        int parsed = extractEpisodeNumber(normalized);
        return parsed == targetEpisodeNumber;
    }

    private String selectedEpisodeText(List<EpisodeCandidate> episodes, int selectedIndex, int targetEpisodeNumber) {
        if (episodes == null || episodes.isEmpty()) return targetEpisodeNumber > 0 ? "第" + targetEpisodeNumber + "集" : "剧集";
        int index = Math.max(0, Math.min(selectedIndex, episodes.size() - 1));
        String label = shortEpisodeText(episodes.get(index));
        return label.isEmpty() ? "剧集" : label;
    }

    private String shortEpisodeText(EpisodeCandidate episode) {
        if (episode == null) return "";
        int number = episodeSortNumber(episode);
        if (number != Integer.MAX_VALUE) return "第" + number + "集";
        return episode.episode == null ? "" : episode.episode;
    }

    private String formatError(Throwable throwable) {
        if (throwable == null) return "未知错误";
        String message = throwable.getMessage();
        if (message == null || message.trim().isEmpty()) return throwable.getClass().getSimpleName();
        return throwable.getClass().getSimpleName() + " " + message;
    }
}
