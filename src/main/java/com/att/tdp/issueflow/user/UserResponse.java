package com.att.tdp.issueflow.user;

public record UserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        UserRole role
) {
}
