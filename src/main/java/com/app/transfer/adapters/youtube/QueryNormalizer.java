package com.app.transfer.adapters.youtube;

public class QueryNormalizer {
    public static String normalize(String title, String artist) {
        return (title.trim().toLowerCase() + "|" + artist.trim().toLowerCase())
                .replaceAll("[^a-z0-9|]", "");
    }
}