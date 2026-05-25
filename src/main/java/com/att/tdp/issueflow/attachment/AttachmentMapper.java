package com.att.tdp.issueflow.attachment;

final class AttachmentMapper {

    private AttachmentMapper() {}

    static AttachmentResponse toResponse(Attachment a) {
        return new AttachmentResponse(
                a.getId(),
                a.getTicket().getId(),
                a.getFilename(),
                a.getContentType(),
                a.getSizeBytes(),
                a.getCreatedAt());
    }
}
