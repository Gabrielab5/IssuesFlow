package com.att.tdp.issueflow.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenDenyListServiceTest {

    @Mock
    private RevokedTokenRepository revokedTokenRepository;

    @InjectMocks
    private TokenDenyListService tokenDenyListService;

    @Test
    void revokePersistsTokenWhenJtiIsNotAlreadyRevoked() {
        Instant expiresAt = Instant.parse("2026-05-20T12:00:00Z");
        when(revokedTokenRepository.existsByJti("token-id")).thenReturn(false);

        tokenDenyListService.revoke("token-id", expiresAt);

        ArgumentCaptor<RevokedToken> tokenCaptor = ArgumentCaptor.forClass(RevokedToken.class);
        verify(revokedTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getJti()).isEqualTo("token-id");
        assertThat(tokenCaptor.getValue().getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void revokeSkipsAlreadyRevokedJti() {
        Instant expiresAt = Instant.parse("2026-05-20T12:00:00Z");
        when(revokedTokenRepository.existsByJti("token-id")).thenReturn(true);

        tokenDenyListService.revoke("token-id", expiresAt);

        verify(revokedTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void isRevokedChecksRepository() {
        when(revokedTokenRepository.existsByJti("token-id")).thenReturn(true);

        assertThat(tokenDenyListService.isRevoked("token-id")).isTrue();
    }

    @Test
    void cleanupExpiredTokensDeletesTokensBeforeNow() {
        Instant beforeCleanup = Instant.now();

        tokenDenyListService.cleanupExpiredTokens();

        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(revokedTokenRepository).deleteByExpiresAtBefore(instantCaptor.capture());
        assertThat(instantCaptor.getValue()).isBetween(beforeCleanup, Instant.now());
    }
}
