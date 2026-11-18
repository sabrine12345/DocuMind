package com.documind.document;

import com.documind.common.PageResponse;
import com.documind.document.dto.DocumentResponse;
import com.documind.document.dto.DocumentVersionResponse;
import com.documind.document.dto.StoredDocument;
import com.documind.document.dto.UpdateDocumentRequest;
import com.documind.processing.ProcessingJobResponse;
import com.documind.processing.ProcessingService;
import com.documind.workspace.WorkspaceResolver;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService service;
    private final ProcessingService processing;
    private final WorkspaceResolver resolver;

    public DocumentController(DocumentService service, ProcessingService processing, WorkspaceResolver resolver) {
        this.service = service;
        this.processing = processing;
        this.resolver = resolver;
    }

    @GetMapping
    public PageResponse<DocumentResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) DocumentCategory category,
            @RequestParam(required = false) UUID templateId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "uploadedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return service.search(resolver.requireWorkspaceId(), search, status, category, templateId,
                page, size, sort, direction);
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable UUID id) {
        return service.get(resolver.requireWorkspaceId(), id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canUploadDocuments()")
    public List<DocumentResponse> upload(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(required = false) UUID templateId,
            @RequestParam(required = false) DocumentCategory category) {
        return service.upload(resolver.requireWorkspaceId(), files, templateId, category);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.canUploadDocuments()")
    public DocumentResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDocumentRequest request) {
        return service.update(resolver.requireWorkspaceId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.canUploadDocuments()")
    public void delete(@PathVariable UUID id) {
        service.delete(resolver.requireWorkspaceId(), id);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id) {
        return stream(id, true);
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<InputStreamResource> preview(@PathVariable UUID id) {
        return stream(id, false);
    }

    @GetMapping("/{id}/versions")
    public List<DocumentVersionResponse> versions(@PathVariable UUID id) {
        return service.listVersions(resolver.requireWorkspaceId(), id);
    }

    @PostMapping(path = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canUploadDocuments()")
    public DocumentVersionResponse addVersion(@PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        return service.addVersion(resolver.requireWorkspaceId(), id, file);
    }

    @GetMapping("/{id}/processing")
    public List<ProcessingJobResponse> processingHistory(@PathVariable UUID id) {
        UUID workspaceId = resolver.requireWorkspaceId();
        service.requireDocument(workspaceId, id);
        return processing.history(workspaceId, id);
    }

    private ResponseEntity<InputStreamResource> stream(UUID id, boolean download) {
        StoredDocument stored = service.loadContent(resolver.requireWorkspaceId(), id, download);
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(stored.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .contentLength(stored.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(stored.stream()));
    }
}
