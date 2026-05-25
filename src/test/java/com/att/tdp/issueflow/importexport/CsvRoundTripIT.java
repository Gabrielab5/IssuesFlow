package com.att.tdp.issueflow.importexport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CsvRoundTripIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Path STORAGE_DIR;
    static {
        try {
            STORAGE_DIR = Files.createTempDirectory("issueflow-csv-it-");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("issueflow.jwt.secret",
                () -> "test-secret-change-in-production-must-be-at-least-256-bits-long!!");
        registry.add("issueflow.jwt.expiration-minutes", () -> "60");
        registry.add("issueflow.jwt.issuer", () -> "issueflow-test");
        registry.add("issueflow.storage.path", STORAGE_DIR::toString);
    }

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;

    private static final AtomicLong SEQ = new AtomicLong(1);

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void roundTrip_exportThenImportProducesEquivalentTickets() throws Exception {
        String token      = loginAsAdmin();
        long   adminId    = getAdminId(token);
        long   projectId  = createProject(token, adminId);

        // Create 3 source tickets with distinct attributes
        createTicket(token, projectId, "Alpha bug", "desc-a", "TODO",    "HIGH",     "BUG");
        createTicket(token, projectId, "Beta feat", "desc-b", "IN_PROGRESS", "LOW", "FEATURE");
        createTicket(token, projectId, "Gamma tech","desc-c", "DONE",    "CRITICAL","TECHNICAL");

        // Export
        MvcResult export = mockMvc.perform(get("/tickets/export")
                        .param("projectId", String.valueOf(projectId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("tickets-" + projectId + "-")))
                .andReturn();

        byte[] csvBytes = export.getResponse().getContentAsByteArray();
        String csvText  = new String(csvBytes, StandardCharsets.UTF_8);

        assertThat(csvText).contains("\"id\",\"title\",\"description\",\"status\",\"priority\",\"type\",\"assigneeId\"");
        assertThat(csvText).contains("Alpha bug");
        assertThat(csvText).contains("Beta feat");

        // Import into a second project
        long targetProject = createProject(token, adminId);
        MvcResult importResult = doImport(token, csvBytes, targetProject);

        JsonNode body = objectMapper.readTree(importResult.getResponse().getContentAsString());
        assertThat(body.get("created").asInt()).isEqualTo(3);
        assertThat(body.get("failed").asInt()).isZero();
        assertThat(body.get("errors").size()).isZero();
    }

    @Test
    void exportPreservesCommasAndQuotesInFields() throws Exception {
        String token     = loginAsAdmin();
        long   adminId   = getAdminId(token);
        long   projectId = createProject(token, adminId);

        String specialTitle = "Fix \"quote\" and, comma issue";
        String specialDesc  = "Line 1\nLine 2";
        createTicket(token, projectId, specialTitle, specialDesc, "TODO", "LOW", "BUG");

        MvcResult export = mockMvc.perform(get("/tickets/export")
                        .param("projectId", String.valueOf(projectId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String csv = export.getResponse().getContentAsString(StandardCharsets.UTF_8);
        // Commons CSV with ALL_NON_NULL: double-quote inside quoted field → ""
        assertThat(csv).contains("Fix \"\"quote\"\" and, comma issue");

        // Re-import: title and description should survive the round-trip
        long target = createProject(token, adminId);
        MvcResult importResult = doImport(token,
                export.getResponse().getContentAsByteArray(), target);

        JsonNode body = objectMapper.readTree(importResult.getResponse().getContentAsString());
        assertThat(body.get("created").asInt()).isEqualTo(1);
        assertThat(body.get("failed").asInt()).isZero();
    }

    @Test
    void importPartialSuccess_validRowsCreatedDespiteOneBadRow() throws Exception {
        String token     = loginAsAdmin();
        long   adminId   = getAdminId(token);
        long   projectId = createProject(token, adminId);

        // Row 2: valid. Row 3: bad status. Row 4: valid.
        String csv = "title,description,status,priority,type,assigneeId\n"
                   + "Good Ticket One,,TODO,HIGH,BUG,\n"
                   + "Bad Ticket,,NOT_A_STATUS,LOW,BUG,\n"
                   + "Good Ticket Two,,DONE,LOW,FEATURE,\n";

        MvcResult result = doImport(token, csv.getBytes(StandardCharsets.UTF_8), projectId);

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("created").asInt()).isEqualTo(2);
        assertThat(body.get("failed").asInt()).isEqualTo(1);

        JsonNode errors = body.get("errors");
        assertThat(errors.size()).isEqualTo(1);
        assertThat(errors.get(0).get("row").asInt()).isEqualTo(2);
        assertThat(errors.get(0).get("reason").asText()).containsIgnoringCase("status");
    }

    @Test
    void importMissingRequiredColumnReturns400ForWholeRequest() throws Exception {
        String token     = loginAsAdmin();
        long   adminId   = getAdminId(token);
        long   projectId = createProject(token, adminId);

        // "priority" column is missing
        String csv = "title,status,type\nFoo,TODO,BUG\n";

        mockMvc.perform(multipart("/tickets/import")
                        .file(csvPart(csv.getBytes(StandardCharsets.UTF_8)))
                        .param("projectId", String.valueOf(projectId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("priority")));
    }

    @Test
    void exportUnknownProjectReturns404() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/tickets/export")
                        .param("projectId", "999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void exportContentType_isTextCsv() throws Exception {
        String token     = loginAsAdmin();
        long   adminId   = getAdminId(token);
        long   projectId = createProject(token, adminId);

        mockMvc.perform(get("/tickets/export")
                        .param("projectId", String.valueOf(projectId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv")));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String loginAsAdmin() throws Exception {
        MvcResult r = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private long getAdminId(String token) throws Exception {
        MvcResult r = mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createProject(String token, long ownerId) throws Exception {
        MvcResult r = mockMvc.perform(post("/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"CSV-Project-%d","ownerId":%d}
                                """.formatted(SEQ.getAndIncrement(), ownerId)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private void createTicket(String token, long projectId, String title, String description,
            String status, String priority, String type) throws Exception {
        mockMvc.perform(post("/tickets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","description":"%s","status":"%s",
                                 "priority":"%s","type":"%s","projectId":%d}
                                """.formatted(title, description, status, priority, type, projectId)))
                .andExpect(status().isCreated());
    }

    private MvcResult doImport(String token, byte[] csvBytes, long projectId) throws Exception {
        return mockMvc.perform(multipart("/tickets/import")
                        .file(csvPart(csvBytes))
                        .param("projectId", String.valueOf(projectId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static MockMultipartFile csvPart(byte[] bytes) {
        return new MockMultipartFile("file", "tickets.csv", "text/csv", bytes);
    }
}
