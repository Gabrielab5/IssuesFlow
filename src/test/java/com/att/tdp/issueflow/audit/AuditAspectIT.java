package com.att.tdp.issueflow.audit;

import com.att.tdp.issueflow.user.CreateUserRequest;
import com.att.tdp.issueflow.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that AuditAspect fires inside a real Spring context with AspectJ proxying active.
 *
 * Same-TX semantics: the audit row is written within the business method's transaction.
 * If that transaction rolls back (tested in txRollbackAlsoRollsBackAuditRow), the audit row
 * disappears too. This is the deliberate "audit on success" trade-off documented in AuditAspect.
 */
@SpringBootTest
@Testcontainers
class AuditAspectIT {

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
    private UserService userService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private static final AtomicLong SEQ = new AtomicLong(1);

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void auditedCreateMethodPersistsAuditRow() {
        setAdminSecurityContext();
        long before = auditLogRepository.count();

        String username = "aspect-create-" + SEQ.getAndIncrement();
        userService.create(new CreateUserRequest(
                username, username + "@test.com", "Test", "DEVELOPER", "pass"));

        List<AuditLog> rows = auditLogRepository.findAll().stream()
                .filter(r -> r.getEntityType().equals("User") && r.getAction() == AuditAction.CREATE)
                .toList();

        assertThat(rows.size()).isGreaterThan((int) before);
        AuditLog last = rows.getLast();
        assertThat(last.getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(last.getEntityType()).isEqualTo("User");
        assertThat(last.getEntityId()).isNotNull();
        assertThat(last.getActor()).isEqualTo(AuditActor.USER);
        assertThat(last.getPayload()).contains(username);
    }

    @Test
    void exceptionInBusinessCallPreventsAuditRow() {
        setAdminSecurityContext();
        long before = auditLogRepository.count();

        // "admin" already exists in seed data — create must fail with ConflictException
        assertThatThrownBy(() -> userService.create(
                new CreateUserRequest("admin", "admin-dup@test.com", "A", "ADMIN", "pass")))
                .isInstanceOf(com.att.tdp.issueflow.common.exception.ConflictException.class);

        // No new audit row should have been written
        assertThat(auditLogRepository.count()).isEqualTo(before);
    }

    @Test
    void txRollbackAlsoRollsBackAuditRow() {
        setAdminSecurityContext();
        long before = auditLogRepository.count();
        String username = "rollback-" + SEQ.getAndIncrement();

        // Execute inside a transaction we force-roll-back
        transactionTemplate.execute(status -> {
            userService.create(new CreateUserRequest(
                    username, username + "@test.com", "T", "DEVELOPER", "pass"));
            // Audit row is written inside this TX, but we mark it for rollback
            status.setRollbackOnly();
            return null;
        });

        // Both the user row and the audit row were rolled back
        assertThat(auditLogRepository.count()).isEqualTo(before);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void setAdminSecurityContext() {
        var auth = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
