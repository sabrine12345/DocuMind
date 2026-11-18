package com.documind.workspace.dto;

import com.documind.user.RoleName;
import java.time.Instant;
import java.util.UUID;

public record WorkspaceSummary(
        UUID id,
        String name,
        String slug,
        RoleName role,
        long memberCount,
        Instant createdAt
) {
}
