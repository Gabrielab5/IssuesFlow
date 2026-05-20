package com.att.tdp.issueflow.user;

import com.att.tdp.issueflow.common.validation.ValueOfEnum;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        String username,

        @NotBlank
        @Email
        String email,

        @NotBlank
        String fullName,

        @NotBlank
        @ValueOfEnum(enumClass = UserRole.class)
        String role,

        @NotBlank
        @Size(min = 8)
        String password
) {
}
