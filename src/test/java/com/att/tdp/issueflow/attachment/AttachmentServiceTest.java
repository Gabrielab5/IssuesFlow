package com.att.tdp.issueflow.attachment;

import com.att.tdp.issueflow.common.exception.FileTooLargeException;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.common.exception.UnsupportedMediaTypeException;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class AttachmentServiceTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private TicketRepository ticketRepository;

    @TempDir
    Path tempDir;

    private AttachmentService service;

    @BeforeEach
    void setUp() {
        service = new AttachmentService(attachmentRepository, ticketRepository, tempDir.toString());
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void findByTicketIdReturnsResponses() {
        when(ticketRepository.existsById(1L)).thenReturn(true);
        Attachment a = attachment(10L, ticket(1L), "doc.pdf", "application/pdf", 42L, "/path/doc.pdf");
        when(attachmentRepository.findByTicketIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(a));

        List<AttachmentResponse> result = service.findByTicketId(1L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().filename()).isEqualTo("doc.pdf");
        assertThat(result.getFirst().contentType()).isEqualTo("application/pdf");
    }

    @Test
    void findByTicketIdThrowsNotFoundForUnknownTicket() {
        when(ticketRepository.existsById(99L)).thenReturn(false);
        assertThatThrownBy(() -> service.findByTicketId(99L)).isInstanceOf(NotFoundException.class);
    }

    // ── upload: happy path ────────────────────────────────────────────────────

    @Test
    void uploadPngStoresFileAndPersistsMetadata() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket(1L)));
        when(attachmentRepository.save(any())).thenAnswer(inv -> {
            Attachment a = inv.getArgument(0);
            a.setId(10L);
            return a;
        });

        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", validPngBytes());
        AttachmentResponse response = service.upload(1L, file);

        assertThat(response.filename()).isEqualTo("photo.png");
        assertThat(response.contentType()).isEqualTo("image/png");
        assertThat(response.sizeBytes()).isEqualTo(validPngBytes().length);
        verify(attachmentRepository).save(any());
    }

    // ── upload: MIME whitelist rejection ──────────────────────────────────────

    @Test
    void uploadRejectsDisallowedMimeType() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket(1L)));

        MockMultipartFile file = new MockMultipartFile(
                "file", "script.js", "application/javascript", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.upload(1L, file))
                .isInstanceOf(UnsupportedMediaTypeException.class);
        verify(attachmentRepository, never()).save(any());
    }

    // ── upload: magic-byte mismatch ───────────────────────────────────────────

    @Test
    void uploadRejectsMagicByteMismatch() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket(1L)));

        // Windows MZ (PE) header claiming to be a PNG
        byte[] exeBytes = {0x4D, 0x5A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "evil.png", "image/png", exeBytes);

        assertThatThrownBy(() -> service.upload(1L, file))
                .isInstanceOf(UnsupportedMediaTypeException.class)
                .hasMessageContaining("image/png");
        verify(attachmentRepository, never()).save(any());
    }

    // ── upload: oversized file ────────────────────────────────────────────────

    @Test
    void uploadRejectsOversizedFile() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket(1L)));

        byte[] big = new byte[10 * 1024 * 1024 + 1]; // 10 MB + 1 byte
        MockMultipartFile file = new MockMultipartFile("file", "huge.txt", "text/plain", big);

        assertThatThrownBy(() -> service.upload(1L, file))
                .isInstanceOf(FileTooLargeException.class);
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void uploadThrowsNotFoundForUnknownTicket() {
        when(ticketRepository.findById(99L)).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "f.txt", "text/plain", new byte[]{1});
        assertThatThrownBy(() -> service.upload(99L, file)).isInstanceOf(NotFoundException.class);
    }

    // ── download ──────────────────────────────────────────────────────────────

    @Test
    void downloadReturnsContentWithMetadata() throws Exception {
        byte[] content = "hello world".getBytes();
        Path stored = Files.write(tempDir.resolve("hello.txt"), content);
        Attachment a = attachment(10L, ticket(1L), "hello.txt", "text/plain",
                (long) content.length, stored.toString());
        when(attachmentRepository.findById(10L)).thenReturn(Optional.of(a));

        DownloadResult result = service.download(1L, 10L);

        assertThat(result.bytes()).isEqualTo(content);
        assertThat(result.contentType()).isEqualTo("text/plain");
        assertThat(result.filename()).isEqualTo("hello.txt");
    }

    @Test
    void downloadThrowsNotFoundForWrongTicket() {
        Attachment a = attachment(10L, ticket(99L), "f.txt", "text/plain", 1L, "/path");
        when(attachmentRepository.findById(10L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.download(1L, 10L)).isInstanceOf(NotFoundException.class);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void deleteRemovesEntityAndFile() throws Exception {
        byte[] content = "data".getBytes();
        Path stored = Files.write(tempDir.resolve("del.txt"), content);
        Attachment a = attachment(10L, ticket(1L), "del.txt", "text/plain",
                (long) content.length, stored.toString());
        when(attachmentRepository.findById(10L)).thenReturn(Optional.of(a));

        service.delete(1L, 10L);

        verify(attachmentRepository).delete(a);
        assertThat(Files.exists(stored)).isFalse();
    }

    // ── filename sanitization ─────────────────────────────────────────────────

    @Test
    void sanitizeFilenameStripsPathTraversals() {
        assertThat(AttachmentService.sanitizeFilename("../../etc/passwd")).isEqualTo("passwd");
        assertThat(AttachmentService.sanitizeFilename("/etc/shadow")).isEqualTo("shadow");
        assertThat(AttachmentService.sanitizeFilename("normal.pdf")).isEqualTo("normal.pdf");
        assertThat(AttachmentService.sanitizeFilename(null)).isEqualTo("unnamed");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static byte[] validPngBytes() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, (byte) 0x1A, 0x0A, // PNG signature
                0x00, 0x00, 0x00, 0x0D  // IHDR length prefix
        };
    }

    private Ticket ticket(Long id) {
        Ticket t = new Ticket();
        t.setId(id);
        return t;
    }

    private Attachment attachment(Long id, Ticket ticket, String filename,
            String contentType, Long sizeBytes, String storagePath) {
        Attachment a = new Attachment();
        a.setId(id);
        a.setTicket(ticket);
        a.setFilename(filename);
        a.setContentType(contentType);
        a.setSizeBytes(sizeBytes);
        a.setStoragePath(storagePath);
        return a;
    }
}
