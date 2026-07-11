package com.app.transfer.domain;

import lombok.Builder;
import lombok.Value;

import java.util.Optional;

@Value
@Builder
public class Track {
    String title;
    String artist;

    @Builder.Default
    Optional<String> albumName = Optional.empty();

    @Builder.Default
    Optional<Integer> durationMs = Optional.empty();

    @Builder.Default
    Optional<String> isrc = Optional.empty();

    String sourceProviderId;
}