package com.documind.workspace.dto;

import com.documind.user.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddMemberRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "A valid email address is required.")
        String email,

        @NotNull(message = "A workspace role is required.")
        RoleName role
) {
}
