package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.mention.MentionMapper;
import com.att.tdp.issueflow.mention.UserSummary;

import java.util.List;

public final class CommentMapper {

    private CommentMapper() {
    }

    public static CommentResponse toResponse(Comment comment) {
        List<UserSummary> mentionedUsers = comment.getMentions().stream()
                .map(m -> MentionMapper.toUserSummary(m.getMentionedUser()))
                .toList();
        return new CommentResponse(
                comment.getId(),
                comment.getTicket().getId(),
                comment.getAuthor().getId(),
                comment.getAuthor().getUsername(),
                comment.getContent(),
                comment.getVersion(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                mentionedUsers
        );
    }
}
