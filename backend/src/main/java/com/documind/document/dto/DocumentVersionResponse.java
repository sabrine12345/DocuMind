package com.documind.document.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentVersionResponse(
        UUID id,
        int versionNumber,
        String fileName,
        String contentType,
        long sizeBytes,
        String checksum,
        String uploadedByName,
        Instant createdAt
) {
}
