package com.att.tdp.issueflow.ticket;

import java.time.Instant;

public record TicketResponse(
        Long id,
        String title,
        String description,
        TicketStatus status,
        TicketPriority priority,
        TicketType type,
        Long projectId,
        Long assigneeId,
        String assigneeUsername,
        Instant dueDate,
        boolean overdue,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
