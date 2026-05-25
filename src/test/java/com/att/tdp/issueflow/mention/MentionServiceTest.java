package com.att.tdp.issueflow.mention;

import com.att.tdp.issueflow.comment.Comment;
import com.att.tdp.issueflow.comment.CommentMention;
import com.att.tdp.issueflow.common.PagedResponse;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import com.att.tdp.issueflow.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentionServiceTest {

    @Mock
    private MentionRepository mentionRepository;

    @Mock
    private UserRepository userRepository;

    private MentionService mentionService;

    @BeforeEach
    void setUp() {
        mentionService = new MentionService(mentionRepository, userRepository);
    }

    // ── syncMentions ──────────────────────────────────────────────────────────

    @Test
    void syncMentionsAddsResolvedUsersToCollection() {
        Comment comment = commentWithNoMentions();
        User alice = user(1L, "alice");
        when(userRepository.findByUsernameIgnoreCaseAndDeletedAtIsNull("alice"))
                .thenReturn(Optional.of(alice));

        mentionService.syncMentions(comment, "Hey @alice please review");

        assertThat(comment.getMentions()).hasSize(1);
        assertThat(comment.getMentions().getFirst().getMentionedUser().getUsername()).isEqualTo("alice");
    }

    @Test
    void syncMentionsCaseInsensitiveResolution() {
        Comment comment = commentWithNoMentions();
        User bob = user(2L, "bob");
        // token is @BOB in uppercase, resolution must match lowercase "bob"
        when(userRepository.findByUsernameIgnoreCaseAndDeletedAtIsNull("bob"))
                .thenReturn(Optional.of(bob));

        mentionService.syncMentions(comment, "Hey @BOB and @Bob, same person");

        // deduplication: two @BOB/@Bob tokens → only one mention
        assertThat(comment.getMentions()).hasSize(1);
    }

    @Test
    void syncMentionsUnknownUsernameIsIgnoredSilently() {
        Comment comment = commentWithNoMentions();
        when(userRepository.findByUsernameIgnoreCaseAndDeletedAtIsNull("nobody"))
                .thenReturn(Optional.empty());

        mentionService.syncMentions(comment, "Hello @nobody");

        assertThat(comment.getMentions()).isEmpty();
    }

    @Test
    void syncMentionsRemovesDroppedMentionsOnUpdate() {
        User alice = user(1L, "alice");
        User bob = user(2L, "bob");
        Comment comment = commentWithMentions(List.of(alice, bob));
        // new content only mentions alice; bob should be removed
        // alice is already in the existing set — no lookup needed

        mentionService.syncMentions(comment, "Only @alice now");

        assertThat(comment.getMentions()).hasSize(1);
        assertThat(comment.getMentions().getFirst().getMentionedUser().getUsername()).isEqualTo("alice");
    }

    @Test
    void syncMentionsReAddsMentionAfterItWasRemoved() {
        User alice = user(1L, "alice");
        // Comment currently has alice
        Comment comment = commentWithMentions(List.of(alice));
        // First update removes alice
        mentionService.syncMentions(comment, "No mentions");
        assertThat(comment.getMentions()).isEmpty();

        // Second update re-adds alice
        when(userRepository.findByUsernameIgnoreCaseAndDeletedAtIsNull("alice"))
                .thenReturn(Optional.of(alice));
        mentionService.syncMentions(comment, "Back @alice");
        assertThat(comment.getMentions()).hasSize(1);
    }

    @Test
    void syncMentionsDoesNotDuplicateExistingMention() {
        User alice = user(1L, "alice");
        Comment comment = commentWithMentions(List.of(alice));

        // alice is already in the collection; content still mentions her → no second entry
        // no stub needed — userRepository should never be called for already-existing mentions

        mentionService.syncMentions(comment, "Still talking to @alice");

        // findByUsernameIgnoreCase was NOT called for alice because she was already in existing set
        verify(userRepository, never()).findByUsernameIgnoreCaseAndDeletedAtIsNull("alice");
        assertThat(comment.getMentions()).hasSize(1);
    }

    // ── findByUser ────────────────────────────────────────────────────────────

    @Test
    void findByUserReturnsPaginatedMentions() {
        User alice = user(1L, "alice");
        Ticket ticket = ticket(10L);
        Comment comment = commentInTicket(5L, ticket, alice);
        CommentMention mention = mention(100L, comment, alice);

        when(userRepository.existsById(1L)).thenReturn(true);
        when(mentionRepository.findByMentionedUserIdOrderByCreatedAtDesc(
                eq(1L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(mention), PageRequest.of(0, 20), 1));

        PagedResponse<MentionResponse> response = mentionService.findByUser(1L, 1, 20);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().getFirst().authorUsername()).isEqualTo("alice");
        assertThat(response.data().getFirst().ticketId()).isEqualTo(10L);
        assertThat(response.data().getFirst().commentId()).isEqualTo(5L);
    }

    @Test
    void findByUserThrowsNotFoundForUnknownUser() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> mentionService.findByUser(99L, 1, 20))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findByUserRespectsPageBoundary() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(mentionRepository.findByMentionedUserIdOrderByCreatedAtDesc(
                eq(1L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(99, 20), 5));

        PagedResponse<MentionResponse> response = mentionService.findByUser(1L, 100, 20);

        assertThat(response.data()).isEmpty();
        assertThat(response.total()).isEqualTo(5);
        assertThat(response.page()).isEqualTo(100);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private User user(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setRole(UserRole.DEVELOPER);
        return u;
    }

    private Ticket ticket(Long id) {
        Ticket t = new Ticket();
        t.setId(id);
        return t;
    }

    private Comment commentWithNoMentions() {
        Comment c = new Comment();
        c.setId(1L);
        c.setTicket(ticket(1L));
        c.setAuthor(user(99L, "owner"));
        c.setContent("some content");
        return c;
    }

    private Comment commentWithMentions(List<User> users) {
        Comment c = commentWithNoMentions();
        for (User u : users) {
            CommentMention m = new CommentMention();
            m.setComment(c);
            m.setMentionedUser(u);
            c.getMentions().add(m);
        }
        return c;
    }

    private Comment commentInTicket(Long commentId, Ticket ticket, User author) {
        Comment c = new Comment();
        c.setId(commentId);
        c.setTicket(ticket);
        c.setAuthor(author);
        c.setContent("comment content");
        return c;
    }

    private CommentMention mention(Long id, Comment comment, User mentionedUser) {
        CommentMention m = new CommentMention();
        m.setId(id);
        m.setComment(comment);
        m.setMentionedUser(mentionedUser);
        return m;
    }
}
