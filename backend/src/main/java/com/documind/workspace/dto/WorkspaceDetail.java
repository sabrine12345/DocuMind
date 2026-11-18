package com.documind.workspace.dto;

import com.documind.user.RoleName;
import java.time.Instant;
import java.util.UUID;

public record WorkspaceDetail(
        UUID id,
        String name,
        String slug,
        RoleName role,
        UUID ownerId,
        long memberCount,
        int maxUploadSizeMb,
        String storageProvider,
        String storageBucket,
        boolean notifyOnUpload,
        boolean notifyOnValidation,
        Instant createdAt
) {
}
