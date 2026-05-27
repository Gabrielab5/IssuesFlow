package com.att.tdp.issueflow.mention;

import com.att.tdp.issueflow.common.PagedResponse;

import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/{userId}/mentions")
public class MentionController {

    private final MentionService mentionService;

    public MentionController(MentionService mentionService) {
        this.mentionService = mentionService;
    }

    @GetMapping
    public PagedResponse<MentionResponse> findByUser(
            @PathVariable @NonNull Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return mentionService.findByUser(userId, page, pageSize);
    }
}
