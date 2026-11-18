package com.documind.extraction.dto;

/**
 * Toggles the validated state of an extraction field. When the body is
 * omitted the field is validated.
 */
public record FieldValidationRequest(Boolean validated) {
}
