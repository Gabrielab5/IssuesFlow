package com.att.tdp.issueflow.mention;

import java.time.Instant;

public record MentionResponse(
        Long id,
        Long commentId,
        Long ticketId,
        Long authorId,
        String authorUsername,
        String commentContent,
        Instant mentionedAt
) {}
