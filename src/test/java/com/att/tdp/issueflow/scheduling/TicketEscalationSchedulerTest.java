package com.att.tdp.issueflow.scheduling;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditActor;
import com.att.tdp.issueflow.audit.AuditService;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.ticket.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketEscalationSchedulerTest {

    private static final Instant NOW     = Instant.parse("2026-05-25T12:00:00Z");
    private static final Instant OVERDUE = NOW.minusSeconds(3600); // 1 hour ago

    @Mock private TicketRepository ticketRepository;
    @Mock private AuditService     auditService;

    private TicketEscalationScheduler scheduler;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(NOW, ZoneOffset.UTC);
        scheduler = new TicketEscalationScheduler(ticketRepository, auditService, fixed);
    }

    // ── LOW → MEDIUM ──────────────────────────────────────────────────────────

    @Test
    void lowPriorityOverdue_promotedToMedium_andOneAuditRow() {
        Ticket ticket = ticket(1L, TicketPriority.LOW, false);
        when(ticketRepository.findOverdueForEscalation(NOW)).thenReturn(List.of(ticket));

        scheduler.escalate();

        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(ticket.isOverdue()).isFalse();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(auditService, times(1)).log(
                eq(AuditAction.AUTO_ESCALATE), eq("Ticket"), eq(1L),
                isNull(), eq(AuditActor.SYSTEM), payload.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> p = (Map<String, Object>) payload.getValue();
        assertThat(p.get("oldPriority")).isEqualTo("LOW");
        assertThat(p.get("newPriority")).isEqualTo("MEDIUM");
    }

    // ── HIGH → CRITICAL ───────────────────────────────────────────────────────

    @Test
    void highPriorityOverdue_promotedToCritical() {
        Ticket ticket = ticket(2L, TicketPriority.HIGH, false);
        when(ticketRepository.findOverdueForEscalation(NOW)).thenReturn(List.of(ticket));

        scheduler.escalate();

        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(ticket.isOverdue()).isFalse();
        verify(auditService, times(1)).log(
                eq(AuditAction.AUTO_ESCALATE), any(), any(), any(), any(), any());
    }

    // ── CRITICAL → isOverdue=true ─────────────────────────────────────────────

    @Test
    void criticalOverdue_setsOverdueFlagAndWritesOneAudit() {
        Ticket ticket = ticket(3L, TicketPriority.CRITICAL, false);
        when(ticketRepository.findOverdueForEscalation(NOW)).thenReturn(List.of(ticket));

        scheduler.escalate();

        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(ticket.isOverdue()).isTrue();
        verify(auditService, times(1)).log(
                eq(AuditAction.AUTO_ESCALATE), eq("Ticket"), eq(3L),
                isNull(), eq(AuditActor.SYSTEM), any());
    }

    @Test
    void criticalAlreadyOverdue_secondRunWritesNoExtraAudit() {
        Ticket ticket = ticket(3L, TicketPriority.CRITICAL, true); // already flagged
        when(ticketRepository.findOverdueForEscalation(NOW)).thenReturn(List.of(ticket));

        scheduler.escalate(); // first call
        scheduler.escalate(); // second call — guard must block it

        assertThat(ticket.isOverdue()).isTrue();
        verify(auditService, never()).log(any(), any(), any(), any(), any(), any());
    }

    // ── DONE tickets are excluded by the repository query ─────────────────────

    @Test
    void noOverdueTickets_schedulerMakesNoChanges() {
        when(ticketRepository.findOverdueForEscalation(NOW)).thenReturn(List.of());

        scheduler.escalate();

        verify(auditService, never()).log(any(), any(), any(), any(), any(), any());
    }

    // ── promote() helper ──────────────────────────────────────────────────────

    @Test
    void promoteCoversAllLevels() {
        assertThat(TicketEscalationScheduler.promote(TicketPriority.LOW)).isEqualTo(TicketPriority.MEDIUM);
        assertThat(TicketEscalationScheduler.promote(TicketPriority.MEDIUM)).isEqualTo(TicketPriority.HIGH);
        assertThat(TicketEscalationScheduler.promote(TicketPriority.HIGH)).isEqualTo(TicketPriority.CRITICAL);
        assertThat(TicketEscalationScheduler.promote(TicketPriority.CRITICAL)).isEqualTo(TicketPriority.CRITICAL);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Ticket ticket(Long id, TicketPriority priority, boolean overdue) {
        Project project = new Project();
        project.setId(10L);

        Ticket t = new Ticket();
        t.setId(id);
        t.setPriority(priority);
        t.setStatus(TicketStatus.IN_PROGRESS);
        t.setOverdue(overdue);
        t.setDueDate(OVERDUE);
        t.setProject(project);
        t.setTitle("Ticket " + id);
        return t;
    }
}
