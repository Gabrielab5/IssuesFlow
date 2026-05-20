package com.att.tdp.issueflow.mention;

import com.att.tdp.issueflow.comment.Comment;
import com.att.tdp.issueflow.comment.CommentMention;
import com.att.tdp.issueflow.common.PagedResponse;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MentionService {

    private final MentionRepository mentionRepository;
    private final UserRepository userRepository;

    public MentionService(MentionRepository mentionRepository, UserRepository userRepository) {
        this.mentionRepository = mentionRepository;
        this.userRepository = userRepository;
    }

    /**
     * Parses @username tokens from content, resolves them to users, and syncs the comment's
     * mention collection: inserts newly added mentions, removes no-longer-present ones.
     * Unknown usernames are silently ignored.
     * Must be called within an open transaction (participates in caller's transaction).
     */
    public void syncMentions(Comment comment, String content) {
        Set<String> parsed = MentionParser.extractUsernames(content); // already lowercase

        // Build set of usernames currently in the collection
        Set<String> existing = comment.getMentions().stream()
                .map(m -> m.getMentionedUser().getUsername().toLowerCase())
                .collect(Collectors.toSet());

        // Remove mentions whose username no longer appears in content
        comment.getMentions().removeIf(m ->
                !parsed.contains(m.getMentionedUser().getUsername().toLowerCase()));

        // Add mentions for newly parsed usernames
        for (String username : parsed) {
            if (!existing.contains(username)) {
                userRepository.findByUsernameIgnoreCaseAndDeletedAtIsNull(username)
                        .ifPresent(user -> {
                            CommentMention mention = new CommentMention();
                            mention.setComment(comment);
                            mention.setMentionedUser(user);
                            comment.getMentions().add(mention);
                        });
            }
        }
    }

    @Transactional(readOnly = true)
    public PagedResponse<MentionResponse> findByUser(Long userId, int page, int pageSize) {
        if (!userRepository.existsById(userId)) {
            throw NotFoundException.of("User", userId);
        }
        Page<CommentMention> result = mentionRepository.findByMentionedUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page - 1, pageSize));
        List<MentionResponse> data = result.getContent().stream()
                .map(MentionMapper::toResponse)
                .toList();
        return new PagedResponse<>(data, result.getTotalElements(), page);
    }
}
