package com.documind.dashboard;

import com.documind.workspace.WorkspaceResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;
    private final WorkspaceResolver resolver;

    public DashboardController(DashboardService service, WorkspaceResolver resolver) {
        this.service = service;
        this.resolver = resolver;
    }

    @GetMapping("/stats")
    public DashboardStatsResponse stats() {
        return service.stats(resolver.requireWorkspaceId());
    }
}
