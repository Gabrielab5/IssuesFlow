package com.att.tdp.issueflow.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank
        @Size(max = 255)
        String name,

        String description,

        @NotNull
        Long ownerId
) {
}
