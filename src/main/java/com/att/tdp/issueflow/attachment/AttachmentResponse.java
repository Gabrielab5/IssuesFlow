package com.att.tdp.issueflow.attachment;

import java.time.Instant;

public record AttachmentResponse(
        Long id,
        Long ticketId,
        String filename,
        String contentType,
        long sizeBytes,
        Instant createdAt) {
}
