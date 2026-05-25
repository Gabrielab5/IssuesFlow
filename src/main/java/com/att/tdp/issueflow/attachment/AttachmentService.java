package com.att.tdp.issueflow.attachment;

import com.att.tdp.issueflow.common.annotation.Audited;
import com.att.tdp.issueflow.common.exception.FileTooLargeException;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class AttachmentService {

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024; // 10 MB

    private final AttachmentRepository attachmentRepository;
    private final TicketRepository ticketRepository;
    private final Path storageRoot;

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            TicketRepository ticketRepository,
            @Value("${issueflow.storage.path:./var/attachments}") String storagePath) {
        this.attachmentRepository = attachmentRepository;
        this.ticketRepository = ticketRepository;
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> findByTicketId(Long ticketId) {
        if (!ticketRepository.existsById(ticketId)) {
            throw NotFoundException.of("Ticket", ticketId);
        }
        return attachmentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)
                .stream().map(AttachmentMapper::toResponse).toList();
    }

    @Audited(action = "CREATE", entityType = "Attachment")
    @Transactional
    public AttachmentResponse upload(Long ticketId, MultipartFile file) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> NotFoundException.of("Ticket", ticketId));

        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new FileTooLargeException("File size exceeds the 10 MB limit");
        }

        String rawType = file.getContentType();
        String claimedType = rawType != null ? rawType.split(";")[0].trim().toLowerCase() : "";

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }

        MimeValidator.validate(bytes, claimedType);

        String sanitized = sanitizeFilename(file.getOriginalFilename());
        String storedName = UUID.randomUUID() + "-" + sanitized;
        Path ticketDir = storageRoot.resolve(String.valueOf(ticketId));
        Path target = ticketDir.resolve(storedName);

        try {
            Files.createDirectories(ticketDir);
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store attachment", e);
        }

        Attachment attachment = new Attachment();
        attachment.setTicket(ticket);
        attachment.setFilename(sanitized);
        attachment.setContentType(claimedType);
        attachment.setSizeBytes((long) bytes.length);
        attachment.setStoragePath(target.toString());

        return AttachmentMapper.toResponse(attachmentRepository.save(attachment));
    }

    @Transactional(readOnly = true)
    public DownloadResult download(Long ticketId, Long attachmentId) {
        Attachment attachment = findAttachment(ticketId, attachmentId);
        try {
            byte[] bytes = Files.readAllBytes(Path.of(attachment.getStoragePath()));
            return new DownloadResult(attachment.getFilename(), attachment.getContentType(), bytes);
        } catch (IOException e) {
            throw NotFoundException.of("Attachment file", attachmentId);
        }
    }

    @Audited(action = "DELETE", entityType = "Attachment", idExpression = "#attachmentId")
    @Transactional
    public void delete(Long ticketId, Long attachmentId) {
        Attachment attachment = findAttachment(ticketId, attachmentId);
        Path stored = Path.of(attachment.getStoragePath());
        attachmentRepository.delete(attachment);
        try {
            Files.deleteIfExists(stored);
        } catch (IOException ignored) {
            // file already gone from storage; metadata is removed — acceptable
        }
    }

    private Attachment findAttachment(Long ticketId, Long attachmentId) {
        Attachment a = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> NotFoundException.of("Attachment", attachmentId));
        if (!a.getTicket().getId().equals(ticketId)) {
            throw NotFoundException.of("Attachment", attachmentId);
        }
        return a;
    }

    static String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) return "unnamed";
        // Strip any path prefix the client may have sent
        String name = Path.of(original).getFileName().toString();
        // Remove characters that are dangerous in filenames
        name = name.replaceAll("[/\\\\:*?\"<>|\\x00-\\x1f]", "_");
        return name.length() > 255 ? name.substring(0, 255) : name;
    }
}
