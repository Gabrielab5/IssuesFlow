package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.common.validation.ValueOfEnum;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateTicketRequest(
        @Size(max = 255)
        String title,

        String description,

        @ValueOfEnum(enumClass = TicketStatus.class)
        String status,

        @ValueOfEnum(enumClass = TicketPriority.class)
        String priority,

        @ValueOfEnum(enumClass = TicketType.class)
        String type,

        Long assigneeId,

        Instant dueDate
) {
}
