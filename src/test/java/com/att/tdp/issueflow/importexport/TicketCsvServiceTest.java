package com.att.tdp.issueflow.importexport;

import com.att.tdp.issueflow.common.exception.BadRequestException;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.CreateTicketRequest;
import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.ticket.TicketResponse;
import com.att.tdp.issueflow.ticket.TicketService;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.TicketType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketCsvServiceTest {

    @Mock private TicketRepository  ticketRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private TicketService     ticketService;

    private TicketCsvService service;

    @BeforeEach
    void setUp() {
        service = new TicketCsvService(ticketRepository, projectRepository, ticketService);
    }

    // ── export: getTicketsForExport ───────────────────────────────────────────

    @Test
    void getTicketsForExportReturnsListForKnownProject() {
        when(projectRepository.existsById(1L)).thenReturn(true);
        when(ticketRepository.findByProjectId(1L)).thenReturn(List.of());

        List<TicketResponse> result = service.getTicketsForExport(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void getTicketsForExportThrowsNotFoundForUnknownProject() {
        when(projectRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.getTicketsForExport(99L))
                .isInstanceOf(NotFoundException.class);
    }

    // ── export: writeCsv ──────────────────────────────────────────────────────

    @Test
    void writeCsvProducesHeaderAndDataRow() {
        List<TicketResponse> tickets = List.of(response(
                1L, "My ticket", "desc", TicketStatus.TODO, TicketPriority.HIGH, TicketType.BUG, 5L));

        String csv = toCsvString(tickets);

        assertThat(csv).contains("\"id\",\"title\",\"description\",\"status\",\"priority\",\"type\",\"assigneeId\"");
        assertThat(csv).contains("\"1\"");
        assertThat(csv).contains("\"My ticket\"");
        assertThat(csv).contains("\"TODO\"");
        assertThat(csv).contains("\"HIGH\"");
        assertThat(csv).contains("\"BUG\"");
        assertThat(csv).contains("\"5\"");
    }

    @Test
    void writeCsvProperlyQuotesCommasQuotesAndNewlines() {
        List<TicketResponse> tickets = List.of(response(
                2L, "Title, with comma", "line1\nline2", TicketStatus.TODO,
                TicketPriority.LOW, TicketType.FEATURE, null));

        String csv = toCsvString(tickets);

        // Title with comma must be quoted
        assertThat(csv).contains("\"Title, with comma\"");
        // Newline inside description must be inside a quoted field
        assertThat(csv).contains("\"line1\nline2\"");
        // Null assigneeId → empty (not quoted, since null is excluded by ALL_NON_NULL)
        assertThat(csv).doesNotContain("\"null\"");
    }

    @Test
    void writeCsvEscapesEmbeddedDoubleQuotes() {
        List<TicketResponse> tickets = List.of(response(
                3L, "Say \"hello\"", null, TicketStatus.TODO,
                TicketPriority.LOW, TicketType.FEATURE, null));

        String csv = toCsvString(tickets);

        // CSV standard: " inside quoted field → ""
        assertThat(csv).contains("\"Say \"\"hello\"\"\"");
    }

    // ── import: happy path ────────────────────────────────────────────────────

    @Test
    void importCsvCreatesTicketForEachValidRow() {
        when(ticketService.create(any())).thenReturn(stubResponse());

        String csv = "title,description,status,priority,type,assigneeId\n"
                   + "Alpha,desc,TODO,HIGH,BUG,\n"
                   + "Beta,,IN_PROGRESS,LOW,FEATURE,\n";

        ImportResult result = service.importCsv(10L, toStream(csv));

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        assertThat(result.errors()).isEmpty();
        verify(ticketService, times(2)).create(any(CreateTicketRequest.class));
    }

    @Test
    void importCsvPassesCorrectProjectIdToCreate() {
        when(ticketService.create(any())).thenReturn(stubResponse());

        String csv = "title,status,priority,type\nFix me,TODO,HIGH,BUG\n";
        service.importCsv(42L, toStream(csv));

        ArgumentCaptor<CreateTicketRequest> captor = ArgumentCaptor.forClass(CreateTicketRequest.class);
        verify(ticketService).create(captor.capture());
        assertThat(captor.getValue().projectId()).isEqualTo(42L);
    }

    // ── import: per-row errors ────────────────────────────────────────────────

    @Test
    void importCsvCapturesRowErrorForBadStatusWithoutAbortingBatch() {
        when(ticketService.create(any())).thenReturn(stubResponse());

        String csv = "title,status,priority,type\n"
                   + "Good,TODO,HIGH,BUG\n"
                   + "Bad,INVALID_STATUS,LOW,FEATURE\n"
                   + "AlsoGood,DONE,LOW,TECHNICAL\n";

        ImportResult result = service.importCsv(1L, toStream(csv));

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().getFirst().row()).isEqualTo(2);
        assertThat(result.errors().getFirst().reason()).containsIgnoringCase("status");
    }

    @Test
    void importCsvCapturesRowErrorForBlankTitle() {
        String csv = "title,status,priority,type\n"
                   + "   ,TODO,HIGH,BUG\n";

        ImportResult result = service.importCsv(1L, toStream(csv));

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errors().getFirst().reason()).containsIgnoringCase("title");
        verify(ticketService, never()).create(any());
    }

    // ── import: missing required column ──────────────────────────────────────

    @Test
    void importCsvThrowsBadRequestWhenRequiredColumnMissing() {
        // "priority" column is absent
        String csv = "title,status,type\nFoo,TODO,BUG\n";

        assertThatThrownBy(() -> service.importCsv(1L, toStream(csv)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("priority");

        verify(ticketService, never()).create(any());
    }

    @Test
    void importCsvIsCaseInsensitiveForHeaders() {
        when(ticketService.create(any())).thenReturn(stubResponse());

        // Headers with mixed case
        String csv = "TITLE,STATUS,PRIORITY,TYPE\nFoo,TODO,LOW,BUG\n";

        ImportResult result = service.importCsv(1L, toStream(csv));

        assertThat(result.created()).isEqualTo(1);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String toCsvString(List<TicketResponse> tickets) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TicketCsvService.writeCsv(tickets, out);
        return out.toString(StandardCharsets.UTF_8);
    }

    private static ByteArrayInputStream toStream(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }

    private static TicketResponse response(Long id, String title, String description,
            TicketStatus status, TicketPriority priority, TicketType type, Long assigneeId) {
        return new TicketResponse(id, title, description, status, priority, type,
                1L, assigneeId, null, null, false, 0L, Instant.now(), Instant.now());
    }

    private static TicketResponse stubResponse() {
        return response(99L, "stub", null, TicketStatus.TODO, TicketPriority.LOW, TicketType.BUG, null);
    }
}
