package com.documind.workspace.dto;

import com.documind.user.RoleName;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(
        @NotNull(message = "A workspace role is required.")
        RoleName role
) {
}
