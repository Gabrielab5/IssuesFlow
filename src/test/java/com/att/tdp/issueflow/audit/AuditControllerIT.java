package com.att.tdp.issueflow.audit;

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
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuditControllerIT {

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

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static final AtomicLong SEQ = new AtomicLong(1);

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void nonAdminGets403() throws Exception {
        String adminToken = loginAsAdmin();
        String devUsername = "dev-audit-" + SEQ.getAndIncrement();
        createDeveloper(adminToken, devUsername);
        String devToken = loginAndReturnToken(devUsername, "devpass123");

        mockMvc.perform(get("/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + devToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListAllAuditLogs() throws Exception {
        String token = loginAsAdmin();

        // Trigger a CREATE audit row
        String username = "audit-list-" + SEQ.getAndIncrement();
        createDeveloper(token, username);

        mockMvc.perform(get("/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(greaterThan(0)))
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void filterByEntityType() throws Exception {
        String token = loginAsAdmin();
        String username = "audit-et-" + SEQ.getAndIncrement();
        createDeveloper(token, username);

        mockMvc.perform(get("/audit-logs?entityType=User")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(greaterThan(0)))
                .andExpect(jsonPath("$.data[0].entityType").value("User"));
    }

    @Test
    void filterByEntityId() throws Exception {
        String token = loginAsAdmin();
        String username = "audit-eid-" + SEQ.getAndIncrement();
        long userId = createDeveloper(token, username);

        mockMvc.perform(get("/audit-logs?entityType=User&entityId=" + userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].entityId").value(userId));
    }

    @Test
    void filterByAction() throws Exception {
        String token = loginAsAdmin();
        createDeveloper(token, "audit-action-" + SEQ.getAndIncrement());

        mockMvc.perform(get("/audit-logs?action=CREATE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(greaterThan(0)))
                .andExpect(jsonPath("$.data[0].action").value("CREATE"));
    }

    @Test
    void filterByActor() throws Exception {
        String token = loginAsAdmin();

        // USER actor rows exist from any admin-triggered service call
        mockMvc.perform(get("/audit-logs?actor=USER")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(greaterThan(0)))
                .andExpect(jsonPath("$.data[0].actor").value("USER"));
    }

    @Test
    void filterByPerformedBy() throws Exception {
        String token = loginAsAdmin();
        long adminId = getAdminId(token);

        createDeveloper(token, "audit-pb-" + SEQ.getAndIncrement());

        mockMvc.perform(get("/audit-logs?performedBy=" + adminId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(greaterThan(0)))
                .andExpect(jsonPath("$.data[0].performedBy").value(adminId));
    }

    @Test
    void filterByFromAndToReturnsOnlyRowsInRange() throws Exception {
        String token = loginAsAdmin();

        // All rows we just produced fall within "now ± 1 minute"
        String from = java.time.Instant.now().minusSeconds(60).toString();
        String to = java.time.Instant.now().plusSeconds(60).toString();

        mockMvc.perform(get("/audit-logs?from=" + from + "&to=" + to)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(greaterThan(0)));

        // A range in the distant past should yield zero rows
        String pastFrom = "2000-01-01T00:00:00Z";
        String pastTo   = "2000-01-01T01:00:00Z";

        mockMvc.perform(get("/audit-logs?from=" + pastFrom + "&to=" + pastTo)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void defaultSortIsTimestampDesc() throws Exception {
        String token = loginAsAdmin();

        createDeveloper(token, "sort-a-" + SEQ.getAndIncrement());
        createDeveloper(token, "sort-b-" + SEQ.getAndIncrement());

        MvcResult result = mockMvc.perform(get("/audit-logs?pageSize=50")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        var tree = objectMapper.readTree(result.getResponse().getContentAsString());
        var data = tree.get("data");
        if (data.size() >= 2) {
            String ts0 = data.get(0).get("timestamp").asText();
            String ts1 = data.get(1).get("timestamp").asText();
            // Descending order: first element >= second element
            assertThat(ts0).isGreaterThanOrEqualTo(ts1);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private long createDeveloper(String adminToken, String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@test.com","fullName":"Dev","role":"DEVELOPER","password":"devpass123"}
                                """.formatted(username, username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
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
