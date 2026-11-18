package com.documind.document.dto;

import com.documind.document.DocumentCategory;
import com.documind.document.DocumentStatus;
import com.documind.document.DocumentValidationStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Public document representation. Storage keys and physical locations are
 * deliberately absent from this payload.
 */
public record DocumentResponse(
        UUID id,
        String fileName,
        String title,
        String description,
        String contentType,
        long sizeBytes,
        int pages,
        DocumentCategory category,
        DocumentStatus status,
        int progress,
        DocumentValidationStatus validationStatus,
        UUID templateId,
        String templateName,
        int currentVersion,
        UUID uploadedBy,
        String uploadedByName,
        String checksum,
        Instant uploadedAt,
        Instant updatedAt
) {
}
