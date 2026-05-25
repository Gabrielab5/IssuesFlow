package com.att.tdp.issueflow.audit;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        AuditAction action,
        String entityType,
        Long entityId,
        Long performedBy,
        AuditActor actor,
        String payload,
        Instant timestamp,
        Instant createdAt
) {}
