package com.att.tdp.issueflow.auth;

public record AuthTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
    public static AuthTokenResponse bearer(String accessToken, long expiresIn) {
        return new AuthTokenResponse(accessToken, "Bearer", expiresIn);
    }
}
