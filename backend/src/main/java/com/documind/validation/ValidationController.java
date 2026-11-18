package com.documind.validation;

import com.documind.common.PageResponse;
import com.documind.validation.dto.AddCommentRequest;
import com.documind.validation.dto.CreateValidationRequest;
import com.documind.validation.dto.ValidationCommentResponse;
import com.documind.validation.dto.ValidationDecisionRequest;
import com.documind.validation.dto.ValidationDetailResponse;
import com.documind.validation.dto.ValidationResponse;
import com.documind.workspace.WorkspaceResolver;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ValidationController {

    private final ValidationService service;
    private final WorkspaceResolver resolver;

    public ValidationController(ValidationService service, WorkspaceResolver resolver) {
        this.service = service;
        this.resolver = resolver;
    }

    @GetMapping("/api/validations")
    public PageResponse<ValidationResponse> list(
            @RequestParam(required = false) ValidationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(resolver.requireWorkspaceId(), status, page, size);
    }

    @GetMapping("/api/validations/{id}")
    public ValidationDetailResponse get(@PathVariable UUID id) {
        return service.get(resolver.requireWorkspaceId(), id);
    }

    @GetMapping("/api/validations/{id}/comments")
    public List<ValidationCommentResponse> comments(@PathVariable UUID id) {
        return service.listComments(resolver.requireWorkspaceId(), id);
    }

    @PostMapping("/api/validations/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canUploadDocuments()")
    public ValidationCommentResponse addComment(@PathVariable UUID id,
            @Valid @RequestBody AddCommentRequest request) {
        return service.addComment(resolver.requireWorkspaceId(), id, request);
    }

    @PostMapping("/api/validations/{id}/approve")
    @PreAuthorize("@perm.canValidate()")
    public ValidationDetailResponse approve(@PathVariable UUID id,
            @Valid @RequestBody(required = false) ValidationDecisionRequest request) {
        return service.approve(resolver.requireWorkspaceId(), id, request);
    }

    @PostMapping("/api/validations/{id}/reject")
    @PreAuthorize("@perm.canValidate()")
    public ValidationDetailResponse reject(@PathVariable UUID id,
            @Valid @RequestBody(required = false) ValidationDecisionRequest request) {
        return service.reject(resolver.requireWorkspaceId(), id, request);
    }

    @GetMapping("/api/documents/{documentId}/validations")
    public List<ValidationResponse> forDocument(@PathVariable UUID documentId) {
        return service.forDocument(resolver.requireWorkspaceId(), documentId);
    }

    @PostMapping("/api/documents/{documentId}/validations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canUploadDocuments()")
    public ValidationDetailResponse submit(@PathVariable UUID documentId,
            @Valid @RequestBody(required = false) CreateValidationRequest request) {
        return service.submit(resolver.requireWorkspaceId(), documentId, request);
    }
}
