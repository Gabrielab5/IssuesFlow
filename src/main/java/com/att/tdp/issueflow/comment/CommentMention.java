package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.common.entity.BaseEntity;
import com.att.tdp.issueflow.user.User;
import jakarta.persistence.*;

@Entity
@Table(
        name = "comment_mentions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_comment_mentions",
                columnNames = {"comment_id", "mentioned_user_id"}
        )
)
public class CommentMention extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentioned_user_id", nullable = false)
    private User mentionedUser;

    public Comment getComment() { return comment; }
    public void setComment(Comment comment) { this.comment = comment; }

    public User getMentionedUser() { return mentionedUser; }
    public void setMentionedUser(User mentionedUser) { this.mentionedUser = mentionedUser; }
}
