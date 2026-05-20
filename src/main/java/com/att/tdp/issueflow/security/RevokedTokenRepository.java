package com.att.tdp.issueflow.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, Long> {

    boolean existsByJti(String jti);

    long deleteByExpiresAtBefore(Instant expiresAt);
}
