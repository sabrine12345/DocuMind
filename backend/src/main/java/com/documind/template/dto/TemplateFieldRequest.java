package com.documind.template.dto;

import com.documind.template.FieldDataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TemplateFieldRequest(
        @NotBlank(message = "Field name is required.")
        @Size(max = 80, message = "Field name must not exceed 80 characters.")
        String name,

        @NotBlank(message = "Field label is required.")
        @Size(max = 140, message = "Field label must not exceed 140 characters.")
        String label,

        @NotNull(message = "Field data type is required.")
        FieldDataType dataType,

        boolean required,

        int displayOrder
) {
}
