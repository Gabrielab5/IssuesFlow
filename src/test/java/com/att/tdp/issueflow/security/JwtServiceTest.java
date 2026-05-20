package com.att.tdp.issueflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-change-in-production-256-bits";
    private static final String ISSUER = "issueflow-test";

    @Test
    void issueAndParseRoundTrip() {
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60, ISSUER));

        String token = jwtService.issue("alice", "ADMIN");

        Claims claims = jwtService.parse(token);
        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(claims.getId()).isNotBlank();
        assertThat(jwtService.getJti(token)).isEqualTo(claims.getId());
        assertThat(jwtService.getExpiry(token)).isAfter(Instant.now());
    }

    @Test
    void parseRejectsTamperedToken() {
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60, ISSUER));
        String token = jwtService.issue("alice", "ADMIN");
        String[] parts = token.split("\\.");
        parts[1] = replaceFirstCharacter(parts[1]);
        String tamperedToken = String.join(".", parts);

        assertThatThrownBy(() -> jwtService.parse(tamperedToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parseRejectsExpiredToken() {
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, -1, ISSUER));
        String token = jwtService.issue("alice", "ADMIN");

        assertThatThrownBy(() -> jwtService.parse(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    private String replaceFirstCharacter(String value) {
        char replacement = value.charAt(0) == 'a' ? 'b' : 'a';
        return replacement + value.substring(1);
    }
}
