package com.documind.extraction.dto;

import com.documind.extraction.ExtractionFieldStatus;
import com.documind.template.FieldDataType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExtractedFieldResponse(
        UUID id,
        UUID documentId,
        UUID templateFieldId,
        String fieldName,
        String fieldLabel,
        FieldDataType dataType,
        String value,
        String originalValue,
        BigDecimal confidence,
        Integer sourcePage,
        ExtractionFieldStatus status,
        UUID editedBy,
        String editedByName,
        Instant editedAt
) {
}
