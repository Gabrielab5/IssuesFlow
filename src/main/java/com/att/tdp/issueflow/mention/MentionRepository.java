package com.att.tdp.issueflow.mention;

import com.att.tdp.issueflow.comment.CommentMention;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MentionRepository extends JpaRepository<CommentMention, Long> {

    @EntityGraph(attributePaths = {"comment", "comment.ticket", "comment.author"})
    Page<CommentMention> findByMentionedUserIdOrderByCreatedAtDesc(Long mentionedUserId, Pageable pageable);
}
