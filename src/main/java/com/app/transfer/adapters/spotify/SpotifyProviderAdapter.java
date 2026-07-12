package com.app.transfer.adapters.spotify;

import com.app.transfer.adapters.spotify.dto.SpotifyCreatePlaylistRequest;
import com.app.transfer.adapters.spotify.dto.SpotifyCreatePlaylistResponse;
import com.app.transfer.adapters.spotify.dto.SpotifyPlaylistResponse;
import com.app.transfer.adapters.spotify.dto.SpotifySearchResponse;
import com.app.transfer.domain.Playlist;
import com.app.transfer.domain.StreamingProvider;
import com.app.transfer.domain.Track;
import com.app.transfer.exception.PlaylistNotFoundException;
import com.app.transfer.exception.ProviderApiException;
import com.app.transfer.exception.ProviderAuthenticationException;
import com.app.transfer.ports.out.MusicProviderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
@Component
@RequiredArgsConstructor
public class SpotifyProviderAdapter implements MusicProviderPort {

    private final WebClient spotifyWebClient;

    @Override
    public StreamingProvider getProviderType() {
        return StreamingProvider.SPOTIFY;
    }

    @Override
    public Playlist fetchPlaylist(String playlistUrlOrId, String accessToken) {
        String playlistId = SpotifyUrlParser.extractPlaylistId(playlistUrlOrId);

        SpotifyPlaylistResponse response;
        try {
            response = spotifyWebClient.get()
                    .uri("/playlists/{id}", playlistId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(SpotifyPlaylistResponse.class)
                    .block(); // synchronous for now — fine for MVC app; revisit if we go reactive later
        } catch (WebClientResponseException e) {
            throw translateSpotifyError(e, playlistId);
        }

        if (response == null) {
            throw new PlaylistNotFoundException("Spotify playlist not found or inaccessible: " + playlistId);
        }

        List<Track> tracks = new ArrayList<>();
        String nextPageUrl = collectTracksFromPage(response.getTracks(), tracks);

        // Handle pagination — Spotify caps each page at 100 items
        while (nextPageUrl != null) {
            SpotifyPlaylistResponse.SpotifyTracksPage nextPage;
            try {
                nextPage = spotifyWebClient.get()
                        .uri(nextPageUrl)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .retrieve()
                        .bodyToMono(SpotifyPlaylistResponse.SpotifyTracksPage.class)
                        .block();
            } catch (WebClientResponseException e) {
                throw translateSpotifyError(e, playlistId);
            }

            if (nextPage == null) break;
            nextPageUrl = collectTracksFromPage(nextPage, tracks);
        }

        return Playlist.builder()
                .name(response.getName())
                .description(response.getDescription())
                .isPublic(true)
                .tracks(tracks)
                .sourceProviderId(playlistId)
                .build();
    }

    private String collectTracksFromPage(SpotifyPlaylistResponse.SpotifyTracksPage page, List<Track> accumulator) {
        if (page == null || page.getItems() == null) return null;

        for (SpotifyPlaylistResponse.SpotifyPlaylistItem item : page.getItems()) {
            if (item.getTrack() == null) continue;
            accumulator.add(mapToTrack(item.getTrack()));
        }
        return page.getNext();
    }

    private Track mapToTrack(SpotifyPlaylistResponse.SpotifyTrack spotifyTrack) {
        String artistName = spotifyTrack.getArtists() != null && !spotifyTrack.getArtists().isEmpty()
                ? spotifyTrack.getArtists().get(0).getName()
                : "Unknown Artist";

        return Track.builder()
                .title(spotifyTrack.getName())
                .artist(artistName)
                .albumName(Optional.ofNullable(spotifyTrack.getAlbum())
                        .map(SpotifyPlaylistResponse.SpotifyAlbum::getName))
                .durationMs(Optional.ofNullable(spotifyTrack.getDuration_ms()))
                .isrc(Optional.ofNullable(spotifyTrack.getExternal_ids())
                        .map(SpotifyPlaylistResponse.ExternalIds::getIsrc))
                .sourceProviderId("spotify:track:" + spotifyTrack.getId())
                .build();
    }

    @Override
    public String createPlaylist(String userId, String name, String description, boolean isPublic, String accessToken) {
        SpotifyCreatePlaylistRequest request = new SpotifyCreatePlaylistRequest();
        request.setName(name);
        request.setDescription(description);
        request.setPublic(isPublic);

        SpotifyCreatePlaylistResponse response;
        try {
            response = spotifyWebClient.post()
                    .uri("/me/playlists")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(SpotifyCreatePlaylistResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw translateSpotifyError(e, name);
        }

        if (response == null) {
            throw new ProviderApiException("Spotify returned an empty response when creating playlist: " + name);
        }

        return response.getId();
    }

    @Override
    public Optional<String> searchTrack(Track track, String accessToken) {
        // 1. ISRC — exact match, try first when available
        if (track.getIsrc().isPresent()) {
            Optional<String> isrcMatch = searchByQuery("isrc:" + track.getIsrc().get(), accessToken, track, false);
            if (isrcMatch.isPresent()) {
                return isrcMatch;
            }
        }

        // 2. Strict field-filtered search — fast, precise, but brittle against messy metadata
        Optional<String> strict = searchByQuery(
                track.getTitle() + " artist:" + track.getArtist(), accessToken, track, true);
        if (strict.isPresent()) {
            return strict;
        }

        // 3. Loose free-text search — no field filters, just title + artist as plain words.
        // Spotify's free-text search is much more forgiving of word order, extra words,
        // and imperfect artist names than the "artist:" field filter.
        Optional<String> loose = searchByQuery(
                track.getTitle() + " " + track.getArtist(), accessToken, track, true);
        if (loose.isPresent()) {
            return loose;
        }

        // 4. Title-only — covers cases where our "artist" guess was actually wrong/noise
        // (e.g. a YouTube channel name that isn't the real performing artist)
        Optional<String> titleOnly = searchByQuery(track.getTitle(), accessToken, track, true);
        if (titleOnly.isPresent()) {
            return titleOnly;
        }

        // 5. Swapped order — covers titles we misread as "Artist - Title" when they were
        // actually "Title - Artist"
        return searchByQuery(track.getArtist() + " " + track.getTitle(), accessToken, track, true);
    }

    private Optional<String> searchByQuery(String query, String accessToken, Track target, boolean scoreResults) {
        SpotifySearchResponse response;
        try {
            response = spotifyWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("type", "track")
                            .queryParam("limit", 10) // Spotify capped this at 10 max as of Feb 2026
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(SpotifySearchResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new ProviderAuthenticationException("Spotify access token is invalid or expired");
            }
            return Optional.empty();
        }

        if (response == null || response.getTracks() == null || response.getTracks().getItems() == null
                || response.getTracks().getItems().isEmpty()) {
            return Optional.empty();
        }

        List<SpotifyPlaylistResponse.SpotifyTrack> candidates = response.getTracks().getItems();

        if (!scoreResults) {
            // ISRC search — trust the top result, since ISRC matches are already exact
            return Optional.of("spotify:track:" + candidates.get(0).getId());
        }

        // Score every candidate against the target and take the best one above a minimum threshold,
        // rather than blindly trusting whichever result Spotify ranks first.
        SpotifyPlaylistResponse.SpotifyTrack best = null;
        double bestScore = 0.0;

        for (SpotifyPlaylistResponse.SpotifyTrack candidate : candidates) {
            String candidateArtist = candidate.getArtists() != null && !candidate.getArtists().isEmpty()
                    ? candidate.getArtists().get(0).getName()
                    : "";
            double score = similarity(target.getTitle(), candidate.getName())
                    + similarity(target.getArtist(), candidateArtist);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        // Threshold is loose on purpose — we WANT to catch messy YouTube-derived metadata.
        // 0.9 out of a possible 2.0 (title + artist each contributing 0-1) means roughly
        // "at least one of the two fields is a strong match, or both are a loose match."
        if (best != null && bestScore >= 0.9) {
            return Optional.of("spotify:track:" + best.getId());
        }

        return Optional.empty();
    }

    /**
     * Simple, dependency-free similarity score in [0.0, 1.0], based on normalized
     * token overlap. Not as precise as a real edit-distance algorithm, but good
     * enough to distinguish "clearly the same song" from "clearly unrelated" —
     * which is all we need here.
     */
    private double similarity(String a, String b) {
        if (a == null || b == null) return 0.0;

        String na = normalize(a);
        String nb = normalize(b);

        if (na.isEmpty() || nb.isEmpty()) return 0.0;
        if (na.equals(nb)) return 1.0;
        if (na.contains(nb) || nb.contains(na)) return 0.85;

        Set<String> tokensA = new HashSet<>(List.of(na.split("\\s+")));
        Set<String> tokensB = new HashSet<>(List.of(nb.split("\\s+")));

        Set<String> intersection = new HashSet<>(tokensA);
        intersection.retainAll(tokensB);

        Set<String> union = new HashSet<>(tokensA);
        union.addAll(tokensB);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private String normalize(String s) {
        return s.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    @Override
    public void addTracksToPlaylist(String playlistId, List<String> providerTrackIds, String accessToken) {
        // Spotify allows max 100 URIs per request — chunk if needed
        List<List<String>> batches = chunkList(providerTrackIds, 100);

        for (List<String> batch : batches) {
            Map<String, Object> body = Map.of("uris", batch);

            try {
                spotifyWebClient.post()
                        .uri("/playlists/{playlistId}/items", playlistId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .bodyValue(body)
                        .retrieve()
                        .toBodilessEntity()
                        .block();
            } catch (WebClientResponseException e) {
                throw translateSpotifyError(e, playlistId);
            }
        }
    }

    private List<List<String>> chunkList(List<String> list, int size) {
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            chunks.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return chunks;
    }

    /**
     * Central place to translate Spotify's HTTP error responses into our domain exceptions.
     * Keeping this in one method avoids repeating the same status-code checks at every call site.
     */
    private RuntimeException translateSpotifyError(WebClientResponseException e, String context) {
        int status = e.getStatusCode().value();
        return switch (status) {
            case 401 -> new ProviderAuthenticationException("Spotify access token is invalid or expired");
            case 403 -> new ProviderAuthenticationException(
                    "Not authorized to access this Spotify resource (may not be owned/collaborated by you): " + context);
            case 404 -> new PlaylistNotFoundException("Spotify resource not found: " + context);
            case 429 -> new ProviderApiException("Spotify rate limit exceeded — try again shortly");
            default -> new ProviderApiException("Spotify API error (" + status + ") for " + context + ": " + e.getMessage(), e);
        };
    }

    @Override
    public String extractPlaylistId(String playlistUrlOrId) {
        return SpotifyUrlParser.extractPlaylistId(playlistUrlOrId);
    }
}