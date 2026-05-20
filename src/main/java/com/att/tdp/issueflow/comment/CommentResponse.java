package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.mention.UserSummary;

import java.time.Instant;
import java.util.List;

public record CommentResponse(
        Long id,
        Long ticketId,
        Long authorId,
        String authorUsername,
        String content,
        Long version,
        Instant createdAt,
        Instant updatedAt,
        List<UserSummary> mentionedUsers
) {
}
