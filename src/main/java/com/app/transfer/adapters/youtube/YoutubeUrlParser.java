package com.app.transfer.adapters.youtube;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YoutubeUrlParser {

    private static final Pattern PLAYLIST_PATTERN = Pattern.compile("[?&]list=([a-zA-Z0-9_-]+)");

    public static String extractPlaylistId(String urlOrId) {
        Matcher matcher = PLAYLIST_PATTERN.matcher(urlOrId);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return urlOrId.trim(); // assume bare ID
    }
}