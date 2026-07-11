package com.app.transfer.ports.in;

import com.app.transfer.application.TransferResult;
import com.app.transfer.domain.StreamingProvider;

import java.util.Map;

public interface TransferPlaylistUseCase {
    TransferResult transferPlaylist(
            StreamingProvider sourceProvider,
            StreamingProvider destinationProvider,
            String sourcePlaylistUrl,
            String destinationPlaylistName,
            Map<StreamingProvider, String> accessTokensByProvider
    );
}