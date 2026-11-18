package com.documind.processing;

import com.documind.common.PageResponse;
import com.documind.workspace.WorkspaceResolver;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/processing/jobs")
public class ProcessingController {

    private final ProcessingService service;
    private final WorkspaceResolver resolver;

    public ProcessingController(ProcessingService service, WorkspaceResolver resolver) {
        this.service = service;
        this.resolver = resolver;
    }

    @GetMapping
    public PageResponse<ProcessingJobResponse> list(
            @RequestParam(required = false) ProcessingJobStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(resolver.requireWorkspaceId(), status, page, size);
    }

    @GetMapping("/{id}")
    public ProcessingJobResponse get(@PathVariable UUID id) {
        return service.get(resolver.requireWorkspaceId(), id);
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("@perm.canUploadDocuments()")
    public ProcessingJobResponse retry(@PathVariable UUID id) {
        return service.retry(resolver.requireWorkspaceId(), id);
    }
}
