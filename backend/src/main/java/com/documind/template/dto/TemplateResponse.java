package com.documind.template.dto;

import com.documind.document.DocumentCategory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TemplateResponse(
        UUID id,
        String name,
        DocumentCategory category,
        String description,
        boolean active,
        int fieldCount,
        List<TemplateFieldResponse> fields,
        Instant createdAt,
        Instant updatedAt
) {
}
