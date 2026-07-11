package com.app.transfer.adapters.spotify.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpotifySearchResponse {
    private TracksResult tracks;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TracksResult {
        private List<SpotifyPlaylistResponse.SpotifyTrack> items;
    }
}