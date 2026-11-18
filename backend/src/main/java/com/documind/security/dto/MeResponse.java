package com.documind.security.dto;

import com.documind.user.UserResponse;
import com.documind.workspace.dto.WorkspaceSummary;
import java.util.List;
import java.util.UUID;

public record MeResponse(
        UserResponse user,
        List<WorkspaceSummary> workspaces,
        UUID activeWorkspaceId
) {
}
