package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditActor;
import com.att.tdp.issueflow.audit.AuditService;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import com.att.tdp.issueflow.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, projectRepository, userRepository, auditService);
    }

    // ── explicit assignee bypasses auto-assign ────────────────────────────────

    @Test
    void explicitAssigneeIdBypassesAutoAssign() {
        Project project = project(1L);
        User dev = developer(5L, "alice");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(userRepository.findById(5L)).thenReturn(Optional.of(dev));
        when(ticketRepository.save(any())).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            t.setId(10L);
            return t;
        });

        TicketResponse response = ticketService.create(request(1L, 5L));

        assertThat(response.assigneeId()).isEqualTo(5L);
        assertThat(response.assigneeUsername()).isEqualTo("alice");
        // auto-assign query and audit must not be called
        verify(ticketRepository, never()).findCandidatesSortedByWorkload(any());
        verify(auditService, never()).log(eq(AuditAction.AUTO_ASSIGN), any(), any(), any(), any(), any());
    }

    // ── auto-assign happy path ────────────────────────────────────────────────

    @Test
    void autoAssignPicksDeveloperWithLowestOpenCount() {
        Project project = project(1L);
        User alice = developer(2L, "alice");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        // alice has 1 open ticket, bob has 3 — alice is the best candidate (first in sorted list)
        when(ticketRepository.findCandidatesSortedByWorkload(1L)).thenReturn(List.of(
                new Object[]{2L, "alice", 1L},
                new Object[]{5L, "bob", 3L}
        ));
        when(userRepository.findById(2L)).thenReturn(Optional.of(alice));
        when(ticketRepository.save(any())).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            t.setId(20L);
            return t;
        });

        TicketResponse response = ticketService.create(request(1L, null));

        assertThat(response.assigneeId()).isEqualTo(2L);
        assertThat(response.assigneeUsername()).isEqualTo("alice");
    }

    @Test
    void autoAssignTiesBrokenByCreatedAt() {
        // The query already returns the tie-broken list; service just takes index 0.
        // This test verifies the service picks the first element when counts are equal.
        Project project = project(1L);
        User alice = developer(2L, "alice"); // older user — query returns her first
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(ticketRepository.findCandidatesSortedByWorkload(1L)).thenReturn(List.of(
                new Object[]{2L, "alice", 2L},
                new Object[]{5L, "bob", 2L}
        ));
        when(userRepository.findById(2L)).thenReturn(Optional.of(alice));
        when(ticketRepository.save(any())).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            t.setId(21L);
            return t;
        });

        TicketResponse response = ticketService.create(request(1L, null));

        assertThat(response.assigneeUsername()).isEqualTo("alice");
    }

    // ── auto-assign skips ADMINs ──────────────────────────────────────────────

    @Test
    void autoAssignSkipsAdmins_onlyAdminsInSystem_leavesAssigneeNull() {
        // The SQL query filters WHERE role = 'DEVELOPER', so it returns an empty list
        // when only admins exist. The service must leave the ticket unassigned.
        Project project = project(1L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(ticketRepository.findCandidatesSortedByWorkload(1L)).thenReturn(List.of());
        when(ticketRepository.save(any())).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            t.setId(22L);
            return t;
        });

        TicketResponse response = ticketService.create(request(1L, null));

        assertThat(response.assigneeId()).isNull();
        assertThat(response.assigneeUsername()).isNull();
        verify(auditService, never()).log(eq(AuditAction.AUTO_ASSIGN), any(), any(), any(), any(), any());
    }

    // ── empty pool ────────────────────────────────────────────────────────────

    @Test
    void autoAssignOnEmptyPoolLeavesAssigneeNullWithNoAudit() {
        Project project = project(1L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(ticketRepository.findCandidatesSortedByWorkload(1L)).thenReturn(List.of());
        when(ticketRepository.save(any())).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            t.setId(30L);
            return t;
        });

        TicketResponse response = ticketService.create(request(1L, null));

        assertThat(response.assigneeId()).isNull();
        verify(auditService, never()).log(any(), any(), any(), any(), any(), any());
    }

    // ── audit log on auto-assign ──────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void autoAssignLogsAuditWithCandidateCounts() {
        Project project = project(1L);
        User alice = developer(2L, "alice");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(ticketRepository.findCandidatesSortedByWorkload(1L)).thenReturn(List.of(
                new Object[]{2L, "alice", 1L},
                new Object[]{5L, "bob", 3L}
        ));
        when(userRepository.findById(2L)).thenReturn(Optional.of(alice));
        when(ticketRepository.save(any())).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            t.setId(40L);
            return t;
        });

        ticketService.create(request(1L, null));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).log(
                eq(AuditAction.AUTO_ASSIGN),
                eq("Ticket"),
                eq(40L),
                isNull(),
                eq(AuditActor.SYSTEM),
                payloadCaptor.capture()
        );

        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(payload.get("ticketId")).isEqualTo(40L);
        assertThat(payload.get("assigneeId")).isEqualTo(2L);

        List<Map<String, Object>> counts = (List<Map<String, Object>>) payload.get("candidateCounts");
        assertThat(counts).hasSize(2);
        assertThat(counts.get(0).get("username")).isEqualTo("alice");
        assertThat(counts.get(0).get("openTicketCount")).isEqualTo(1L);
        assertThat(counts.get(1).get("username")).isEqualTo("bob");
        assertThat(counts.get(1).get("openTicketCount")).isEqualTo(3L);
    }

    // ── update: isOverdue reset ───────────────────────────────────────────────

    @Test
    void update_priorityChange_clearsOverdueFlag() {
        Ticket ticket = ticketWithOverdue(1L, TicketPriority.CRITICAL, true);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        UpdateTicketRequest request = new UpdateTicketRequest(null, null, null, "HIGH", null, null, null);
        TicketResponse result = ticketService.update(1L, request);

        assertThat(result.priority()).isEqualTo(TicketPriority.HIGH);
        assertThat(result.overdue()).isFalse();
    }

    @Test
    void update_noFieldsChanged_preservesExistingValues() {
        Ticket ticket = ticketWithOverdue(2L, TicketPriority.MEDIUM, false);
        when(ticketRepository.findById(2L)).thenReturn(Optional.of(ticket));

        // All null fields → nothing changes
        UpdateTicketRequest request = new UpdateTicketRequest(null, null, null, null, null, null, null);
        TicketResponse result = ticketService.update(2L, request);

        assertThat(result.priority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(result.overdue()).isFalse();
    }

    @Test
    void update_throwsNotFoundForUnknownTicket() {
        when(ticketRepository.findById(999L)).thenReturn(Optional.empty());

        UpdateTicketRequest request = new UpdateTicketRequest(null, null, "DONE", null, null, null, null);
        assertThatThrownBy(() -> ticketService.update(999L, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("999");
    }

    // ── error paths ───────────────────────────────────────────────────────────

    @Test
    void createThrowsNotFoundForUnknownProject() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.create(request(99L, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void createThrowsNotFoundForUnknownExplicitAssignee() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project(1L)));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.create(request(1L, 999L)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("999");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CreateTicketRequest request(Long projectId, Long assigneeId) {
        return new CreateTicketRequest(
                "Test ticket",
                "Some description",
                "TODO",
                "MEDIUM",
                "BUG",
                projectId,
                assigneeId,
                null
        );
    }

    private Project project(Long id) {
        User owner = new User();
        owner.setId(10L);
        owner.setUsername("owner");
        owner.setRole(UserRole.ADMIN);

        Project project = new Project();
        project.setId(id);
        project.setName("Test Project");
        project.setOwner(owner);
        return project;
    }

    private User developer(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(UserRole.DEVELOPER);
        return user;
    }

    private Ticket ticketWithOverdue(Long id, TicketPriority priority, boolean overdue) {
        Project p = project(1L);
        Ticket t = new Ticket();
        t.setId(id);
        t.setTitle("Test");
        t.setStatus(TicketStatus.IN_PROGRESS);
        t.setPriority(priority);
        t.setType(TicketType.BUG);
        t.setProject(p);
        t.setOverdue(overdue);
        return t;
    }
}
