package com.att.tdp.issueflow.audit;

public final class AuditMapper {

    private AuditMapper() {}

    public static AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getPerformedBy(),
                log.getActor(),
                log.getPayload(),
                log.getTimestamp(),
                log.getCreatedAt()
        );
    }
}
