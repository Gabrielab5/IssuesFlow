package com.att.tdp.issueflow.user;

import com.att.tdp.issueflow.common.validation.ValueOfEnum;
import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(
        @NotBlank
        String fullName,

        @NotBlank
        @ValueOfEnum(enumClass = UserRole.class)
        String role
) {
}
