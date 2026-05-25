package com.att.tdp.issueflow.importexport;

import com.att.tdp.issueflow.ticket.TicketResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * CSV bulk export and import for tickets.
 *
 * <p>Export: {@code GET /tickets/export?projectId=} — streams text/csv.
 * <p>Import: {@code POST /tickets/import} (multipart; parts: {@code file} + {@code projectId}).
 *   Each row runs through {@link TicketCsvService#importCsv} in its own transaction so partial
 *   success is preserved — see {@link TicketCsvService} for the trade-off documentation.
 */
@RestController
@RequestMapping("/tickets")
public class TicketCsvController {

    private final TicketCsvService ticketCsvService;

    public TicketCsvController(TicketCsvService ticketCsvService) {
        this.ticketCsvService = ticketCsvService;
    }

    /**
     * Exports all tickets for a project as a CSV download.
     * Validation (project existence) happens before streaming begins,
     * so a 404 can still be returned if the project is not found.
     */
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(@RequestParam Long projectId) {
        // Load and validate eagerly — throws NotFoundException before any bytes are written
        List<TicketResponse> tickets = ticketCsvService.getTicketsForExport(projectId);

        String date     = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String filename = "tickets-" + projectId + "-" + date + ".csv";

        StreamingResponseBody body = out -> TicketCsvService.writeCsv(tickets, out);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    /**
     * Imports tickets from a CSV file into the specified project.
     * Missing required header column → 400 for the whole request.
     * Per-row errors are collected and returned in the result body without aborting the batch.
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importCsv(
            @RequestPart("file") MultipartFile file,
            @RequestParam Long projectId) throws IOException {
        return ticketCsvService.importCsv(projectId, file.getInputStream());
    }
}
