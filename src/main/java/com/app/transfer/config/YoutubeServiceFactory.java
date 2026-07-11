package com.app.transfer.config;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;

@Component
public class YoutubeServiceFactory {

    public YouTube create(String accessToken) {
        HttpRequestInitializer requestInitializer = request ->
                request.getHeaders().setAuthorization("Bearer " + accessToken);

        try {
            return new YouTube.Builder(
                    NetHttpTransport.class.getDeclaredConstructor().newInstance(),
                    GsonFactory.getDefaultInstance(),
                    requestInitializer)
                    .setApplicationName("transfer")
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build YouTube service", e);
        }
    }
}