package com.att.tdp.issueflow.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "issueflow.jwt")
public record JwtProperties(String secret, long expirationMinutes, String issuer) {

    private static final long DEFAULT_EXPIRATION_MINUTES = 60;
    private static final String DEFAULT_ISSUER = "issueflow";

    public JwtProperties {
        if (expirationMinutes == 0) {
            expirationMinutes = DEFAULT_EXPIRATION_MINUTES;
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = DEFAULT_ISSUER;
        }
    }
}
