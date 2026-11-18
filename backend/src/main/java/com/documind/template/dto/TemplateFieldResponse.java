package com.documind.template.dto;

import com.documind.template.FieldDataType;
import java.util.UUID;

public record TemplateFieldResponse(
        UUID id,
        String name,
        String label,
        FieldDataType dataType,
        boolean required,
        int displayOrder
) {
}
