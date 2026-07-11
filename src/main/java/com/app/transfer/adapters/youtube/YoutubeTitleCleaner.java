package com.app.transfer.adapters.youtube;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YoutubeTitleCleaner {

    // Broad set of noise phrases found in music video titles, case-insensitive
    private static final List<String> NOISE_PHRASES = List.of(
            "official music video", "official video", "official audio",
            "official lyric video", "official lyrics video", "lyric video", "lyrics video", "lyrics",
            "official visualizer", "visualizer", "official performance video",
            "audio only", "full audio", "hd audio", "hq audio",
            "remastered", "remaster", "clean version", "explicit version",
            "4k", "hd", "hq", "1080p", "high quality",
            "official", "video", "audio"
    );

    private static final Pattern BRACKETED_NOISE = Pattern.compile(
            "[\\(\\[]([^\\)\\]]*)[\\)\\]]"
    );

    private static final Pattern FEAT_PATTERN = Pattern.compile(
            "(?i)\\s*(feat\\.?|ft\\.?|featuring)\\s+.*$"
    );

    private static final Pattern CHANNEL_SUFFIX = Pattern.compile(
            "(?i)\\s*-\\s*(Topic|VEVO)\\s*$|(?i)\\s*\\bVEVO\\b\\s*$"
    );

    private static final Pattern DASH_NORMALIZER = Pattern.compile("[–—]");

    public static CleanedTitle clean(String rawTitle, String channelName) {
        String title = DASH_NORMALIZER.matcher(rawTitle).replaceAll("-");
        String channel = DASH_NORMALIZER.matcher(channelName).replaceAll("-");

        // Strip bracketed noise segments whose content matches known noise phrases
        title = stripBracketedNoise(title);

        // Strip "feat. X" / "ft. X" — often disrupts matching more than it helps
        title = FEAT_PATTERN.matcher(title).replaceAll("");

        // Clean the channel name of "- Topic" / "VEVO" suffixes
        String cleanedChannel = CHANNEL_SUFFIX.matcher(channel).replaceAll("").trim();

        title = title.replaceAll("\\s{2,}", " ").trim();
        title = stripSurroundingQuotes(title);

        // Try splitting on " - " to separate artist/title
        String[] parts = title.split("\\s*-\\s*", 2);

        if (parts.length == 2) {
            String left = parts[0].trim();
            String right = parts[1].trim();

            // Decide which side is the artist by checking similarity to the channel name
            if (namesMatch(left, cleanedChannel)) {
                return new CleanedTitle(right, left);
            } else if (namesMatch(right, cleanedChannel)) {
                return new CleanedTitle(left, right);
            }

            // No clear match — default to conventional "Artist - Title" order
            return new CleanedTitle(right, left);
        }

        // No dash found at all — fall back to channel name as the artist guess
        return new CleanedTitle(title, cleanedChannel.isBlank() ? "Unknown Artist" : cleanedChannel);
    }

    private static String stripBracketedNoise(String title) {
        Matcher matcher = BRACKETED_NOISE.matcher(title);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            String inner = matcher.group(1).toLowerCase();
            boolean isNoise = NOISE_PHRASES.stream().anyMatch(inner::contains);
            result.append(title, lastEnd, matcher.start());
            if (!isNoise) {
                result.append(matcher.group()); // keep it — might be meaningful, e.g. "(Remix)" by a real artist name, "(feat. X)" handled separately
            }
            lastEnd = matcher.end();
        }
        result.append(title.substring(lastEnd));
        return result.toString().replaceAll("\\s{2,}", " ").trim();
    }

    private static String stripSurroundingQuotes(String s) {
        return s.replaceAll("^[\"']|[\"']$", "").trim();
    }

    private static boolean namesMatch(String candidate, String channelName) {
        if (channelName.isBlank()) return false;
        String a = candidate.toLowerCase().replaceAll("[^a-z0-9]", "");
        String b = channelName.toLowerCase().replaceAll("[^a-z0-9]", "");
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    public record CleanedTitle(String title, String artist) {}
}