package com.att.tdp.issueflow.security;

import com.att.tdp.issueflow.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "revoked_tokens")
public class RevokedToken extends BaseEntity {

    @Column(nullable = false, unique = true, length = 36)
    private String jti;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public String getJti() { return jti; }
    public void setJti(String jti) { this.jti = jti; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
