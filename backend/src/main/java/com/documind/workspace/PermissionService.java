package com.documind.workspace;

import com.documind.user.RoleName;
import org.springframework.stereotype.Component;

/**
 * Role based permission gates used by {@code @PreAuthorize} expressions.
 */
@Component("perm")
public class PermissionService {

    private final WorkspaceResolver resolver;

    public PermissionService(WorkspaceResolver resolver) {
        this.resolver = resolver;
    }

    public boolean canManageWorkspace() {
        return resolver.hasAnyRole(RoleName.ADMIN);
    }

    public boolean canManageTemplates() {
        return resolver.hasAnyRole(RoleName.ADMIN, RoleName.MANAGER);
    }

    public boolean canValidate() {
        return resolver.hasAnyRole(RoleName.ADMIN, RoleName.MANAGER, RoleName.VALIDATOR);
    }

    public boolean canUploadDocuments() {
        return resolver.hasAnyRole(RoleName.ADMIN, RoleName.MANAGER, RoleName.VALIDATOR, RoleName.USER);
    }

    public boolean canViewAudit() {
        return resolver.hasAnyRole(RoleName.ADMIN, RoleName.MANAGER);
    }
}
