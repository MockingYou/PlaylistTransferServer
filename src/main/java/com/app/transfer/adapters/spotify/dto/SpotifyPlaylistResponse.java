package com.app.transfer.adapters.spotify.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpotifyPlaylistResponse {
    private String id;
    private String name;
    private String description;
    private SpotifyTracksPage tracks;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpotifyTracksPage {
        private java.util.List<SpotifyPlaylistItem> items;
        private String next; // pagination URL, null when no more pages
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpotifyPlaylistItem {
        private SpotifyTrack track;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpotifyTrack {
        private String id;
        private String name;
        private Integer duration_ms;
        private ExternalIds external_ids;
        private java.util.List<SpotifyArtist> artists;
        private SpotifyAlbum album;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpotifyArtist {
        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpotifyAlbum {
        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExternalIds {
        private String isrc;
    }
}