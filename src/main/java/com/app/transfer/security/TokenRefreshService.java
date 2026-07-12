package com.app.transfer.security;

import com.app.transfer.domain.StreamingProvider;
import com.app.transfer.exception.ProviderAuthenticationException;
import com.app.transfer.security.dto.TokenRefreshResponse;
import com.app.transfer.user.ProviderConnection;
import com.app.transfer.user.ProviderConnectionRepository;
import com.app.transfer.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TokenRefreshService {

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final ProviderConnectionRepository providerConnectionRepository;
    private final WebClient.Builder webClientBuilder;

    public String getFreshAccessToken(User user, StreamingProvider provider) {
        ProviderConnection connection = providerConnectionRepository.findByUserAndProvider(user, provider)
                .orElseThrow(() -> new ProviderAuthenticationException(
                        "No " + provider + " connection found for this account. Connect it first."));

        String registrationId = provider == StreamingProvider.SPOTIFY ? "spotify" : "google";
        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(registrationId);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", connection.getRefreshToken());
        form.add("client_id", registration.getClientId());
        form.add("client_secret", registration.getClientSecret());

        TokenRefreshResponse response;
        try {
            response = webClientBuilder.build()
                    .post()
                    .uri(registration.getProviderDetails().getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(TokenRefreshResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 400 || e.getStatusCode().value() == 401) {
                throw new ProviderAuthenticationException(
                        provider + " connection has expired or been revoked. Please reconnect it.");
            }
            throw new ProviderAuthenticationException(
                    "Failed to refresh " + provider + " access token: " + e.getMessage());
        }

        if (response == null || response.getAccess_token() == null) {
            throw new ProviderAuthenticationException("Empty token response while refreshing " + provider);
        }

        if (response.getRefresh_token() != null) {
            connection.setRefreshToken(response.getRefresh_token());
            connection.setUpdatedAt(Instant.now());
            providerConnectionRepository.save(connection);
        }

        return response.getAccess_token();
    }
}