package com.documind.extraction.dto;

import jakarta.validation.constraints.Size;

/**
 * Updates the value of an extraction field. A blank value clears the field.
 */
public record UpdateFieldRequest(
        @Size(max = 2000, message = "A field value cannot exceed 2000 characters.")
        String value
) {
}
