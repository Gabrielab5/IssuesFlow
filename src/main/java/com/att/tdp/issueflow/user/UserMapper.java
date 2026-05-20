package com.att.tdp.issueflow.user;

public final class UserMapper {

    private UserMapper() {
    }

    public static User toEntity(CreateUserRequest request, String passwordHash) {
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setRole(parseRole(request.role()));
        user.setPasswordHash(passwordHash);
        return user;
    }

    public static void updateEntity(User user, UpdateUserRequest request) {
        user.setFullName(request.fullName());
        user.setRole(parseRole(request.role()));
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole()
        );
    }

    private static UserRole parseRole(String role) {
        return UserRole.valueOf(role.toUpperCase());
    }
}
