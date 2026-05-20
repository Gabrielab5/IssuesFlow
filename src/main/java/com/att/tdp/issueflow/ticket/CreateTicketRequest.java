package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.common.validation.ValueOfEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateTicketRequest(
        @NotBlank
        @Size(max = 255)
        String title,

        String description,

        @NotBlank
        @ValueOfEnum(enumClass = TicketStatus.class)
        String status,

        @NotBlank
        @ValueOfEnum(enumClass = TicketPriority.class)
        String priority,

        @NotBlank
        @ValueOfEnum(enumClass = TicketType.class)
        String type,

        @NotNull
        Long projectId,

        Long assigneeId,

        Instant dueDate
) {
}
