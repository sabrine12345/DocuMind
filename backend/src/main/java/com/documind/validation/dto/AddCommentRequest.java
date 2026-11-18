package com.documind.validation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddCommentRequest(
        @NotBlank(message = "A comment message is required.")
        @Size(max = 2000, message = "A comment cannot exceed 2000 characters.")
        String message
) {
}
