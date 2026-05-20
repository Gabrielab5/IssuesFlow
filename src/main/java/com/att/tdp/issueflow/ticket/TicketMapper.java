package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.user.User;

public final class TicketMapper {

    private TicketMapper() {
    }

    public static Ticket toEntity(CreateTicketRequest request, Project project, User assignee) {
        Ticket ticket = new Ticket();
        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setStatus(TicketStatus.valueOf(request.status().toUpperCase()));
        ticket.setPriority(TicketPriority.valueOf(request.priority().toUpperCase()));
        ticket.setType(TicketType.valueOf(request.type().toUpperCase()));
        ticket.setProject(project);
        ticket.setAssignee(assignee);
        ticket.setDueDate(request.dueDate());
        return ticket;
    }

    public static TicketResponse toResponse(Ticket ticket) {
        User assignee = ticket.getAssignee();
        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getType(),
                ticket.getProject().getId(),
                assignee != null ? assignee.getId() : null,
                assignee != null ? assignee.getUsername() : null,
                ticket.getDueDate(),
                ticket.isOverdue(),
                ticket.getVersion(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}
