package com.documind.usage;

import com.documind.workspace.WorkspaceResolver;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only AI usage endpoints. There is deliberately no way to inject usage:
 * rows only come from the AI integration layer.
 */
@RestController
@RequestMapping("/api/usage")
public class UsageController {

    private final UsageService service;
    private final WorkspaceResolver resolver;

    public UsageController(UsageService service, WorkspaceResolver resolver) {
        this.service = service;
        this.resolver = resolver;
    }

    @GetMapping("/summary")
    @PreAuthorize("@perm.canViewAudit()")
    public UsageSummaryResponse summary(@RequestParam(defaultValue = "7") int days) {
        return service.summary(resolver.requireWorkspaceId(), days);
    }
}
