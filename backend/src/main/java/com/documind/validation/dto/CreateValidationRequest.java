package com.documind.validation.dto;

import jakarta.validation.constraints.Size;

/**
 * Submits a document for validation with an optional note.
 */
public record CreateValidationRequest(
        @Size(max = 2000, message = "A comment cannot exceed 2000 characters.")
        String comment
) {
}
