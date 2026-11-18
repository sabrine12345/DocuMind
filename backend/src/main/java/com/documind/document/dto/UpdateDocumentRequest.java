package com.documind.document.dto;

import com.documind.document.DocumentCategory;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Metadata update. A null templateId clears the template assignment.
 */
public record UpdateDocumentRequest(
        @Size(max = 255, message = "Title must not exceed 255 characters.")
        String title,

        @Size(max = 1000, message = "Description must not exceed 1000 characters.")
        String description,

        DocumentCategory category,

        UUID templateId
) {
}
