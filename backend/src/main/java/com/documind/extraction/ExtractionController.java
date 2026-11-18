package com.documind.extraction;

import com.documind.extraction.dto.ExtractedFieldResponse;
import com.documind.extraction.dto.FieldValidationRequest;
import com.documind.extraction.dto.UpdateFieldRequest;
import com.documind.workspace.WorkspaceResolver;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExtractionController {

    private final ExtractionService service;
    private final WorkspaceResolver resolver;

    public ExtractionController(ExtractionService service, WorkspaceResolver resolver) {
        this.service = service;
        this.resolver = resolver;
    }

    @GetMapping("/api/documents/{documentId}/fields")
    public List<ExtractedFieldResponse> list(@PathVariable UUID documentId) {
        return service.list(resolver.requireWorkspaceId(), documentId);
    }

    @PostMapping("/api/documents/{documentId}/fields/from-template")
    @PreAuthorize("@perm.canUploadDocuments()")
    public List<ExtractedFieldResponse> prepare(@PathVariable UUID documentId,
            @RequestParam(required = false) UUID templateId) {
        return service.prepare(resolver.requireWorkspaceId(), documentId, templateId);
    }

    @PostMapping("/api/documents/{documentId}/fields/validate")
    @PreAuthorize("@perm.canValidate()")
    public List<ExtractedFieldResponse> validateAll(@PathVariable UUID documentId) {
        return service.validateAll(resolver.requireWorkspaceId(), documentId);
    }

    @PutMapping("/api/fields/{fieldId}")
    @PreAuthorize("@perm.canUploadDocuments()")
    public ExtractedFieldResponse update(@PathVariable UUID fieldId,
            @Valid @RequestBody UpdateFieldRequest request) {
        return service.updateValue(resolver.requireWorkspaceId(), fieldId, request);
    }

    @PostMapping("/api/fields/{fieldId}/validate")
    @PreAuthorize("@perm.canValidate()")
    public ExtractedFieldResponse validate(@PathVariable UUID fieldId,
            @Valid @RequestBody(required = false) FieldValidationRequest request) {
        return service.validate(resolver.requireWorkspaceId(), fieldId, request);
    }
}
