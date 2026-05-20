package com.att.tdp.issueflow.comment;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CommentControllerIT {

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
    void createAndListComments() throws Exception {
        String token = loginAsAdmin();
        long ticketId = createTicketInNewProject(token);

        MvcResult created = mockMvc.perform(post("/tickets/{id}/comments", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"first comment"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("first comment"))
                .andExpect(jsonPath("$.authorUsername").value("admin"))
                .andExpect(jsonPath("$.ticketId").value(ticketId))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();

        long commentId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/tickets/{id}/comments", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(commentId))
                .andExpect(jsonPath("$[0].content").value("first comment"));
    }

    @Test
    void authorCanUpdateThenDeleteOwnComment() throws Exception {
        String token = loginAsAdmin();
        long ticketId = createTicketInNewProject(token);

        MvcResult created = mockMvc.perform(post("/tickets/{id}/comments", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"original"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        var tree = objectMapper.readTree(created.getResponse().getContentAsString());
        long commentId = tree.get("id").asLong();
        long version = tree.get("version").asLong();

        mockMvc.perform(patch("/tickets/{tid}/comments/{cid}", ticketId, commentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"updated","version":%d}
                                """.formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("updated"));

        mockMvc.perform(delete("/tickets/{tid}/comments/{cid}", ticketId, commentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/tickets/{id}/comments", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void nonAuthorGets403OnUpdate() throws Exception {
        String adminToken = loginAsAdmin();
        long ticketId = createTicketInNewProject(adminToken);

        MvcResult created = mockMvc.perform(post("/tickets/{id}/comments", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"admin wrote this"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        var tree = objectMapper.readTree(created.getResponse().getContentAsString());
        long commentId = tree.get("id").asLong();
        long version = tree.get("version").asLong();

        String devUsername = "dev-" + SEQ.getAndIncrement();
        String devPassword = "devpass123";
        mockMvc.perform(post("/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@test.com","fullName":"Dev User",\
"role":"DEVELOPER","password":"%s"}
                                """.formatted(devUsername, devUsername, devPassword)))
                .andExpect(status().isOk());

        String devToken = loginAndReturnToken(devUsername, devPassword);

        mockMvc.perform(patch("/tickets/{tid}/comments/{cid}", ticketId, commentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + devToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"intruder edit","version":%d}
                                """.formatted(version)))
                .andExpect(status().isForbidden());
    }

    @Test
    void staleVersionReturns409() throws Exception {
        String token = loginAsAdmin();
        long ticketId = createTicketInNewProject(token);

        MvcResult created = mockMvc.perform(post("/tickets/{id}/comments", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"v0"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        var tree = objectMapper.readTree(created.getResponse().getContentAsString());
        long commentId = tree.get("id").asLong();
        long version = tree.get("version").asLong();

        // First update succeeds — DB version increments to version+1
        mockMvc.perform(patch("/tickets/{tid}/comments/{cid}", ticketId, commentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"v1","version":%d}
                                """.formatted(version)))
                .andExpect(status().isOk());

        // Second update with original (stale) version → 409
        mockMvc.perform(patch("/tickets/{tid}/comments/{cid}", ticketId, commentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"stale","version":%d}
                                """.formatted(version)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Resource was modified by another user, please retry"));
    }

    @Test
    void listCommentsOnUnknownTicketReturns404() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/tickets/99999/comments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private long createTicketInNewProject(String token) throws Exception {
        long adminId = getAdminId(token);
        String projectName = "it-proj-" + SEQ.getAndIncrement();

        MvcResult projectResult = mockMvc.perform(post("/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","ownerId":%d}
                                """.formatted(projectName, adminId)))
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
