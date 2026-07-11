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
    String warning; // non-null if the transfer completed partially, e.g. due to quota exhaustion
}