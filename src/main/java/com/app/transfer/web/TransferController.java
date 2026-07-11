package com.app.transfer.web;

import com.app.transfer.application.TransferResult;
import com.app.transfer.domain.StreamingProvider;
import com.app.transfer.ports.in.TransferPlaylistUseCase;
import com.app.transfer.web.dto.TransferRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class TransferController {

    private final TransferPlaylistUseCase transferPlaylistUseCase;
    private final OAuth2AuthorizedClientRepository authorizedClientRepository;

    @PostMapping("/transfers")
    public TransferResult transfer(@Valid @RequestBody TransferRequest request, HttpServletRequest httpRequest) {
        Map<StreamingProvider, String> tokens = new EnumMap<>(StreamingProvider.class);
        tokens.put(request.getSourceProvider(), tokenFor(request.getSourceProvider(), httpRequest));
        tokens.put(request.getDestinationProvider(), tokenFor(request.getDestinationProvider(), httpRequest));

        return transferPlaylistUseCase.transferPlaylist(
                request.getSourceProvider(),
                request.getDestinationProvider(),
                request.getSourcePlaylistUrl(),
                request.getDestinationPlaylistName(),
                tokens
        );
    }

    private String tokenFor(StreamingProvider provider, HttpServletRequest request) {
        String registrationId = provider == StreamingProvider.SPOTIFY ? "spotify" : "google";
        OAuth2AuthorizedClient client = authorizedClientRepository.loadAuthorizedClient(registrationId, null, request);
        if (client == null) {
            throw new IllegalStateException("Not authorized with " + registrationId + ". Visit /oauth2/authorization/" + registrationId);
        }
        return client.getAccessToken().getTokenValue();
    }

    @GetMapping("/test/session-check")
    public Map<String, Boolean> sessionCheck(HttpServletRequest request) {
        boolean spotify = authorizedClientRepository.loadAuthorizedClient("spotify", null, request) != null;
        boolean google = authorizedClientRepository.loadAuthorizedClient("google", null, request) != null;
        return Map.of("spotify", spotify, "google", google);
    }
}