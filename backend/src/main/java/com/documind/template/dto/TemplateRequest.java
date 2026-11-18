package com.documind.template.dto;

import com.documind.document.DocumentCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TemplateRequest(
        @NotBlank(message = "Template name is required.")
        @Size(max = 140, message = "Template name must not exceed 140 characters.")
        String name,

        @NotNull(message = "Document category is required.")
        DocumentCategory category,

        @Size(max = 1000, message = "Description must not exceed 1000 characters.")
        String description,

        boolean active,

        @Valid
        List<TemplateFieldRequest> fields
) {
}
