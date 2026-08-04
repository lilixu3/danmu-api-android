package com.example.danmuapiapp.xposed;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DanmuXposedTextPolicy {
    private static final Pattern PATTERN_SEASON_EPISODE = Pattern.compile("(?i)(?:^|[^a-z0-9])s\\s*0*(\\d{1,2})\\s*e\\s*0*(\\d{1,3})(?:\\D|$)");
    private static final Pattern PATTERN_EXPLICIT_EPISODE = Pattern.compile("(?i)(?:第\\s*([0-9]{1,3}|[一二两三四五六七八九十百零〇]+)\\s*[集期话]|(?:episode|ep|e)\\s*\\.?\\s*0*(\\d{1,3})(?:\\D|$)|[_\\-.\\s\\[]0*(\\d{1,3})(?=[_\\-.\\s\\]集期话]|$))");
    private static final Pattern PATTERN_TRAILING_NUMBER = Pattern.compile("(?:^|[^0-9])0*(\\d{1,3})(?:\\D*)$");
    private static final Pattern PATTERN_YEAR = Pattern.compile("(?<!\\d)(?:19|20)\\d{2}(?!\\d)");
    private static final Pattern PATTERN_EXPLICIT_SEASON = Pattern.compile(
        "(?i)(?:第\\s*([0-9]{1,2}|[一二两三四五六七八九十零〇]+)\\s*[季部]|(?<![a-z0-9])(?:season|s)\\s*0*(\\d{1,2})(?!\\d|\\s*e\\s*\\d))");
    private static final Pattern PATTERN_ROMAN_SEASON_SUFFIX = Pattern.compile(
        "(?i)(?:^|[\\s\\-_:：])((?:xii|xi|x|ix|viii|vii|vi|v|iv|iii|ii|i)|[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩⅪⅫ])\\s*$");
    private static final Pattern PATTERN_VARIETY_DATE = Pattern.compile(
        "(?<!\\d)((?:19|20)\\d{2})[-./年]?([01]?\\d)[-./月]?([0-3]?\\d)(?:日)?(?!\\d)");
    private static final Pattern PATTERN_VARIETY_PERIOD = Pattern.compile(
        "第\\s*([0-9]{1,3}|[一二两三四五六七八九十百零〇]+)\\s*[期篇]([^,，。]*)");
    private static final Pattern PATTERN_BRACKETED = Pattern.compile("[\\[【「『（(](.*?)[\\]】」』）)]");
    private static final Pattern PATTERN_NOISE_WORDS = Pattern.compile("(?i)(全\\s*\\d+\\s*[集期话]|全集|全季|完结|更新至|更至|第\\s*\\d+\\s*[集期话]|s\\s*\\d+\\s*e\\s*\\d+|episode\\s*\\d+|ep\\s*\\d+|e\\s*\\d+|4k|8k|1080p|720p|2160p|hdr|h265|h264|x264|x265|web[- ]?dl|bluray|webrip|国语|国剧|国产|内地|大陆|电视剧|连续剧|剧集|电影|综艺|动漫|动画|中字|字幕|高清|超清|蓝光|资源|网盘|阿里云盘|夸克|百度云|腾讯|爱奇艺|优酷|mkv|mp4|avi)");
    private static final Pattern PATTERN_LEADING_FILE_SIZE = Pattern.compile("^\\s*[\\[【(（]\\s*[0-9]+(?:\\.[0-9]+)?\\s*(?:gb|g|mb|m|kb|k|tb|t)\\s*[\\]】)）]\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_LEADING_EPISODE_FILE = Pattern.compile("^\\s*0*(\\d{1,3})(?=\\s*(?:4k|8k|1080p|720p|2160p|hdr|hevc|h265|h264|x264|x265|\\.|-|_|$))", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_SOURCE_FROM_TITLE = Pattern.compile("(?i)\\bfrom\\s+([a-zA-Z0-9&]+)\\s*$");
    private static final Pattern PATTERN_MATCH_TRAILING_NOISE = Pattern.compile(
        "(?i)(?:[\\s·・•—–\\-－～~：:_,，。/\\\\|]+)(?:4k|8k|1080p|720p|2160p|hdr|h265|h264|x264|x265|中字|字幕|高清|超清|蓝光)+\\s*$");

    private DanmuXposedTextPolicy() {
    }

    static int extractEpisodeNumber(String raw) {
        if (raw == null) return -1;
        String text = raw.trim();
        if (text.isEmpty()) return -1;
        String strippedFileMeta = stripLeadingFileSize(text);
        Matcher leadingEpisodeFile = PATTERN_LEADING_EPISODE_FILE.matcher(strippedFileMeta);
        if (leadingEpisodeFile.find()) return safeEpisodeNumber(leadingEpisodeFile.group(1));
        Matcher seasonEpisode = PATTERN_SEASON_EPISODE.matcher(strippedFileMeta);
        if (seasonEpisode.find()) return safeEpisodeNumber(seasonEpisode.group(2));
        Matcher episodeFileAfterSize = PATTERN_LEADING_EPISODE_FILE.matcher(text);
        if (episodeFileAfterSize.find()) return safeEpisodeNumber(episodeFileAfterSize.group(1));
        Matcher explicit = PATTERN_EXPLICIT_EPISODE.matcher(strippedFileMeta);
        while (explicit.find()) {
            for (int i = 1; i <= explicit.groupCount(); i++) {
                String group = explicit.group(i);
                int value = parseEpisodeToken(group);
                if (value > 0) return value;
            }
        }
        String withoutNoise = PATTERN_NOISE_WORDS.matcher(strippedFileMeta).replaceAll(" ");
        Matcher trailing = PATTERN_TRAILING_NUMBER.matcher(withoutNoise);
        int fallback = -1;
        while (trailing.find()) {
            int value = safeEpisodeNumber(trailing.group(1));
            if (value > 0) fallback = value;
        }
        return fallback;
    }

    static String normalizeSearchTitle(String raw) {
        String title = normalizeDisplayTitle(normalizeQuoteCharacters(raw));
        title = removeSeasonMarkers(title);
        title = PATTERN_NOISE_WORDS.matcher(title).replaceAll(" ");
        title = stripTitleMetadataYears(title);
        title = title.replaceAll("(?i)\\b[a-z]\\b", " ");
        title = title.replaceAll("[\uD83C-\uDBFF\uDC00-\uDFFF★☆🔥❤♡]+", " ");
        title = title.replaceAll("[·・•—–\\-－～~：:_,，。/\\\\|]+", " ");
        title = title.replaceAll("\\s+", " ").trim();
        return title;
    }

    static String normalizeDisplayTitle(String raw) {
        if (raw == null) return "";
        String title = normalizeQuoteCharacters(raw).trim();
        if (title.isEmpty()) return "";
        title = title.replaceAll("(?i)\\s*\\bfrom\\s+[\\w&.-]+$", " ");
        title = title.replaceAll("\\s*\\|\\s*[\\w&.-]+$", " ");
        Matcher bracket = PATTERN_BRACKETED.matcher(title);
        StringBuffer sb = new StringBuffer();
        while (bracket.find()) {
            String inside = bracket.group(1) == null ? "" : bracket.group(1).trim();
            boolean keepYear = PATTERN_YEAR.matcher(inside).matches();
            boolean keepLikelyTitle = inside.length() >= 2 && inside.length() <= 16 && !PATTERN_NOISE_WORDS.matcher(inside).find() && !PATTERN_YEAR.matcher(inside).find();
            bracket.appendReplacement(sb, keepYear || keepLikelyTitle ? " " + Matcher.quoteReplacement(inside) + " " : " ");
        }
        bracket.appendTail(sb);
        title = sb.toString();
        title = PATTERN_SEASON_EPISODE.matcher(title).replaceAll(" ");
        title = PATTERN_EXPLICIT_EPISODE.matcher(title).replaceAll(" ");
        title = title.replaceAll("(?i)(^|\\s)(tv|hd|sd)(?=\\s|$)", " ");
        title = title.replaceAll("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF★☆🔥❤♡]+", " ");
        title = title.replaceAll("\\s+", " ").trim();
        String collapsed = title.replaceAll("[\\s·・•—–\\-－～~：:_,，。/\\\\|]+", " ").trim();
        String[] parts = collapsed.split("\\s+");
        if (parts.length > 1) {
            for (String part : parts) {
                if (part.length() >= 2 && !PATTERN_NOISE_WORDS.matcher(part).find() && !PATTERN_YEAR.matcher(part).matches()) {
                    return part;
                }
            }
        }
        return collapsed;
    }

    static int extractSeasonNumber(String raw) {
        if (raw == null || raw.trim().isEmpty()) return -1;
        String text = normalizeQuoteCharacters(raw).trim();
        Matcher seasonEpisode = PATTERN_SEASON_EPISODE.matcher(text);
        if (seasonEpisode.find()) {
            int parsed = safeEpisodeNumber(seasonEpisode.group(1));
            if (parsed > 0 && parsed <= 99) return parsed;
        }
        Matcher explicit = PATTERN_EXPLICIT_SEASON.matcher(text);
        if (explicit.find()) {
            for (int i = 1; i <= explicit.groupCount(); i++) {
                int parsed = parseEpisodeToken(explicit.group(i));
                if (parsed > 0 && parsed <= 99) return parsed;
            }
        }
        Matcher roman = PATTERN_ROMAN_SEASON_SUFFIX.matcher(text);
        if (!roman.find()) return -1;
        return parseRomanNumber(roman.group(1));
    }

    static String normalizeTitleForMatch(String raw) {
        if (raw == null) return "";
        String title = normalizeQuoteCharacters(raw).trim();
        if (title.isEmpty()) return "";
        title = title.replaceAll("(?i)\\s*\\bfrom\\s+[\\w&.-]+$", " ");
        title = title.replaceAll("\\s*\\|\\s*[\\w&.-]+$", " ");
        title = removeSeasonMarkers(title);
        title = stripBracketedMatchMetadata(title);
        title = PATTERN_SEASON_EPISODE.matcher(title).replaceAll(" ");
        title = PATTERN_EXPLICIT_EPISODE.matcher(title).replaceAll(" ");
        title = stripTitleMetadataYears(title);
        title = PATTERN_MATCH_TRAILING_NOISE.matcher(title).replaceAll(" ");
        title = title.replaceAll("[\\[\\]【】「」『』（）()·・•—–\\-－～~：:_,，。/\\\\|\\s]+", "");
        return title.toLowerCase(Locale.ROOT).trim();
    }

    static boolean titlesMatch(String left, String right) {
        String a = normalizeTitleForMatch(left);
        String b = normalizeTitleForMatch(right);
        return !a.isEmpty() && a.equals(b);
    }

    static String normalizeVarietyEpisode(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String text = normalizeQuoteCharacters(raw).trim();
        Matcher date = PATTERN_VARIETY_DATE.matcher(text);
        if (date.find()) {
            int month = safeParseInt(date.group(2));
            int day = safeParseInt(date.group(3));
            if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                String suffix = normalizeEpisodeSuffix(date.replaceFirst(" "));
                return String.format(Locale.ROOT, "date:%s%02d%02d:%s", date.group(1), month, day, suffix);
            }
        }
        Matcher period = PATTERN_VARIETY_PERIOD.matcher(text);
        if (period.find()) {
            int number = parseEpisodeToken(period.group(1));
            if (number > 0) {
                String suffix = normalizeEpisodeSuffix(period.group(2));
                return "period:" + number + ":" + suffix;
            }
        }
        return "";
    }

    static String normalizeEpisodeIdentity(String raw) {
        if (raw == null) return "";
        String value = normalizeQuoteCharacters(raw).toLowerCase(Locale.ROOT);
        value = value.replaceAll("[·・•—–\\-－～~：:_,，。/\\\\|\\s]+", "");
        return value.trim();
    }

    static String extractYear(String... candidates) {
        if (candidates == null) return "";
        for (String candidate : candidates) {
            if (candidate == null || candidate.trim().isEmpty()) continue;
            Matcher matcher = PATTERN_YEAR.matcher(candidate);
            if (matcher.find()) return matcher.group();
        }
        return "";
    }

    static String extractTitleMetadataYear(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        Matcher matcher = PATTERN_YEAR.matcher(raw);
        while (matcher.find()) {
            if (isTitleMetadataYear(raw, matcher)) return matcher.group();
        }
        return "";
    }

    static String extractSourceFromTitle(String title) {
        if (title == null || title.trim().isEmpty()) return "";
        Matcher matcher = PATTERN_SOURCE_FROM_TITLE.matcher(title.trim());
        return matcher.find() ? matcher.group(1) : "";
    }

    static String normalizeSourceKey(String raw) {
        if (raw == null) return "";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return "";
        if (value.contains("&")) {
            LinkedHashSet<String> parts = new LinkedHashSet<>();
            for (String part : value.split("&")) {
                String canonical = canonicalSingleSource(part);
                if (!canonical.isEmpty()) parts.add(canonical);
            }
            return joinAmp(parts);
        }
        return canonicalSingleSource(value);
    }

    static String displaySourceName(String source) {
        String key = normalizeSourceKey(source);
        if (key.isEmpty()) return "";
        if (key.contains("&")) {
            ArrayList<String> labels = new ArrayList<>();
            for (String part : key.split("&")) {
                String label = displaySingleSourceName(part);
                if (!label.isEmpty() && !labels.contains(label)) labels.add(label);
            }
            return joinNonBlank(labels.toArray(new String[0])).replace(" · ", "/");
        }
        return displaySingleSourceName(key);
    }

    static boolean isTrailerText(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("预告") || lower.contains("trailer") || lower.contains("花絮") || lower.contains("彩蛋");
    }

    static int safeParseInt(String raw) {
        try {
            if (raw == null || raw.trim().isEmpty()) return -1;
            return Integer.parseInt(raw.trim());
        } catch (Throwable ignored) {
            return -1;
        }
    }

    static Double parseNullableDouble(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0.0d;
        try {
            return Double.parseDouble(raw.trim());
        } catch (Throwable throwable) {
            return null;
        }
    }

    static double safeParseDouble(String raw, double fallback) {
        try {
            return raw == null || raw.trim().isEmpty() ? fallback : Double.parseDouble(raw.trim());
        } catch (Throwable throwable) {
            return fallback;
        }
    }

    static String formatOffsetSeconds(double value) {
        if (Math.abs(value) < 1e-6) return "0";
        String formatted = String.format(Locale.US, "%.3f", value);
        while (formatted.contains(".") && formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        if (formatted.endsWith(".")) formatted = formatted.substring(0, formatted.length() - 1);
        return formatted;
    }

    static String joinNonBlank(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(part.trim());
        }
        return sb.toString();
    }

    private static String stripLeadingFileSize(String text) {
        if (text == null) return "";
        String stripped = PATTERN_LEADING_FILE_SIZE.matcher(text.trim()).replaceFirst("");
        return stripped.trim();
    }

    private static String normalizeQuoteCharacters(String raw) {
        if (raw == null) return "";
        return raw.replace('“', ' ').replace('”', ' ').replace('‘', ' ')
            .replace('’', ' ').replace('"', ' ').replace('\'', ' ');
    }

    private static String stripBracketedMatchMetadata(String raw) {
        Matcher matcher = PATTERN_BRACKETED.matcher(raw == null ? "" : raw);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String inside = matcher.group(1) == null ? "" : matcher.group(1).trim();
            boolean metadata = PATTERN_YEAR.matcher(inside).find()
                || PATTERN_EXPLICIT_SEASON.matcher(inside).find()
                || PATTERN_NOISE_WORDS.matcher(inside).matches();
            matcher.appendReplacement(out, metadata ? " " : " " + Matcher.quoteReplacement(inside) + " ");
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String stripTitleMetadataYears(String raw) {
        String value = raw == null ? "" : raw;
        Matcher matcher = PATTERN_YEAR.matcher(value);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(out, isTitleMetadataYear(value, matcher) ? " " : matcher.group());
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static boolean isTitleMetadataYear(String text, Matcher matcher) {
        int start = matcher.start();
        int end = matcher.end();
        if (start <= 0 || !containsTitleCharacter(text.substring(0, start))) return false;
        char before = text.charAt(start - 1);
        char after = end < text.length() ? text.charAt(end) : '\0';
        if (isOpeningBracket(before) && isClosingBracket(after)) return true;
        if (!Character.isWhitespace(before) && "-_./".indexOf(before) < 0) return false;
        String suffix = text.substring(end);
        if (!containsCjk(text.substring(0, start))) {
            return false;
        }
        suffix = removeSeasonMarkers(suffix);
        suffix = PATTERN_NOISE_WORDS.matcher(suffix).replaceAll(" ");
        suffix = suffix.replaceAll("[\\[\\]【】「」『』（）()·・•—–\\-－～~：:_,，。/\\\\|\\s]+", "");
        return suffix.isEmpty();
    }

    private static boolean containsTitleCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLetterOrDigit(value.charAt(i))) return true;
        }
        return false;
    }

    private static boolean containsCjk(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '\u3400' && c <= '\u9fff') return true;
        }
        return false;
    }

    private static boolean isOpeningBracket(char value) {
        return value == '(' || value == '（' || value == '[' || value == '【';
    }

    private static boolean isClosingBracket(char value) {
        return value == ')' || value == '）' || value == ']' || value == '】';
    }

    private static String removeSeasonMarkers(String raw) {
        String value = raw == null ? "" : raw;
        value = PATTERN_EXPLICIT_SEASON.matcher(value).replaceAll(" ");
        value = PATTERN_ROMAN_SEASON_SUFFIX.matcher(value).replaceAll(" ");
        return value;
    }

    private static String normalizeEpisodeSuffix(String raw) {
        if (raw == null) return "";
        String suffix = normalizeQuoteCharacters(raw).toLowerCase(Locale.ROOT);
        suffix = suffix.replaceAll("(?i)(正在播放|更新至|完整版|正片)", " ");
        suffix = suffix.replaceAll("[\\[\\]【】「」『』（）()·・•—–\\-－～~：:_,，。/\\\\|\\s]+", "");
        return suffix.trim();
    }

    private static int parseRomanNumber(String raw) {
        if (raw == null) return -1;
        String token = raw.trim().toUpperCase(Locale.ROOT);
        switch (token) {
            case "I": case "Ⅰ": return 1;
            case "II": case "Ⅱ": return 2;
            case "III": case "Ⅲ": return 3;
            case "IV": case "Ⅳ": return 4;
            case "V": case "Ⅴ": return 5;
            case "VI": case "Ⅵ": return 6;
            case "VII": case "Ⅶ": return 7;
            case "VIII": case "Ⅷ": return 8;
            case "IX": case "Ⅸ": return 9;
            case "X": case "Ⅹ": return 10;
            case "XI": case "Ⅺ": return 11;
            case "XII": case "Ⅻ": return 12;
            default: return -1;
        }
    }

    private static int parseEpisodeToken(String raw) {
        if (raw == null) return -1;
        String token = raw.trim();
        if (token.isEmpty()) return -1;
        int numeric = safeEpisodeNumber(token);
        if (numeric > 0) return numeric;
        return parseChineseNumber(token);
    }

    private static int safeEpisodeNumber(String raw) {
        int value = safeParseInt(raw);
        if (value <= 0 || value > 999) return -1;
        if (value >= 1900 && value <= 2099) return -1;
        return value;
    }

    private static int parseChineseNumber(String raw) {
        if (raw == null) return -1;
        String text = raw.trim();
        if (text.isEmpty()) return -1;
        int total = 0;
        int current = 0;
        boolean seen = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int digit = chineseDigit(c);
            if (digit >= 0) {
                current = digit;
                seen = true;
            } else if (c == '十') {
                total += (current <= 0 ? 1 : current) * 10;
                current = 0;
                seen = true;
            } else if (c == '百') {
                total += (current <= 0 ? 1 : current) * 100;
                current = 0;
                seen = true;
            }
        }
        int value = total + current;
        return seen && value > 0 && value <= 999 ? value : -1;
    }

    private static int chineseDigit(char c) {
        switch (c) {
            case '零':
            case '〇': return 0;
            case '一': return 1;
            case '二':
            case '两': return 2;
            case '三': return 3;
            case '四': return 4;
            case '五': return 5;
            case '六': return 6;
            case '七': return 7;
            case '八': return 8;
            case '九': return 9;
            default: return -1;
        }
    }

    private static String canonicalSingleSource(String raw) {
        if (raw == null) return "";
        String source = raw.trim().toLowerCase(Locale.ROOT);
        if (source.isEmpty()) return "";
        if ("qq".equals(source)) return "tencent";
        if ("qiyi".equals(source)) return "iqiyi";
        if ("bilibili1".equals(source) || "bili".equals(source)) return "bilibili";
        if ("mango".equals(source) || "mgtv".equals(source)) return "imgo";
        if ("360kan".equals(source) || "kan360".equals(source)) return "360";
        switch (source) {
            case "360":
            case "vod":
            case "tmdb":
            case "douban":
            case "tencent":
            case "youku":
            case "iqiyi":
            case "imgo":
            case "bilibili":
            case "migu":
            case "renren":
            case "hanjutv":
            case "bahamut":
            case "dandan":
            case "sohu":
            case "leshi":
            case "xigua":
            case "maiduidui":
            case "aiyifan":
            case "animeko":
            case "custom":
                return source;
            default:
                return "";
        }
    }

    private static String joinAmp(LinkedHashSet<String> parts) {
        StringBuilder sb = new StringBuilder();
        if (parts == null) return "";
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append('&');
            sb.append(part.trim());
        }
        return sb.toString();
    }

    private static String displaySingleSourceName(String source) {
        String key = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
        switch (key) {
            case "360": return "360";
            case "vod": return "VOD";
            case "tmdb": return "TMDB";
            case "douban": return "豆瓣";
            case "tencent": return "腾讯";
            case "youku": return "优酷";
            case "iqiyi": return "爱奇艺";
            case "imgo": return "芒果TV";
            case "bilibili": return "哔哩哔哩";
            case "migu": return "咪咕";
            case "renren": return "人人";
            case "hanjutv": return "韩剧TV";
            case "bahamut": return "巴哈姆特";
            case "dandan": return "弹弹Play";
            case "sohu": return "搜狐";
            case "leshi": return "乐视";
            case "xigua": return "西瓜";
            case "maiduidui": return "埋堆堆";
            case "aiyifan": return "爱壹帆";
            case "animeko": return "Animeko";
            case "custom": return "自定义源";
            default: return "";
        }
    }
}
