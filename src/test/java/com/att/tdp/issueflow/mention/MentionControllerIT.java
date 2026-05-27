package com.att.tdp.issueflow.mention;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@SuppressWarnings({"null", "resource"})
class MentionControllerIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("issueflow.jwt.secret", () -> "test-secret-change-in-production-must-be-at-least-256-bits-long!!");
        registry.add("issueflow.jwt.expiration-minutes", () -> "60");
        registry.add("issueflow.jwt.issuer", () -> "issueflow-test");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final AtomicLong SEQ = new AtomicLong(1);

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void mentionedUserAppearsInCommentResponseAndMentionFeed() throws Exception {
        String adminToken = loginAsAdmin();
        long adminId = getAdminId(adminToken);

        // Create a developer to be mentioned
        String devUsername = "dev-mention-" + SEQ.getAndIncrement();
        String devPassword = "devpass123";
        long devId = createDeveloper(adminToken, devUsername, devPassword);

        long ticketId = createTicketInNewProject(adminToken, adminId);

        // Create comment that mentions the developer (case-insensitive: uppercase username)
        MvcResult commentResult = mockMvc.perform(post("/tickets/{id}/comments", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Hey @%s please review this"}
                                """.formatted(devUsername.toUpperCase())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mentionedUsers[0].username").value(devUsername))
                .andReturn();

        long commentId = objectMapper.readTree(commentResult.getResponse().getContentAsString())
                .get("id").asLong();

        // GET /tickets/{id}/comments → comment includes mentionedUsers
        mockMvc.perform(get("/tickets/{id}/comments", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mentionedUsers[0].username").value(devUsername));

        // GET /users/{devId}/mentions → mention record present
        MvcResult feedResult = mockMvc.perform(get("/users/{id}/mentions", devId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.data[0].commentId").value(commentId))
                .andExpect(jsonPath("$.data[0].ticketId").value(ticketId))
                .andExpect(jsonPath("$.data[0].authorUsername").value("admin"))
                .andReturn();

        JsonNode feed = objectMapper.readTree(feedResult.getResponse().getContentAsString());
        assertThat(feed.get("data").get(0).get("commentContent").asText())
                .contains("@" + devUsername.toUpperCase());
    }

    @Test
    void updateCommentSyncsMentionsDiff() throws Exception {
        String adminToken = loginAsAdmin();
        long adminId = getAdminId(adminToken);

        String devA = "deva-" + SEQ.getAndIncrement();
        String devB = "devb-" + SEQ.getAndIncrement();
        long devAId = createDeveloper(adminToken, devA, "devpass123");
        long devBId = createDeveloper(adminToken, devB, "devpass123");

        long ticketId = createTicketInNewProject(adminToken, adminId);

        // Create comment mentioning devA
        MvcResult created = mockMvc.perform(post("/tickets/{id}/comments", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Hello @%s"}
                                """.formatted(devA)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode tree = objectMapper.readTree(created.getResponse().getContentAsString());
        long commentId = tree.get("id").asLong();
        long version = tree.get("version").asLong();

        // devA has 1 mention, devB has 0
        mockMvc.perform(get("/users/{id}/mentions", devAId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/users/{id}/mentions", devBId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(jsonPath("$.total").value(0));

        // Update comment: remove devA, add devB
        mockMvc.perform(patch("/tickets/{tid}/comments/{cid}", ticketId, commentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Hello @%s","version":%d}
                                """.formatted(devB, version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentionedUsers[0].username").value(devB));

        // devA now has 0 mentions, devB has 1
        mockMvc.perform(get("/users/{id}/mentions", devAId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/users/{id}/mentions", devBId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void unknownMentionedUsernameIsIgnored() throws Exception {
        String adminToken = loginAsAdmin();
        long adminId = getAdminId(adminToken);
        long ticketId = createTicketInNewProject(adminToken, adminId);

        mockMvc.perform(post("/tickets/{id}/comments", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Hey @doesnotexistuser123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mentionedUsers").isEmpty());
    }

    @Test
    void mentionFeedPaginationBoundary() throws Exception {
        String adminToken = loginAsAdmin();
        long adminId = getAdminId(adminToken);

        // Request page beyond total → empty data, correct total
        mockMvc.perform(get("/users/{id}/mentions?page=999&pageSize=5", adminId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(999))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void mentionFeedForUnknownUserReturns404() throws Exception {
        String adminToken = loginAsAdmin();

        mockMvc.perform(get("/users/99999/mentions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private long createDeveloper(String adminToken, String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@test.com","fullName":"%s","role":"DEVELOPER","password":"%s"}
                                """.formatted(username, username, username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createTicketInNewProject(String token, long ownerId) throws Exception {
        String projectName = "it-proj-" + SEQ.getAndIncrement();
        MvcResult projectResult = mockMvc.perform(post("/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","ownerId":%d}
                                """.formatted(projectName, ownerId)))
                .andExpect(status().isCreated())
                .andReturn();
        long projectId = objectMapper.readTree(projectResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult ticketResult = mockMvc.perform(post("/tickets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"ticket-%d","status":"OPEN","priority":"MEDIUM","type":"TASK","projectId":%d}
                                """.formatted(SEQ.getAndIncrement(), projectId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(ticketResult.getResponse().getContentAsString()).get("id").asLong();
    }

    private String loginAsAdmin() throws Exception {
        return loginAndReturnToken("admin", "admin123");
    }

    private String loginAndReturnToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
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
}
