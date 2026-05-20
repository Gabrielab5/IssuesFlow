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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

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
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> NotFoundException.of("Project", request.projectId()));

        User assignee = null;
        List<Object[]> candidates = null;

        if (request.assigneeId() != null) {
            assignee = userRepository.findById(request.assigneeId())
                    .orElseThrow(() -> NotFoundException.of("User", request.assigneeId()));
        } else {
            candidates = ticketRepository.findCandidatesSortedByWorkload(request.projectId());
            if (!candidates.isEmpty()) {
                Long bestId = ((Number) candidates.get(0)[0]).longValue();
                assignee = userRepository.findById(bestId)
                        .orElseThrow(() -> NotFoundException.of("User", bestId));
            }
        }

        Ticket saved = ticketRepository.save(TicketMapper.toEntity(request, project, assignee));

        if (candidates != null && !candidates.isEmpty()) {
            logAutoAssign(saved.getId(), assignee.getId(), candidates);
        }

        return TicketMapper.toResponse(saved);
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
