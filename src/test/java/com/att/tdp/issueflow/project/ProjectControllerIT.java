package com.att.tdp.issueflow.project;

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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@SuppressWarnings({"null", "resource"})
class ProjectControllerIT {

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

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    void adminCanCompleteProjectCrudRoundTrip() throws Exception {
        String token = loginAsAdmin();
        long ownerId = getAdminId(token);
        String name = "proj-crud-" + SEQ.getAndIncrement();

        // Create → 201
        MvcResult created = mockMvc.perform(post("/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"initial desc","ownerId":%d}
                                """.formatted(name, ownerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.description").value("initial desc"))
                .andExpect(jsonPath("$.ownerId").value(ownerId))
                .andReturn();

        long projectId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // Get by id → 200
        mockMvc.perform(get("/projects/{id}", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId));

        // List → project present
        mockMvc.perform(get("/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem(name)));

        // Patch name only → description unchanged
        String updatedName = "updated-" + name;
        mockMvc.perform(patch("/projects/{id}", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(updatedName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(updatedName))
                .andExpect(jsonPath("$.description").value("initial desc"));

        // Delete → 204
        mockMvc.perform(delete("/projects/{id}", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void softDeleteHidesProjectFromListAndGetButAppearsInDeleted() throws Exception {
        String token = loginAsAdmin();
        long ownerId = getAdminId(token);
        String name = "proj-soft-" + SEQ.getAndIncrement();

        MvcResult created = mockMvc.perform(post("/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","ownerId":%d}
                                """.formatted(name, ownerId)))
                .andExpect(status().isCreated())
                .andReturn();

        long projectId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // Soft-delete
        mockMvc.perform(delete("/projects/{id}", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        // GET /projects → deleted project absent
        mockMvc.perform(get("/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", not(hasItem(name))));

        // GET /projects/{id} → 404
        mockMvc.perform(get("/projects/{id}", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        // GET /projects/deleted → deleted project present
        mockMvc.perform(get("/projects/deleted")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem(name)));
    }

    @Test
    void adminCanRestoreProjectAfterSoftDelete() throws Exception {
        String token = loginAsAdmin();
        long ownerId = getAdminId(token);
        String name = "proj-restore-" + SEQ.getAndIncrement();

        MvcResult created = mockMvc.perform(post("/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","ownerId":%d}
                                """.formatted(name, ownerId)))
                .andExpect(status().isCreated())
                .andReturn();

        long projectId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // Delete
        mockMvc.perform(delete("/projects/{id}", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        // Confirm hidden
        mockMvc.perform(get("/projects/{id}", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        // Restore → 200 with project data
        mockMvc.perform(post("/projects/{id}/restore", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId))
                .andExpect(jsonPath("$.name").value(name));

        // Visible again
        mockMvc.perform(get("/projects/{id}", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name));

        // Not in deleted list
        mockMvc.perform(get("/projects/deleted")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", not(hasItem(name))));
    }

    @Test
    void developerForbiddenOnDeletedAndRestoreEndpoints() throws Exception {
        String adminToken = loginAsAdmin();
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

        // GET /projects/deleted → 403
        mockMvc.perform(get("/projects/deleted")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + devToken))
                .andExpect(status().isForbidden());

        // POST /projects/{id}/restore → 403 (security check fires before method body)
        mockMvc.perform(post("/projects/9999/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + devToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void createProjectWithUnknownOwnerReturns404() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(post("/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"orphan-proj","ownerId":99999}
                                """))
                .andExpect(status().isNotFound());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

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
