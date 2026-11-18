package com.documind.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "The current password is required.")
        String currentPassword,

        @NotBlank(message = "The new password is required.")
        @Size(min = 8, max = 100, message = "Password must contain between 8 and 100 characters.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$",
                message = "Password must contain at least one letter and one digit.")
        String newPassword
) {
}
