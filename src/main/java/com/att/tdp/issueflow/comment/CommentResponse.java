package com.att.tdp.issueflow.comment;

import java.time.Instant;

public record CommentResponse(
        Long id,
        Long ticketId,
        Long authorId,
        String authorUsername,
        String content,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
