package com.app.transfer.adapters.spotify.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SpotifyCreatePlaylistRequest {
    private String name;
    private String description;

    @JsonProperty("public")
    private boolean isPublic;
}