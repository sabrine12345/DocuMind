package com.documind.workspace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateWorkspaceRequest(
        @NotBlank(message = "Workspace name is required.")
        @Size(max = 120, message = "Workspace name must not exceed 120 characters.")
        String name,

        @Min(value = 1, message = "The maximum upload size must be at least 1 MB.")
        @Max(value = 100, message = "The maximum upload size must not exceed 100 MB.")
        int maxUploadSizeMb,

        boolean notifyOnUpload,
        boolean notifyOnValidation
) {
}
