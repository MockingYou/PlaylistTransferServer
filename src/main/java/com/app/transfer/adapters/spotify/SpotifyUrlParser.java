package com.app.transfer.adapters.spotify;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpotifyUrlParser {

    private static final Pattern PLAYLIST_PATTERN =
            Pattern.compile("playlist[/:]([a-zA-Z0-9]+)");

    public static String extractPlaylistId(String urlOrId) {
        Matcher matcher = PLAYLIST_PATTERN.matcher(urlOrId);
        if (matcher.find()) {
            return matcher.group(1);
        }
        // assume it's already a bare ID if no URL pattern matched
        return urlOrId.trim();
    }
}