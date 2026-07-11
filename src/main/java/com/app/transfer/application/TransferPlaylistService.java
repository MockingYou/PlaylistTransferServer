package com.app.transfer.application;

import com.app.transfer.domain.Playlist;
import com.app.transfer.domain.StreamingProvider;
import com.app.transfer.domain.Track;
import com.app.transfer.exception.QuotaExceededException;
import com.app.transfer.ports.in.TransferPlaylistUseCase;
import com.app.transfer.ports.out.MusicProviderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
            Map<StreamingProvider, String> accessTokensByProvider
    ) {
        MusicProviderPort source = resolve(sourceProvider);
        MusicProviderPort destination = resolve(destinationProvider);

        String sourceToken = requireToken(accessTokensByProvider, sourceProvider);
        String destinationToken = requireToken(accessTokensByProvider, destinationProvider);

        // 1. Fetch source playlist
        Playlist sourcePlaylist = source.fetchPlaylist(sourcePlaylistUrl, sourceToken);

        String finalName = (destinationPlaylistName != null && !destinationPlaylistName.isBlank())
                ? destinationPlaylistName
                : sourcePlaylist.getName();

        // 2. Create destination playlist upfront — so even a partial transfer leaves something usable
        String destinationPlaylistId = destination.createPlaylist(
                null, finalName, sourcePlaylist.getDescription(), true, destinationToken);

        // 3. Search each track on destination, collecting matches and misses.
        List<String> matchedTrackIds = new ArrayList<>();
        List<String> unmatchedTitles = new ArrayList<>();
        String warning = null;

        List<Track> sourceTracks = sourcePlaylist.getTracks();
        int processedCount = 0;

        try {
            for (Track track : sourceTracks) {
                Optional<String> match = destination.searchTrack(track, destinationToken);
                if (match.isPresent()) {
                    matchedTrackIds.add(match.get());
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
            destination.addTracksToPlaylist(destinationPlaylistId, matchedTrackIds, destinationToken);
        }

        return TransferResult.builder()
                .destinationPlaylistId(destinationPlaylistId)
                .totalTracks(sourceTracks.size())
                .matchedTracks(matchedTrackIds.size())
                .unmatchedTrackTitles(unmatchedTitles)
                .warning(warning)
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