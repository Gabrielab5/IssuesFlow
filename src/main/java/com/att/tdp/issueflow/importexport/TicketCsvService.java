package com.att.tdp.issueflow.importexport;

import com.att.tdp.issueflow.common.exception.BadRequestException;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.common.exception.ValidationException;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.CreateTicketRequest;
import com.att.tdp.issueflow.ticket.TicketMapper;
import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.ticket.TicketResponse;
import com.att.tdp.issueflow.ticket.TicketService;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.TicketType;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.QuoteMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles CSV export and import for tickets.
 *
 * <p><b>Import transaction model</b>: {@code importCsv} is intentionally NOT {@code @Transactional}.
 * Each call to {@code TicketService.create()} starts and commits its own transaction. This gives
 * <em>partial-success semantics</em>: 10 valid rows in a 12-row CSV produce 10 committed tickets;
 * the 2 failures are reported in {@link ImportResult#errors()} without rolling back the successes.
 * The trade-off is that there is no single rollback point — a truly atomic all-or-nothing import
 * would require wrapping everything in one outer transaction (or an OUTBOX/saga pattern).
 */
@Service
public class TicketCsvService {

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final TicketService ticketService;

    public TicketCsvService(
            TicketRepository ticketRepository,
            ProjectRepository projectRepository,
            TicketService ticketService) {
        this.ticketRepository = ticketRepository;
        this.projectRepository = projectRepository;
        this.ticketService = ticketService;
    }

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Loads and returns all non-deleted tickets for the given project.
     * Throws {@link NotFoundException} (404) if the project does not exist.
     * This method is called synchronously in the controller before setting up
     * the streaming body, so 404 can be returned before any bytes are written.
     */
    @Transactional(readOnly = true)
    public List<TicketResponse> getTicketsForExport(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw NotFoundException.of("Project", projectId);
        }
        return ticketRepository.findByProjectId(projectId)
                .stream().map(TicketMapper::toResponse).toList();
    }

    /**
     * Writes {@code tickets} as CSV to {@code out} using DEFAULT format with
     * {@code QuoteMode.ALL_NON_NULL} — every non-null value is quoted, so commas,
     * double-quotes, and embedded newlines inside field values are safe.
     */
    static void writeCsv(List<TicketResponse> tickets, OutputStream out) {
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        try (CSVPrinter printer = new CSVPrinter(writer, exportFormat())) {
            for (TicketResponse t : tickets) {
                printer.printRecord(
                        t.id(),
                        t.title(),
                        t.description(),
                        t.status().name(),
                        t.priority().name(),
                        t.type().name(),
                        t.assigneeId()
                );
            }
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write CSV", e);
        }
    }

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Parses {@code in} as CSV and creates a ticket per data row via
     * {@link TicketService#create(CreateTicketRequest)}.
     *
     * <ul>
     *   <li>Missing required header column → {@link BadRequestException} (400) for the whole request.</li>
     *   <li>Per-row validation or service failure → added to {@link ImportResult#errors()};
     *       remaining rows are still processed.</li>
     * </ul>
     *
     * <p>Not transactional — see class-level Javadoc for the trade-off explanation.
     */
    public ImportResult importCsv(Long projectId, InputStream in) {
        try (CSVParser parser = importFormat().parse(new InputStreamReader(in, StandardCharsets.UTF_8))) {

            validateHeaders(parser.getHeaderMap());

            int created = 0, failed = 0;
            List<RowError> errors = new ArrayList<>();
            int rowNum = 0;

            for (CSVRecord record : parser) {
                rowNum++;
                try {
                    ticketService.create(buildRequest(record, projectId));
                    created++;
                } catch (Exception e) {
                    failed++;
                    errors.add(new RowError(rowNum, rootMessage(e)));
                }
            }
            return new ImportResult(created, failed, errors);

        } catch (BadRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse CSV", e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static CSVFormat exportFormat() {
        return CSVFormat.DEFAULT.builder()
                .setHeader(CsvColumns.HEADERS)
                .setQuoteMode(QuoteMode.ALL_NON_NULL)
                .build();
    }

    private static CSVFormat importFormat() {
        return CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build();
    }

    private static void validateHeaders(Map<String, Integer> headerMap) {
        for (String required : CsvColumns.REQUIRED) {
            if (!headerMap.containsKey(required.toLowerCase())) {
                throw new BadRequestException("Missing required column in CSV: " + required);
            }
        }
    }

    private static CreateTicketRequest buildRequest(CSVRecord record, Long projectId) {
        String title = get(record, CsvColumns.TITLE);
        if (title == null || title.isBlank()) {
            throw new ValidationException("title must not be blank");
        }

        String status = require(record, CsvColumns.STATUS);
        validateEnum(TicketStatus.class, status, "status");

        String priority = require(record, CsvColumns.PRIORITY);
        validateEnum(TicketPriority.class, priority, "priority");

        String type = require(record, CsvColumns.TYPE);
        validateEnum(TicketType.class, type, "type");

        String description  = get(record, CsvColumns.DESCRIPTION);
        String assigneeStr  = get(record, CsvColumns.ASSIGNEE_ID);
        Long   assigneeId   = (assigneeStr != null && !assigneeStr.isBlank())
                ? Long.parseLong(assigneeStr) : null;

        return new CreateTicketRequest(
                title, description,
                status.toUpperCase(), priority.toUpperCase(), type.toUpperCase(),
                projectId, assigneeId, null);
    }

    private static <E extends Enum<E>> void validateEnum(Class<E> cls, String value, String field) {
        try {
            Enum.valueOf(cls, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid " + field + ": " + value);
        }
    }

    /** Returns trimmed value or {@code null} if blank / column absent. */
    private static String get(CSVRecord record, String column) {
        try {
            String v = record.get(column);
            return (v == null || v.isBlank()) ? null : v.trim();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** Like {@link #get} but throws {@link ValidationException} if value is blank. */
    private static String require(CSVRecord record, String column) {
        String v = get(record, column);
        if (v == null) throw new ValidationException(column + " must not be blank");
        return v;
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) root = root.getCause();
        String msg = root.getMessage();
        return (msg != null && !msg.isBlank()) ? msg : t.getClass().getSimpleName();
    }
}
