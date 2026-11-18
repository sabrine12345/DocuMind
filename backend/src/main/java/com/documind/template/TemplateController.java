package com.documind.template;

import com.documind.template.dto.TemplateRequest;
import com.documind.template.dto.TemplateResponse;
import com.documind.workspace.WorkspaceResolver;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateService service;
    private final WorkspaceResolver resolver;

    public TemplateController(TemplateService service, WorkspaceResolver resolver) {
        this.service = service;
        this.resolver = resolver;
    }

    @GetMapping
    public List<TemplateResponse> list() {
        return service.list(resolver.requireWorkspaceId());
    }

    @GetMapping("/{id}")
    public TemplateResponse get(@PathVariable UUID id) {
        return service.get(resolver.requireWorkspaceId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canManageTemplates()")
    public TemplateResponse create(@Valid @RequestBody TemplateRequest request) {
        return service.create(resolver.requireWorkspaceId(), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.canManageTemplates()")
    public TemplateResponse update(@PathVariable UUID id, @Valid @RequestBody TemplateRequest request) {
        return service.update(resolver.requireWorkspaceId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.canManageTemplates()")
    public void delete(@PathVariable UUID id) {
        service.delete(resolver.requireWorkspaceId(), id);
    }
}
