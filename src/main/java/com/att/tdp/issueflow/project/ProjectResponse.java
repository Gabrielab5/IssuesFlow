package com.att.tdp.issueflow.project;

import java.time.Instant;

public record ProjectResponse(
        Long id,
        String name,
        String description,
        Long ownerId,
        String ownerUsername,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {
}
