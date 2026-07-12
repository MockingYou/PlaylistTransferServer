package com.app.transfer.web.dto;

import com.app.transfer.domain.StreamingProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransferRequest {
    @NotNull
    private StreamingProvider sourceProvider;

    @NotNull
    private StreamingProvider destinationProvider;

    @NotBlank
    private String sourcePlaylistUrl;

    private String destinationPlaylistName;

    private String destinationPlaylistUrl;
}