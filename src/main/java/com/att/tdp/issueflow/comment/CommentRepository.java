package com.att.tdp.issueflow.comment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("""
            SELECT DISTINCT c FROM Comment c
            JOIN FETCH c.author
            JOIN FETCH c.ticket
            LEFT JOIN FETCH c.mentions m
            LEFT JOIN FETCH m.mentionedUser
            WHERE c.ticket.id = :ticketId
            ORDER BY c.createdAt ASC
            """)
    List<Comment> findByTicketIdOrderByCreatedAtAsc(@Param("ticketId") Long ticketId);

    @Query("""
            SELECT c FROM Comment c
            JOIN FETCH c.author
            JOIN FETCH c.ticket
            LEFT JOIN FETCH c.mentions m
            LEFT JOIN FETCH m.mentionedUser
            WHERE c.id = :id
            """)
    Optional<Comment> findByIdWithMentions(@Param("id") Long id);
}
