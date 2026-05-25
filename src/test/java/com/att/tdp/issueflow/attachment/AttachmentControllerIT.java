package com.att.tdp.issueflow.attachment;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class AttachmentControllerIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    // Static initializer guarantees the path exists before @DynamicPropertySource runs
    private static final Path STORAGE_DIR;
    static {
        try {
            STORAGE_DIR = Files.createTempDirectory("issueflow-attach-it-");
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

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static final AtomicLong SEQ = new AtomicLong(1);

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void uploadHappyPathReturns201WithMetadata() throws Exception {
        String token = loginAsAdmin();
        long ticketId = createTicket(token);

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", validPngBytes());

        MvcResult result = mockMvc.perform(multipart("/tickets/" + ticketId + "/attachments")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("photo.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.ticketId").value(ticketId))
                .andReturn();

        long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        assertThat(id).isGreaterThan(0);
    }

    @Test
    void listReturnsUploadedAttachments() throws Exception {
        String token = loginAsAdmin();
        long ticketId = createTicket(token);

        mockMvc.perform(multipart("/tickets/" + ticketId + "/attachments")
                        .file(new MockMultipartFile("file", "doc.pdf", "application/pdf", validPdfBytes()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/tickets/" + ticketId + "/attachments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].filename").value("doc.pdf"));
    }

    @Test
    void wrongMimeTypeReturns415() throws Exception {
        String token = loginAsAdmin();
        long ticketId = createTicket(token);

        MockMultipartFile file = new MockMultipartFile(
                "file", "script.js", "application/javascript", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/tickets/" + ticketId + "/attachments")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void magicByteMismatchReturns415() throws Exception {
        String token = loginAsAdmin();
        long ticketId = createTicket(token);

        // Windows MZ (PE) header claiming to be a PNG
        byte[] exeBytes = {0x4D, 0x5A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file", "evil.png", "image/png", exeBytes);

        mockMvc.perform(multipart("/tickets/" + ticketId + "/attachments")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void downloadReturnsBytesWithCorrectHeaders() throws Exception {
        String token = loginAsAdmin();
        long ticketId = createTicket(token);

        byte[] content = "hello attachment".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "readme.txt", "text/plain", content);

        MvcResult uploadResult = mockMvc.perform(multipart("/tickets/" + ticketId + "/attachments")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        long attachmentId = objectMapper
                .readTree(uploadResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/tickets/" + ticketId + "/attachments/" + attachmentId + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith("text/plain")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, endsWith("readme.txt\"")))
                .andExpect(content().bytes(content));
    }

    @Test
    void deleteRemovesAttachmentFromListAndDisk() throws Exception {
        String token = loginAsAdmin();
        long ticketId = createTicket(token);

        MockMultipartFile file = new MockMultipartFile(
                "file", "bye.txt", "text/plain", "bye".getBytes());

        MvcResult uploadResult = mockMvc.perform(multipart("/tickets/" + ticketId + "/attachments")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        long attachmentId = objectMapper
                .readTree(uploadResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/tickets/" + ticketId + "/attachments/" + attachmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/tickets/" + ticketId + "/attachments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // file should be gone from disk (storagePath not exposed in response, so check directory)
        long remaining = Files.walk(STORAGE_DIR)
                .filter(p -> p.getFileName().toString().endsWith("bye.txt"))
                .count();
        assertThat(remaining).isZero();
    }

    @Test
    void unknownTicketReturns404() throws Exception {
        String token = loginAsAdmin();
        MockMultipartFile file = new MockMultipartFile(
                "file", "f.txt", "text/plain", "x".getBytes());

        mockMvc.perform(multipart("/tickets/999999/attachments")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String loginAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private long getAdminId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    private long createTicket(String token) throws Exception {
        long adminId = getAdminId(token);

        MvcResult projectResult = mockMvc.perform(post("/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Att-Project-%d","ownerId":%d}
                                """.formatted(SEQ.getAndIncrement(), adminId)))
                .andExpect(status().isCreated())
                .andReturn();
        long projectId = objectMapper.readTree(projectResult.getResponse().getContentAsString())
                .get("id").asLong();

        MvcResult ticketResult = mockMvc.perform(post("/tickets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Ticket-%d","status":"TODO","priority":"LOW","type":"BUG","projectId":%d}
                                """.formatted(SEQ.getAndIncrement(), projectId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(ticketResult.getResponse().getContentAsString())
                .get("id").asLong();
    }

    private static byte[] validPngBytes() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, (byte) 0x1A, 0x0A, // PNG signature
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52               // IHDR chunk start
        };
    }

    private static byte[] validPdfBytes() {
        // %PDF-1.4 header
        return new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};
    }
}
