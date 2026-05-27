package com.att.tdp.issueflow;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditActor;
import com.att.tdp.issueflow.audit.AuditLog;
import com.att.tdp.issueflow.audit.AuditLogRepository;
import com.att.tdp.issueflow.audit.AuditLogSpecifications;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.TicketType;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import com.att.tdp.issueflow.user.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SuppressWarnings({"null", "resource"})
class RepositoryDataJpaTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void ticketRepositoryHidesSoftDeletedRowsAndCanQueryDeletedRowsExplicitly() {
        User assignee = saveUser("soft-delete-user");
        Project project = saveProject("Soft Delete Project", assignee);
        Ticket activeTicket = saveTicket("Visible ticket", project, assignee, TicketStatus.TODO);
        Ticket deletedTicket = saveTicket("Deleted ticket", project, assignee, TicketStatus.IN_PROGRESS);

        ticketRepository.delete(deletedTicket);
        ticketRepository.flush();
        entityManager.clear();

        assertThat(ticketRepository.findAll())
                .extracting(Ticket::getId)
                .containsExactly(activeTicket.getId());
        assertThat(ticketRepository.findById(deletedTicket.getId())).isEmpty();
        assertThat(ticketRepository.findAllDeleted())
                .extracting(Ticket::getId)
                .containsExactly(deletedTicket.getId());
        assertThat(ticketRepository.findDeletedById(deletedTicket.getId()))
                .hasValueSatisfying(ticket -> assertThat(ticket.getTitle()).isEqualTo("Deleted ticket"));
    }

    @Test
    void projectRepositoryHidesSoftDeletedRowsAndCanQueryDeletedRowsExplicitly() {
        User owner = saveUser("project-owner");
        Project activeProject = saveProject("Active project", owner);
        Project deletedProject = saveProject("Deleted project", owner);

        projectRepository.delete(deletedProject);
        projectRepository.flush();
        entityManager.clear();

        assertThat(projectRepository.findAll())
                .extracting(Project::getId)
                .containsExactly(activeProject.getId());
        assertThat(projectRepository.findById(deletedProject.getId())).isEmpty();
        assertThat(projectRepository.findDeletedById(deletedProject.getId()))
                .hasValueSatisfying(project -> assertThat(project.getName()).isEqualTo("Deleted project"));
    }

    @Test
    void ticketRepositoryCountsOpenWorkloadForAssigneeAndProject() {
        User assignee = saveUser("workload-user");
        User otherAssignee = saveUser("other-workload-user");
        Project project = saveProject("Workload Project", assignee);
        Project otherProject = saveProject("Other Workload Project", assignee);

        Ticket todo = saveTicket("Todo", project, assignee, TicketStatus.TODO);
        Ticket inProgress = saveTicket("In progress", project, assignee, TicketStatus.IN_PROGRESS);
        saveTicket("Done", project, assignee, TicketStatus.DONE);
        saveTicket("Other assignee", project, otherAssignee, TicketStatus.TODO);
        saveTicket("Other project", otherProject, assignee, TicketStatus.TODO);
        Ticket deletedOpen = saveTicket("Deleted open", project, assignee, TicketStatus.TODO);

        ticketRepository.delete(deletedOpen);
        ticketRepository.flush();
        entityManager.clear();

        assertThat(ticketRepository.countOpenByAssigneeAndProject(assignee.getId(), project.getId())).isEqualTo(2);
        assertThat(ticketRepository.findByProjectIdAndAssigneeIdAndStatusNot(project.getId(), assignee.getId(), TicketStatus.DONE))
                .extracting(Ticket::getId)
                .containsExactlyInAnyOrder(todo.getId(), inProgress.getId());
    }

    @Test
    void auditLogRepositoryFiltersDynamicallyWithSpecifications() {
        Instant now = Instant.parse("2026-05-20T12:00:00Z");
        AuditLog matching = saveAuditLog(
                AuditAction.UPDATE,
                "Ticket",
                101L,
                55L,
                AuditActor.USER,
                now,
                "{\"field\":\"status\"}"
        );
        saveAuditLog(AuditAction.CREATE, "Ticket", 101L, 55L, AuditActor.USER, now.minusSeconds(60), "{}");
        saveAuditLog(AuditAction.UPDATE, "Project", 202L, 55L, AuditActor.USER, now, "{}");
        saveAuditLog(AuditAction.UPDATE, "Ticket", 101L, 77L, AuditActor.SYSTEM, now.plusSeconds(60), "{}");

        assertThat(auditLogRepository.findAll(AuditLogSpecifications.filter(
                AuditAction.UPDATE,
                "Ticket",
                101L,
                55L,
                AuditActor.USER,
                now.minusSeconds(1),
                now.plusSeconds(1)
        )))
                .extracting(AuditLog::getId)
                .containsExactly(matching.getId());

        assertThat(auditLogRepository.findAll(AuditLogSpecifications.filter(
                null,
                "Ticket",
                101L,
                null,
                null,
                null,
                null
        ))).hasSize(3);
    }

    private User saveUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFullName(username);
        user.setRole(UserRole.DEVELOPER);
        user.setPasswordHash("hash");
        return userRepository.saveAndFlush(user);
    }

    private Project saveProject(String name, User owner) {
        Project project = new Project();
        project.setName(name);
        project.setDescription(name + " description");
        project.setOwner(owner);
        return projectRepository.saveAndFlush(project);
    }

    private Ticket saveTicket(String title, Project project, User assignee, TicketStatus status) {
        Ticket ticket = new Ticket();
        ticket.setTitle(title);
        ticket.setDescription(title + " description");
        ticket.setStatus(status);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setType(TicketType.FEATURE);
        ticket.setProject(project);
        ticket.setAssignee(assignee);
        return ticketRepository.saveAndFlush(ticket);
    }

    private AuditLog saveAuditLog(
            AuditAction action,
            String entityType,
            Long entityId,
            Long performedBy,
            AuditActor actor,
            Instant timestamp,
            String payload
    ) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setPerformedBy(performedBy);
        auditLog.setActor(actor);
        auditLog.setTimestamp(timestamp);
        auditLog.setPayload(payload);
        return auditLogRepository.saveAndFlush(auditLog);
    }
}
