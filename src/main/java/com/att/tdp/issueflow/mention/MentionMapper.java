package com.att.tdp.issueflow.mention;

import com.att.tdp.issueflow.comment.Comment;
import com.att.tdp.issueflow.comment.CommentMention;

public final class MentionMapper {

    private MentionMapper() {}

    public static MentionResponse toResponse(CommentMention mention) {
        Comment c = mention.getComment();
        return new MentionResponse(
                mention.getId(),
                c.getId(),
                c.getTicket().getId(),
                c.getAuthor().getId(),
                c.getAuthor().getUsername(),
                c.getContent(),
                mention.getCreatedAt()
        );
    }

    public static UserSummary toUserSummary(com.att.tdp.issueflow.user.User user) {
        return new UserSummary(user.getId(), user.getUsername(), user.getFullName());
    }
}
