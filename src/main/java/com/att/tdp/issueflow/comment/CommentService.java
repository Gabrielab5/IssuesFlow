package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.common.annotation.Audited;
import com.att.tdp.issueflow.common.exception.ConflictException;
import com.att.tdp.issueflow.common.exception.ForbiddenException;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.mention.MentionService;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final MentionService mentionService;

    public CommentService(
            CommentRepository commentRepository,
            TicketRepository ticketRepository,
            UserRepository userRepository,
            MentionService mentionService
    ) {
        this.commentRepository = commentRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.mentionService = mentionService;
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> findByTicketId(Long ticketId) {
        requireTicket(ticketId);
        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(CommentMapper::toResponse)
                .toList();
    }

    @Transactional
    @Audited(action = "CREATE", entityType = "Comment")
    public CommentResponse create(Long ticketId, CreateCommentRequest request, Authentication auth) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> NotFoundException.of("Ticket", ticketId));
        User author = userRepository.findByUsernameAndDeletedAtIsNull(auth.getName())
                .orElseThrow(() -> NotFoundException.of("User", auth.getName()));

        Comment comment = new Comment();
        comment.setTicket(ticket);
        comment.setAuthor(author);
        comment.setContent(request.content());

        Comment saved = commentRepository.save(comment);
        mentionService.syncMentions(saved, request.content());
        return CommentMapper.toResponse(saved);
    }

    @Transactional
    @Audited(action = "UPDATE", entityType = "Comment")
    public CommentResponse update(
            Long ticketId, Long commentId, UpdateCommentRequest request, Authentication auth
    ) {
        Comment comment = findComment(ticketId, commentId);
        assertCanModify(comment, auth);
        assertVersionMatches(comment, request.version());
        comment.setContent(request.content());
        mentionService.syncMentions(comment, request.content());
        // dirty-checking flushes the UPDATE; Hibernate also adds AND version=? as a second guard
        return CommentMapper.toResponse(comment);
    }

    @Transactional
    @Audited(action = "DELETE", entityType = "Comment", idExpression = "#commentId")
    public void delete(Long ticketId, Long commentId, Authentication auth) {
        Comment comment = findComment(ticketId, commentId);
        assertCanModify(comment, auth);
        commentRepository.delete(comment);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private void requireTicket(Long ticketId) {
        if (!ticketRepository.existsById(ticketId)) {
            throw NotFoundException.of("Ticket", ticketId);
        }
    }

    private Comment findComment(Long ticketId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> NotFoundException.of("Comment", commentId));
        // Verify the comment belongs to the ticket in the URL (don't leak existence of other tickets)
        if (!comment.getTicket().getId().equals(ticketId)) {
            throw NotFoundException.of("Comment", commentId);
        }
        return comment;
    }

    /**
     * Explicit "If-Match" check: the client must send the version they last read.
     * If it differs from the persisted version, another writer already committed a change.
     * Hibernate's @Version provides a second guard at the SQL level.
     */
    private void assertVersionMatches(Comment comment, Long requestVersion) {
        if (!comment.getVersion().equals(requestVersion)) {
            throw new ConflictException("Resource was modified by another user, please retry");
        }
    }

    private void assertCanModify(Comment comment, Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !comment.getAuthor().getUsername().equals(auth.getName())) {
            throw new ForbiddenException("Only the comment author or an admin may modify this comment");
        }
    }
}
