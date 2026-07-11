package com.app.transfer.domain;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class Playlist {
    String name;
    String description;
    boolean isPublic;
    List<Track> tracks;
    String sourceProviderId;
}