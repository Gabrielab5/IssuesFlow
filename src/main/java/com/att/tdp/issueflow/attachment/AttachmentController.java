package com.att.tdp.issueflow.attachment;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;

/**
 * Attachment endpoints:
 *   POST   /tickets/{ticketId}/attachments          — upload (multipart/form-data, part name "file")
 *   GET    /tickets/{ticketId}/attachments          — list metadata
 *   GET    /tickets/{ticketId}/attachments/{id}/download — stream file bytes
 *   DELETE /tickets/{ticketId}/attachments/{id}     — remove
 */
@RestController
@RequestMapping("/tickets/{ticketId}/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping
    public List<AttachmentResponse> list(@PathVariable @NonNull Long ticketId) {
        return attachmentService.findByTicketId(ticketId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> upload(
            @PathVariable @NonNull Long ticketId,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attachmentService.upload(ticketId, file));
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable @NonNull Long ticketId,
            @PathVariable @NonNull Long attachmentId) {
        DownloadResult result = attachmentService.download(ticketId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(Objects.requireNonNull(result.contentType())))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(result.filename())
                                .build()
                                .toString())
                .body(result.bytes());
    }

    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> delete(
            @PathVariable @NonNull Long ticketId,
            @PathVariable @NonNull Long attachmentId) {
        attachmentService.delete(ticketId, attachmentId);
        return ResponseEntity.noContent().build();
    }
}
