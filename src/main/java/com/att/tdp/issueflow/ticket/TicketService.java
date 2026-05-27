package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditActor;
import com.att.tdp.issueflow.audit.AuditService;
import com.att.tdp.issueflow.common.annotation.Audited;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public TicketService(
            TicketRepository ticketRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            AuditService auditService
    ) {
        this.ticketRepository = ticketRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * Creates a ticket. If assigneeId is null, auto-assigns to the DEVELOPER with the fewest
     * open tickets in this project (tie-broken by user.createdAt ASC). If no DEVELOPER exists,
     * the ticket is left unassigned — this is not an error.
     * Auto-assignment is NOT triggered when assigneeId is explicitly provided.
     */
    @Transactional
    @Audited(action = "CREATE", entityType = "Ticket")
    public TicketResponse create(CreateTicketRequest request) {
        Long projectId = Objects.requireNonNull(request.projectId());
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> NotFoundException.of("Project", projectId));

        User assignee = null;
        List<Object[]> autoAssignCandidates = null;
        Long autoAssigneeId = null;

        if (request.assigneeId() != null) {
            Long assigneeId = Objects.requireNonNull(request.assigneeId());
            assignee = userRepository.findById(assigneeId)
                    .orElseThrow(() -> NotFoundException.of("User", assigneeId));
        } else {
            autoAssignCandidates = ticketRepository.findCandidatesSortedByWorkload(projectId);
            if (!autoAssignCandidates.isEmpty()) {
                Long bestId = ((Number) autoAssignCandidates.get(0)[0]).longValue();
                assignee = userRepository.findById(bestId)
                        .orElseThrow(() -> NotFoundException.of("User", bestId));
                autoAssigneeId = assignee.getId();
            }
        }

        Ticket saved = ticketRepository.save(
                Objects.requireNonNull(TicketMapper.toEntity(request, project, assignee)));

        if (autoAssigneeId != null) {
            logAutoAssign(Objects.requireNonNull(saved.getId()), autoAssigneeId, autoAssignCandidates);
        }

        return TicketMapper.toResponse(saved);
    }

    /**
     * Partial update: only non-null fields in the request are applied.
     * A priority change always resets the isOverdue flag — manual triage overrides
     * the scheduler's escalation state.
     */
    @Transactional
    @Audited(action = "UPDATE", entityType = "Ticket", idExpression = "#ticketId")
    public TicketResponse update(@NonNull Long ticketId, UpdateTicketRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> NotFoundException.of("Ticket", ticketId));

        if (request.title() != null && !request.title().isBlank()) {
            ticket.setTitle(request.title());
        }
        if (request.description() != null) {
            ticket.setDescription(request.description());
        }
        if (request.status() != null) {
            ticket.setStatus(TicketStatus.valueOf(request.status().toUpperCase()));
        }
        if (request.priority() != null) {
            ticket.setPriority(TicketPriority.valueOf(request.priority().toUpperCase()));
            ticket.setOverdue(false);
        }
        if (request.type() != null) {
            ticket.setType(TicketType.valueOf(request.type().toUpperCase()));
        }
        if (request.assigneeId() != null) {
            Long assigneeId = Objects.requireNonNull(request.assigneeId());
            User assignee = userRepository.findById(assigneeId)
                    .orElseThrow(() -> NotFoundException.of("User", assigneeId));
            ticket.setAssignee(assignee);
        }
        if (request.dueDate() != null) {
            ticket.setDueDate(request.dueDate());
        }

        return TicketMapper.toResponse(ticket);
    }

    private void logAutoAssign(Long ticketId, Long assigneeId, List<Object[]> candidates) {
        List<Map<String, Object>> candidateCounts = candidates.stream()
                .map(row -> Map.<String, Object>of(
                        "userId", ((Number) row[0]).longValue(),
                        "username", row[1],
                        "openTicketCount", ((Number) row[2]).longValue()
                ))
                .toList();
        auditService.log(
                AuditAction.AUTO_ASSIGN,
                "Ticket",
                ticketId,
                null,
                AuditActor.SYSTEM,
                Map.of(
                        "ticketId", ticketId,
                        "assigneeId", assigneeId,
                        "candidateCounts", candidateCounts
                )
        );
    }
}
