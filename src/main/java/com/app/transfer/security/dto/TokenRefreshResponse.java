package com.app.transfer.security.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenRefreshResponse {
    private String access_token;
    private String refresh_token;
    private Integer expires_in;
    private String token_type;
    private String scope;
}