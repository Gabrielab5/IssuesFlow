package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.common.exception.ConflictException;
import com.att.tdp.issueflow.common.exception.ForbiddenException;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import com.att.tdp.issueflow.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(commentRepository, ticketRepository, userRepository);
    }

    // ── list ─────────────────────────────────────────────────────────────────

    @Test
    void findByTicketIdReturnsCommentResponses() {
        when(ticketRepository.existsById(1L)).thenReturn(true);
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(comment(10L, ticket(1L), author(5L, "alice"), "hello", 0L)));

        List<CommentResponse> result = commentService.findByTicketId(1L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().content()).isEqualTo("hello");
        assertThat(result.getFirst().authorUsername()).isEqualTo("alice");
    }

    @Test
    void findByTicketIdThrowsNotFoundForUnknownTicket() {
        when(ticketRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> commentService.findByTicketId(99L))
                .isInstanceOf(NotFoundException.class);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void createPersistsCommentWithAuthorFromAuthContext() {
        Ticket ticket = ticket(1L);
        User alice = author(5L, "alice");
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(userRepository.findByUsernameAndDeletedAtIsNull("alice")).thenReturn(Optional.of(alice));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            c.setId(20L);
            c.setVersion(0L);
            return c;
        });

        CommentResponse response = commentService.create(1L, new CreateCommentRequest("hello"), auth("alice", "DEVELOPER"));

        assertThat(response.content()).isEqualTo("hello");
        assertThat(response.authorUsername()).isEqualTo("alice");
    }

    @Test
    void createThrowsNotFoundForUnknownTicket() {
        when(ticketRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.create(99L, new CreateCommentRequest("hi"), auth("alice", "DEVELOPER")))
                .isInstanceOf(NotFoundException.class);
    }

    // ── update: version / optimistic-lock ────────────────────────────────────

    @Test
    void updateSucceedsWhenVersionMatches() {
        Ticket ticket = ticket(1L);
        User alice = author(5L, "alice");
        Comment comment = comment(10L, ticket, alice, "old", 2L);
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        CommentResponse response = commentService.update(
                1L, 10L, new UpdateCommentRequest("new content", 2L), auth("alice", "DEVELOPER"));

        assertThat(response.content()).isEqualTo("new content");
    }

    @Test
    void twoSimultaneousUpdates_secondGetsConflict() {
        // Simulate: TX1 already committed (DB version is now 3).
        // TX2 arrives with the stale version=2 it read before TX1 committed.
        Ticket ticket = ticket(1L);
        User alice = author(5L, "alice");
        Comment commentAtVersion3 = comment(10L, ticket, alice, "original", 3L);
        when(commentRepository.findById(10L)).thenReturn(Optional.of(commentAtVersion3));

        assertThatThrownBy(() -> commentService.update(
                1L, 10L, new UpdateCommentRequest("tx2 edit", 2L), auth("alice", "DEVELOPER")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Resource was modified by another user, please retry");

        verify(commentRepository, never()).save(any());
    }

    @Test
    void updateThrowsNotFoundWhenCommentBelongsToDifferentTicket() {
        Ticket otherTicket = ticket(99L);
        Comment comment = comment(10L, otherTicket, author(5L, "alice"), "hi", 0L);
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        // URL says ticketId=1 but comment belongs to ticket 99
        assertThatThrownBy(() -> commentService.update(
                1L, 10L, new UpdateCommentRequest("edit", 0L), auth("alice", "DEVELOPER")))
                .isInstanceOf(NotFoundException.class);
    }

    // ── update: 403 for non-author non-admin ──────────────────────────────────

    @Test
    void nonAuthorNonAdminGets403OnUpdate() {
        Ticket ticket = ticket(1L);
        Comment comment = comment(10L, ticket, author(5L, "alice"), "alice wrote this", 0L);
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        // bob is a DEVELOPER but not the author
        assertThatThrownBy(() -> commentService.update(
                1L, 10L, new UpdateCommentRequest("bob override", 0L), auth("bob", "DEVELOPER")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Only the comment author or an admin may modify this comment");

        verify(commentRepository, never()).save(any());
    }

    @Test
    void adminCanUpdateAnyComment() {
        Ticket ticket = ticket(1L);
        Comment comment = comment(10L, ticket, author(5L, "alice"), "alice wrote this", 0L);
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        CommentResponse response = commentService.update(
                1L, 10L, new UpdateCommentRequest("admin edit", 0L), auth("admin", "ADMIN"));

        assertThat(response.content()).isEqualTo("admin edit");
    }

    @Test
    void authorCanUpdateOwnComment() {
        Ticket ticket = ticket(1L);
        Comment comment = comment(10L, ticket, author(5L, "alice"), "original", 0L);
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        CommentResponse response = commentService.update(
                1L, 10L, new UpdateCommentRequest("updated", 0L), auth("alice", "DEVELOPER"));

        assertThat(response.content()).isEqualTo("updated");
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void nonAuthorNonAdminGets403OnDelete() {
        Ticket ticket = ticket(1L);
        Comment comment = comment(10L, ticket, author(5L, "alice"), "hi", 0L);
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.delete(1L, 10L, auth("bob", "DEVELOPER")))
                .isInstanceOf(ForbiddenException.class);

        verify(commentRepository, never()).delete(any());
    }

    @Test
    void authorCanDeleteOwnComment() {
        Ticket ticket = ticket(1L);
        Comment comment = comment(10L, ticket, author(5L, "alice"), "hi", 0L);
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        commentService.delete(1L, 10L, auth("alice", "DEVELOPER"));

        verify(commentRepository).delete(comment);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Ticket ticket(Long id) {
        Ticket t = new Ticket();
        t.setId(id);
        return t;
    }

    private User author(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setRole(UserRole.DEVELOPER);
        return u;
    }

    private Comment comment(Long id, Ticket ticket, User author, String content, Long version) {
        Comment c = new Comment();
        c.setId(id);
        c.setTicket(ticket);
        c.setAuthor(author);
        c.setContent(content);
        c.setVersion(version);
        return c;
    }

    private Authentication auth(String username, String role) {
        return new UsernamePasswordAuthenticationToken(
                username, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }
}
