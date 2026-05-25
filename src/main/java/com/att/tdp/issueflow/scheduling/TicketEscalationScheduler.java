package com.att.tdp.issueflow.scheduling;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditActor;
import com.att.tdp.issueflow.audit.AuditService;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Runs every 5 minutes (configurable via {@code issueflow.escalation.cron}) and promotes the
 * priority of each non-DONE ticket whose {@code dueDate} has passed:
 * <ul>
 *   <li>LOW / MEDIUM / HIGH → next level, one AUTO_ESCALATE audit row per ticket.</li>
 *   <li>CRITICAL → sets {@code isOverdue = true} (idempotent: skipped if already set).</li>
 * </ul>
 *
 * <p><b>Idempotency:</b> Re-running after a restart produces no duplicate escalations because
 * once a ticket reaches CRITICAL the {@code isOverdue} guard prevents a second audit row, and for
 * lower priorities the priority field is already higher so the same level cannot be re-applied.
 *
 * <p><b>Concurrency:</b> {@link TicketRepository#findOverdueForEscalation} uses
 * {@code FOR UPDATE SKIP LOCKED} so two concurrent app instances process disjoint ticket sets.
 */
@Component
public class TicketEscalationScheduler {

    private static final Logger log = LoggerFactory.getLogger(TicketEscalationScheduler.class);

    private final TicketRepository ticketRepository;
    private final AuditService     auditService;
    private final Clock            clock;

    public TicketEscalationScheduler(
            TicketRepository ticketRepository,
            AuditService auditService,
            Clock clock) {
        this.ticketRepository = ticketRepository;
        this.auditService     = auditService;
        this.clock            = clock;
    }

    @Scheduled(cron = "${issueflow.escalation.cron:0 */5 * * * *}")
    @Transactional
    public void escalate() {
        Instant now = Instant.now(clock);
        List<Ticket> overdue = ticketRepository.findOverdueForEscalation(now);
        if (!overdue.isEmpty()) {
            log.debug("Escalation run at {}: {} overdue ticket(s)", now, overdue.size());
        }
        overdue.forEach(this::escalateOne);
    }

    private void escalateOne(Ticket ticket) {
        TicketPriority old = ticket.getPriority();
        if (old == TicketPriority.CRITICAL) {
            if (ticket.isOverdue()) {
                return; // idempotent — already flagged, no extra audit
            }
            ticket.setOverdue(true);
            logEscalation(ticket, TicketPriority.CRITICAL, TicketPriority.CRITICAL);
        } else {
            TicketPriority next = promote(old);
            ticket.setPriority(next);
            logEscalation(ticket, old, next);
        }
    }

    private void logEscalation(Ticket ticket, TicketPriority oldPriority, TicketPriority newPriority) {
        auditService.log(
                AuditAction.AUTO_ESCALATE,
                "Ticket",
                ticket.getId(),
                null,
                AuditActor.SYSTEM,
                Map.of(
                        "oldPriority", oldPriority.name(),
                        "newPriority", newPriority.name(),
                        "dueDate",     String.valueOf(ticket.getDueDate())
                )
        );
    }

    static TicketPriority promote(TicketPriority p) {
        return switch (p) {
            case LOW      -> TicketPriority.MEDIUM;
            case MEDIUM   -> TicketPriority.HIGH;
            case HIGH     -> TicketPriority.CRITICAL;
            case CRITICAL -> TicketPriority.CRITICAL;
        };
    }
}
