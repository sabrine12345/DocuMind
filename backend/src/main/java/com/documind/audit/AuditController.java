package com.documind.audit;

import com.documind.common.PageResponse;
import com.documind.workspace.WorkspaceResolver;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditController {

    private final AuditService service;
    private final WorkspaceResolver resolver;

    public AuditController(AuditService service, WorkspaceResolver resolver) {
        this.service = service;
        this.resolver = resolver;
    }

    @GetMapping
    @PreAuthorize("@perm.canViewAudit()")
    public PageResponse<AuditLogResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.list(resolver.requireWorkspaceId(), page, size);
    }

    @GetMapping("/recent")
    public List<AuditLogResponse> recent() {
        return service.recent(resolver.requireWorkspaceId());
    }
}
