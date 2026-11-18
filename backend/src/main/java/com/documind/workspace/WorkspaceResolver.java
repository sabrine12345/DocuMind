package com.documind.workspace;

import com.documind.common.BadRequestException;
import com.documind.common.NotFoundException;
import com.documind.security.AuthenticatedUser;
import com.documind.security.CurrentUserProvider;
import com.documind.user.RoleName;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolves and enforces the active workspace for every request.
 *
 * The workspace is read from the {@code X-Workspace-Id} header when present,
 * otherwise the first workspace of the authenticated user is used. Membership
 * is always verified server-side: a client can never reach another tenant's data.
 */
@Component
public class WorkspaceResolver {

    public static final String WORKSPACE_HEADER = "X-Workspace-Id";

    private final CurrentUserProvider currentUser;
    private final WorkspaceMemberRepository members;
    private final WorkspaceRepository workspaces;

    public WorkspaceResolver(CurrentUserProvider currentUser, WorkspaceMemberRepository members,
            WorkspaceRepository workspaces) {
        this.currentUser = currentUser;
        this.members = members;
        this.workspaces = workspaces;
    }

    public UUID requireWorkspaceId() {
        AuthenticatedUser user = currentUser.require();
        String header = headerValue();
        if (header != null && !header.isBlank()) {
            UUID workspaceId;
            try {
                workspaceId = UUID.fromString(header.trim());
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("The X-Workspace-Id header is not a valid identifier.");
            }
            verifyAccess(user, workspaceId);
            return workspaceId;
        }
        if (user.globalRole() == RoleName.SUPER_ADMIN) {
            return workspaces.findAllByOrderByCreatedAtAsc().stream()
                    .findFirst()
                    .map(WorkspaceEntity::getId)
                    .orElseThrow(() -> new BadRequestException("No workspace exists on the platform yet."));
        }
        return members.findAllForUser(user.id()).stream()
                .findFirst()
                .map(WorkspaceMemberEntity::getWorkspaceId)
                .orElseThrow(() -> new BadRequestException("Your account is not associated with any workspace."));
    }

    public WorkspaceEntity requireWorkspace() {
        return workspaces.findById(requireWorkspaceId())
                .orElseThrow(() -> new NotFoundException("Workspace not found."));
    }

    public RoleName role() {
        AuthenticatedUser user = currentUser.require();
        if (user.globalRole() == RoleName.SUPER_ADMIN) {
            return RoleName.SUPER_ADMIN;
        }
        UUID workspaceId = requireWorkspaceId();
        return members.findByWorkspaceIdAndUser_Id(workspaceId, user.id())
                .map(member -> member.getRole().getName())
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this workspace."));
    }

    public boolean hasAnyRole(RoleName... allowed) {
        RoleName current = role();
        return current == RoleName.SUPER_ADMIN || Arrays.asList(allowed).contains(current);
    }

    private void verifyAccess(AuthenticatedUser user, UUID workspaceId) {
        if (user.globalRole() == RoleName.SUPER_ADMIN) {
            if (!workspaces.existsById(workspaceId)) {
                throw new NotFoundException("Workspace not found.");
            }
            return;
        }
        if (!members.existsByWorkspaceIdAndUser_Id(workspaceId, user.id())) {
            throw new AccessDeniedException("You are not a member of this workspace.");
        }
    }

    private String headerValue() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletRequest(servletAttributes).getHeader(WORKSPACE_HEADER);
        }
        return null;
    }

    private jakarta.servlet.http.HttpServletRequest servletRequest(ServletRequestAttributes attributes) {
        return attributes.getRequest();
    }
}
