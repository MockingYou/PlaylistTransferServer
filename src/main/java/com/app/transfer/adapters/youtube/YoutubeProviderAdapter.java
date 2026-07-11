package com.app.transfer.adapters.youtube;

import com.app.transfer.config.YoutubeServiceFactory;
import com.app.transfer.domain.Playlist;
import com.app.transfer.domain.StreamingProvider;
import com.app.transfer.domain.Track;
import com.app.transfer.exception.PlaylistNotFoundException;
import com.app.transfer.exception.ProviderApiException;
import com.app.transfer.exception.ProviderAuthenticationException;
import com.app.transfer.exception.QuotaExceededException;
import com.app.transfer.ports.out.MusicProviderPort;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class YoutubeProviderAdapter implements MusicProviderPort {

    private final YoutubeServiceFactory serviceFactory;
    private final SearchCacheRepository searchCacheRepository;
    private final SearchQuotaGuard searchQuotaGuard;

    @Override
    public StreamingProvider getProviderType() {
        return StreamingProvider.YOUTUBE;
    }

    @Override
    public Playlist fetchPlaylist(String playlistUrlOrId, String accessToken) {
        YouTube youtube = serviceFactory.create(accessToken);
        String playlistId = YoutubeUrlParser.extractPlaylistId(playlistUrlOrId);

        PlaylistListResponse playlistResponse;
        try {
            playlistResponse = youtube.playlists()
                    .list(List.of("snippet"))
                    .setId(Collections.singletonList(playlistId))
                    .execute();
        } catch (GoogleJsonResponseException e) {
            throw translateYoutubeError(e, playlistId);
        } catch (IOException e) {
            throw new ProviderApiException("Failed to reach YouTube API while fetching playlist: " + playlistId, e);
        }

        if (playlistResponse.getItems().isEmpty()) {
            throw new PlaylistNotFoundException("YouTube playlist not found: " + playlistId);
        }
        com.google.api.services.youtube.model.Playlist ytPlaylist = playlistResponse.getItems().get(0);

        List<Track> tracks = new ArrayList<>();
        String pageToken = null;

        do {
            PlaylistItemListResponse itemsResponse;
            try {
                itemsResponse = youtube.playlistItems()
                        .list(List.of("snippet"))
                        .setPlaylistId(playlistId)
                        .setMaxResults(50L)
                        .setPageToken(pageToken)
                        .execute();
            } catch (GoogleJsonResponseException e) {
                throw translateYoutubeError(e, playlistId);
            } catch (IOException e) {
                throw new ProviderApiException("Failed to reach YouTube API while fetching playlist items: " + playlistId, e);
            }

            for (PlaylistItem item : itemsResponse.getItems()) {
                PlaylistItemSnippet snippet = item.getSnippet();
                if (snippet == null || snippet.getResourceId() == null) continue;

                String rawTitle = snippet.getTitle();
                String channelName = snippet.getVideoOwnerChannelTitle() != null
                        ? snippet.getVideoOwnerChannelTitle()
                        : "Unknown Artist";

                YoutubeTitleCleaner.CleanedTitle cleaned = YoutubeTitleCleaner.clean(rawTitle, channelName);

                tracks.add(Track.builder()
                        .title(cleaned.title())
                        .artist(cleaned.artist())
                        .sourceProviderId("youtube:video:" + snippet.getResourceId().getVideoId())
                        .build());
            }

            pageToken = itemsResponse.getNextPageToken();
        } while (pageToken != null);

        return Playlist.builder()
                .name(ytPlaylist.getSnippet().getTitle())
                .description(ytPlaylist.getSnippet().getDescription())
                .isPublic(true)
                .tracks(tracks)
                .sourceProviderId(playlistId)
                .build();
    }

    @Override
    public String createPlaylist(String userId, String name, String description, boolean isPublic, String accessToken) {
        YouTube youtube = serviceFactory.create(accessToken);

        com.google.api.services.youtube.model.Playlist playlist = new com.google.api.services.youtube.model.Playlist();

        PlaylistSnippet snippet = new PlaylistSnippet();
        snippet.setTitle(name);
        snippet.setDescription(description);
        playlist.setSnippet(snippet);

        PlaylistStatus status = new PlaylistStatus();
        status.setPrivacyStatus(isPublic ? "public" : "private");
        playlist.setStatus(status);

        try {
            com.google.api.services.youtube.model.Playlist created = youtube.playlists()
                    .insert(List.of("snippet", "status"), playlist)
                    .execute();
            return created.getId();
        } catch (GoogleJsonResponseException e) {
            throw translateYoutubeError(e, name);
        } catch (IOException e) {
            throw new ProviderApiException("Failed to reach YouTube API while creating playlist: " + name, e);
        }
    }

    @Override
    public Optional<String> searchTrack(Track track, String accessToken) {
        String cacheKey = QueryNormalizer.normalize(track.getTitle(), track.getArtist());

        // Cache hit — costs nothing but a DB read
        Optional<SearchCacheEntry> cached = searchCacheRepository.findByNormalizedQuery(cacheKey);
        if (cached.isPresent()) {
            return Optional.of("youtube:video:" + cached.get().getYoutubeVideoId());
        }

        // Cache miss — only now do we consider spending real search quota.
        // DESIGN DECISION: quota exhaustion aborts the transfer with a clear error,
        // rather than silently reporting remaining tracks as "unmatched" — a user needs
        // to know "try again tomorrow" is different from "this song doesn't exist."
        // To soften this back to a per-track skip instead, replace the throw below
        // with `return Optional.empty();`.
        if (!searchQuotaGuard.tryConsumeSearch()) {
            throw new QuotaExceededException(
                    "Daily YouTube search quota exhausted. Please try again tomorrow, or transfer a smaller playlist.");
        }

        YouTube youtube = serviceFactory.create(accessToken);
        String query = track.getTitle() + " " + track.getArtist();

        try {
            SearchListResponse response = youtube.search()
                    .list(List.of("snippet"))
                    .setQ(query)
                    .setType(Collections.singletonList("video"))
                    .setVideoCategoryId("10") // Music category — improves match relevance
                    .setMaxResults(1L)
                    .execute();

            if (response.getItems().isEmpty()) {
                return Optional.empty();
            }

            String videoId = response.getItems().get(0).getId().getVideoId();

            SearchCacheEntry entry = new SearchCacheEntry(cacheKey, videoId, java.time.Instant.now());
            searchCacheRepository.save(entry);

            return Optional.of("youtube:video:" + videoId);

        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 401) {
                throw new ProviderAuthenticationException("YouTube access token is invalid or expired");
            }
            // A single failed search shouldn't sink the whole transfer — treat as "no match"
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public void addTracksToPlaylist(String playlistId, List<String> providerTrackIds, String accessToken) {
        YouTube youtube = serviceFactory.create(accessToken);

        for (String trackId : providerTrackIds) {
            String videoId = trackId.replace("youtube:video:", "");

            PlaylistItem item = new PlaylistItem();
            PlaylistItemSnippet snippet = new PlaylistItemSnippet();
            snippet.setPlaylistId(playlistId);

            ResourceId resourceId = new ResourceId();
            resourceId.setKind("youtube#video");
            resourceId.setVideoId(videoId);
            snippet.setResourceId(resourceId);

            item.setSnippet(snippet);

            try {
                youtube.playlistItems()
                        .insert(List.of("snippet"), item)
                        .execute();
            } catch (GoogleJsonResponseException e) {
                if (e.getStatusCode() == 401) {
                    throw new ProviderAuthenticationException("YouTube access token is invalid or expired");
                }
                // Don't let one bad video ID kill the whole transfer — log and continue
                System.err.println("Failed to add video " + videoId + " to playlist " + playlistId + ": " + e.getMessage());
            } catch (IOException e) {
                System.err.println("Failed to reach YouTube API adding video " + videoId + ": " + e.getMessage());
            }
        }
    }

    private RuntimeException translateYoutubeError(GoogleJsonResponseException e, String context) {
        int status = e.getStatusCode();
        return switch (status) {
            case 401 -> new ProviderAuthenticationException("YouTube access token is invalid or expired");
            case 403 -> new ProviderAuthenticationException(
                    "Not authorized to access this YouTube resource: " + context);
            case 404 -> new PlaylistNotFoundException("YouTube resource not found: " + context);
            case 429 -> new QuotaExceededException("YouTube API quota exceeded — try again tomorrow");
            default -> new ProviderApiException("YouTube API error (" + status + ") for " + context + ": " + e.getMessage(), e);
        };
    }
}