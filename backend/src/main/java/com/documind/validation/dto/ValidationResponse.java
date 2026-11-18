package com.documind.validation.dto;

import com.documind.document.DocumentCategory;
import com.documind.validation.ValidationStatus;
import java.time.Instant;
import java.util.UUID;

public record ValidationResponse(
        UUID id,
        UUID documentId,
        String fileName,
        DocumentCategory category,
        int documentVersion,
        ValidationStatus status,
        UUID submittedBy,
        String submittedByName,
        Instant submittedAt,
        UUID decidedBy,
        String decidedByName,
        Instant decidedAt,
        String decisionComment,
        long commentCount,
        Instant createdAt,
        Instant updatedAt
) {
}
