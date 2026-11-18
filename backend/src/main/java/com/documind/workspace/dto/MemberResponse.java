package com.documind.workspace.dto;

import com.documind.user.RoleName;
import java.time.Instant;
import java.util.UUID;

public record MemberResponse(
        UUID id,
        UUID userId,
        String email,
        String fullName,
        RoleName role,
        boolean owner,
        Instant joinedAt
) {
}
