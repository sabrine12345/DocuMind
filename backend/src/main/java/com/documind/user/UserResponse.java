package com.documind.user;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String jobTitle,
        String globalRole,
        boolean enabled,
        Instant lastLoginAt,
        Instant createdAt
) {
    public static UserResponse from(UserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getJobTitle(),
                user.getGlobalRole().getName().name(),
                user.isEnabled(),
                user.getLastLoginAt(),
                user.getCreatedAt()
        );
    }
}
