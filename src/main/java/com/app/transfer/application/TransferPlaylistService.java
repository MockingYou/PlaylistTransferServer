package com.app.transfer.application;

import com.app.transfer.domain.Playlist;
import com.app.transfer.domain.StreamingProvider;
import com.app.transfer.domain.Track;
import com.app.transfer.exception.QuotaExceededException;
import com.app.transfer.ports.in.TransferPlaylistUseCase;
import com.app.transfer.ports.out.MusicProviderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TransferPlaylistService implements TransferPlaylistUseCase {

    private final List<MusicProviderPort> providers;

    private MusicProviderPort resolve(StreamingProvider type) {
        return providers.stream()
                .filter(p -> p.getProviderType() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No adapter registered for provider: " + type));
    }

    @Override
    public TransferResult transferPlaylist(
            StreamingProvider sourceProvider,
            StreamingProvider destinationProvider,
            String sourcePlaylistUrl,
            String destinationPlaylistName,
            String destinationPlaylistUrl,
            Map<StreamingProvider, String> accessTokensByProvider
    ) {
        MusicProviderPort source = resolve(sourceProvider);
        MusicProviderPort destination = resolve(destinationProvider);

        String sourceToken = requireToken(accessTokensByProvider, sourceProvider);
        String destinationToken = requireToken(accessTokensByProvider, destinationProvider);

        Playlist sourcePlaylist = source.fetchPlaylist(sourcePlaylistUrl, sourceToken);

        String destinationPlaylistId;
        boolean isNewPlaylist;
        Set<String> existingTrackIds = Collections.emptySet();

        if (destinationPlaylistUrl != null && !destinationPlaylistUrl.isBlank()) {
            destinationPlaylistId = destination.extractPlaylistId(destinationPlaylistUrl);
            isNewPlaylist = false;

            // Fetch what's already in the destination so we can skip re-adding it.
            // This costs one extra read call, but it's cheap (1 unit on YouTube,
            // no special cost on Spotify) compared to the cost of a messy, duplicated playlist.
            Playlist existingPlaylist = destination.fetchPlaylist(destinationPlaylistId, destinationToken);
            existingTrackIds = new HashSet<>();
            for (Track t : existingPlaylist.getTracks()) {
                existingTrackIds.add(t.getSourceProviderId());
            }
        } else {
            String finalName = (destinationPlaylistName != null && !destinationPlaylistName.isBlank())
                    ? destinationPlaylistName
                    : sourcePlaylist.getName();
            destinationPlaylistId = destination.createPlaylist(
                    null, finalName, sourcePlaylist.getDescription(), true, destinationToken);
            isNewPlaylist = true;
        }

        // LinkedHashSet: dedupes automatically, preserves first-seen order —
        // covers the case where the SOURCE playlist itself has the same song twice.
        Set<String> matchedTrackIds = new LinkedHashSet<>();
        List<String> unmatchedTitles = new ArrayList<>();
        int skippedDuplicates = 0;
        String warning = null;

        List<Track> sourceTracks = sourcePlaylist.getTracks();
        int processedCount = 0;

        try {
            for (Track track : sourceTracks) {
                Optional<String> match = destination.searchTrack(track, destinationToken);
                if (match.isPresent()) {
                    String matchedId = match.get();

                    boolean alreadyInDestination = existingTrackIds.contains(matchedId);
                    boolean alreadyQueuedThisRun = matchedTrackIds.contains(matchedId);

                    if (alreadyInDestination || alreadyQueuedThisRun) {
                        skippedDuplicates++;
                    } else {
                        matchedTrackIds.add(matchedId);
                    }
                } else {
                    unmatchedTitles.add(track.getTitle() + " - " + track.getArtist());
                }
                processedCount++;
            }
        } catch (QuotaExceededException e) {
            int remaining = sourceTracks.size() - processedCount;
            warning = e.getMessage() + " (" + processedCount + "/" + sourceTracks.size()
                    + " tracks processed before quota ran out; " + remaining + " remaining tracks were not searched)";
        }

        if (!matchedTrackIds.isEmpty()) {
            destination.addTracksToPlaylist(destinationPlaylistId, new ArrayList<>(matchedTrackIds), destinationToken);
        }

        return TransferResult.builder()
                .destinationPlaylistId(destinationPlaylistId)
                .totalTracks(sourceTracks.size())
                .matchedTracks(matchedTrackIds.size())
                .unmatchedTrackTitles(unmatchedTitles)
                .skippedDuplicates(skippedDuplicates)
                .warning(warning)
                .appendedToExisting(!isNewPlaylist)
                .build();
    }

    private String requireToken(Map<StreamingProvider, String> tokens, StreamingProvider provider) {
        String token = tokens.get(provider);
        if (token == null) {
            throw new IllegalStateException("Missing access token for provider: " + provider);
        }
        return token;
    }
}