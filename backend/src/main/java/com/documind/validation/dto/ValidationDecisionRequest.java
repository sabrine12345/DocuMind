package com.documind.validation.dto;

import jakarta.validation.constraints.Size;

/**
 * Approval or rejection decision. A comment is mandatory when rejecting.
 */
public record ValidationDecisionRequest(
        @Size(max = 2000, message = "A comment cannot exceed 2000 characters.")
        String comment
) {
}
