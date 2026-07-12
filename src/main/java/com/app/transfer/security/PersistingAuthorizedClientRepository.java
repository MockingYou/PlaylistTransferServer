package com.app.transfer.security;

import com.app.transfer.domain.StreamingProvider;
import com.app.transfer.user.ProviderConnection;
import com.app.transfer.user.ProviderConnectionRepository;
import com.app.transfer.user.User;
import com.app.transfer.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class PersistingAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

    private final HttpSessionOAuth2AuthorizedClientRepository delegate = new HttpSessionOAuth2AuthorizedClientRepository();
    private final UserRepository userRepository;
    private final ProviderConnectionRepository providerConnectionRepository;

    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String registrationId, Authentication principal, HttpServletRequest request) {
        return delegate.loadAuthorizedClient(registrationId, principal, request);
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal,
                                     HttpServletRequest request, HttpServletResponse response) {
        delegate.saveAuthorizedClient(authorizedClient, principal, request, response);

        // Only persist if there's a real logged-in app user AND the provider gave us a refresh token
        // (Spotify/Google both issue one for offline access with our configured scopes).
        if (principal != null && principal.isAuthenticated() && authorizedClient.getRefreshToken() != null) {
            userRepository.findByEmail(principal.getName()).ifPresent(user -> {
                StreamingProvider provider = mapRegistrationId(authorizedClient.getClientRegistration().getRegistrationId());

                ProviderConnection connection = providerConnectionRepository
                        .findByUserAndProvider(user, provider)
                        .orElseGet(ProviderConnection::new);

                connection.setUser(user);
                connection.setProvider(provider);
                connection.setRefreshToken(authorizedClient.getRefreshToken().getTokenValue());
                connection.setUpdatedAt(Instant.now());

                providerConnectionRepository.save(connection);
            });
        }
    }

    @Override
    public void removeAuthorizedClient(String registrationId, Authentication principal, HttpServletRequest request, HttpServletResponse response) {
        delegate.removeAuthorizedClient(registrationId, principal, request, response);
    }

    private StreamingProvider mapRegistrationId(String registrationId) {
        return "spotify".equals(registrationId) ? StreamingProvider.SPOTIFY : StreamingProvider.YOUTUBE;
    }
}