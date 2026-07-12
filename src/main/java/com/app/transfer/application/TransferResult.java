package com.app.transfer.application;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class TransferResult {
    String destinationPlaylistId;
    int totalTracks;
    int matchedTracks;
    List<String> unmatchedTrackTitles;
    int skippedDuplicates;
    String warning;
    boolean appendedToExisting;
}