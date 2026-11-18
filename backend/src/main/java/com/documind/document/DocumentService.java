package com.documind.document;

import com.documind.audit.AuditAction;
import com.documind.audit.AuditService;
import com.documind.common.BadRequestException;
import com.documind.common.ConflictException;
import com.documind.common.NotFoundException;
import com.documind.common.PageResponse;
import com.documind.document.dto.DocumentResponse;
import com.documind.document.dto.DocumentVersionResponse;
import com.documind.document.dto.StoredDocument;
import com.documind.document.dto.UpdateDocumentRequest;
import com.documind.processing.ProcessingService;
import com.documind.security.AuthenticatedUser;
import com.documind.security.CurrentUserProvider;
import com.documind.storage.StorageException;
import com.documind.storage.StorageService;
import com.documind.template.DocumentTemplateEntity;
import com.documind.template.DocumentTemplateRepository;
import com.documind.template.TemplateService;
import com.documind.user.UserEntity;
import com.documind.user.UserRepository;
import com.documind.workspace.WorkspaceEntity;
import com.documind.workspace.WorkspaceRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private static final int MAX_FILES_PER_UPLOAD = 10;

    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final WorkspaceRepository workspaces;
    private final UserRepository users;
    private final DocumentTemplateRepository templates;
    private final TemplateService templateService;
    private final StorageService storage;
    private final FileValidator fileValidator;
    private final ProcessingService processing;
    private final AuditService audit;
    private final CurrentUserProvider currentUser;

    public DocumentService(DocumentRepository documents, DocumentVersionRepository versions,
            WorkspaceRepository workspaces, UserRepository users, DocumentTemplateRepository templates,
            TemplateService templateService, StorageService storage, FileValidator fileValidator,
            ProcessingService processing, AuditService audit, CurrentUserProvider currentUser) {
        this.documents = documents;
        this.versions = versions;
        this.workspaces = workspaces;
        this.users = users;
        this.templates = templates;
        this.templateService = templateService;
        this.storage = storage;
        this.fileValidator = fileValidator;
        this.processing = processing;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> search(UUID workspaceId, String search, DocumentStatus status,
            DocumentCategory category, UUID templateId, int page, int size, String sortBy, String direction) {
        Specification<DocumentEntity> spec = DocumentSpecifications.inWorkspace(workspaceId)
                .and(DocumentSpecifications.notDeleted());
        if (search != null && !search.isBlank()) {
            spec = spec.and(DocumentSpecifications.fileNameContains(search.trim()));
        }
        if (status != null) {
            spec = spec.and(DocumentSpecifications.hasStatus(status));
        }
        if (category != null) {
            spec = spec.and(DocumentSpecifications.hasCategory(category));
        }
        if (templateId != null) {
            spec = spec.and(DocumentSpecifications.hasTemplate(templateId));
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                sort(sortBy, direction));
        Page<DocumentEntity> result = documents.findAll(spec, pageable);
        Map<UUID, String> userNames = uploaderNames(result.getContent());
        Map<UUID, String> templateNames = templateNames(workspaceId, result.getContent());
        return PageResponse.of(result, document -> toResponse(document, userNames, templateNames));
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(UUID workspaceId, UUID documentId) {
        DocumentEntity document = requireDocument(workspaceId, documentId);
        return toResponse(document,
                uploaderNames(List.of(document)),
                templateNames(workspaceId, List.of(document)));
    }

    @Transactional(readOnly = true)
    public DocumentEntity requireDocument(UUID workspaceId, UUID documentId) {
        DocumentEntity document = documents.findByIdAndWorkspaceId(documentId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Document not found."));
        if (document.isDeleted()) {
            throw new NotFoundException("Document not found.");
        }
        return document;
    }

    @Transactional
    public List<DocumentResponse> upload(UUID workspaceId, List<MultipartFile> files, UUID templateId,
            DocumentCategory category) {
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("Please select at least one file.");
        }
        if (files.size() > MAX_FILES_PER_UPLOAD) {
            throw new BadRequestException("A maximum of " + MAX_FILES_PER_UPLOAD + " files can be uploaded at once.");
        }
        WorkspaceEntity workspace = workspaces.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace not found."));
        DocumentTemplateEntity template = templateId == null ? null
                : templateService.requireTemplate(workspaceId, templateId);
        List<FileValidator.DetectedFile> detectedFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            detectedFiles.add(fileValidator.validate(file, workspace.getMaxUploadSizeMb()));
        }

        AuthenticatedUser actor = currentUser.require();
        List<String> storedKeys = new ArrayList<>();
        List<DocumentEntity> created = new ArrayList<>();
        try {
            for (int index = 0; index < files.size(); index++) {
                MultipartFile file = files.get(index);
                FileValidator.DetectedFile detected = detectedFiles.get(index);
                DocumentCategory resolvedCategory = category != null ? category
                        : template != null ? template.getCategory() : DocumentCategory.OTHER;
                DocumentEntity document = documents.save(new DocumentEntity(workspaceId,
                        detected.originalName(), detected.contentType(), file.getSize(),
                        resolvedCategory, template == null ? null : template.getId(), actor.id()));
                String key = storageKey(workspaceId, document.getId(), 1, detected.canonicalExtension());
                try (InputStream stream = file.getInputStream()) {
                    storage.put(key, stream, file.getSize(), detected.contentType());
                }
                storedKeys.add(key);
                versions.save(new DocumentVersionEntity(workspaceId, document.getId(), 1,
                        detected.originalName(), detected.contentType(), file.getSize(), key,
                        storage.provider(), actor.id()));
                processing.enqueue(workspaceId, document.getId());
                audit.record(workspaceId, AuditAction.DOCUMENT_UPLOADED, "DOCUMENT", document.getId(),
                        detected.originalName() + " (" + file.getSize() + " bytes)");
                created.add(document);
            }
        } catch (IOException ex) {
            cleanup(storedKeys);
            throw new StorageException("The uploaded file could not be read.", ex);
        } catch (RuntimeException ex) {
            cleanup(storedKeys);
            throw ex;
        }
        Map<UUID, String> names = Map.of(actor.id(), actor.fullName());
        Map<UUID, String> templateNames = template == null ? Map.of()
                : Map.of(template.getId(), template.getName());
        return created.stream().map(document -> toResponse(document, names, templateNames)).toList();
    }

    @Transactional
    public DocumentResponse update(UUID workspaceId, UUID documentId, UpdateDocumentRequest request) {
        DocumentEntity document = requireDocument(workspaceId, documentId);
        if (request.templateId() != null) {
            templateService.requireTemplate(workspaceId, request.templateId());
        }
        DocumentCategory category = request.category() == null ? document.getCategory() : request.category();
        // A missing templateId keeps the current template instead of clearing it.
        UUID templateId = request.templateId() == null ? document.getTemplateId() : request.templateId();
        document.applyMetadata(normalize(request.title()), normalize(request.description()), category, templateId);
        documents.save(document);
        audit.record(workspaceId, AuditAction.DOCUMENT_UPDATED, "DOCUMENT", documentId,
                "Metadata updated for " + document.getFileName());
        return toResponse(document, uploaderNames(List.of(document)),
                templateNames(workspaceId, List.of(document)));
    }

    @Transactional
    public void delete(UUID workspaceId, UUID documentId) {
        DocumentEntity document = requireDocument(workspaceId, documentId);
        document.softDelete();
        documents.save(document);
        audit.record(workspaceId, AuditAction.DOCUMENT_DELETED, "DOCUMENT", documentId, document.getFileName());
    }

    @Transactional(readOnly = true)
    public List<DocumentVersionResponse> listVersions(UUID workspaceId, UUID documentId) {
        requireDocument(workspaceId, documentId);
        List<DocumentVersionEntity> versionList = versions.findAllByDocumentIdOrderByVersionNumberDesc(documentId);
        Map<UUID, String> names = uploaderNamesFromVersions(versionList);
        return versionList.stream().map(version -> new DocumentVersionResponse(
                version.getId(), version.getVersionNumber(), version.getFileName(), version.getContentType(),
                version.getSizeBytes(), version.getChecksum(),
                version.getUploadedBy() == null ? null : names.get(version.getUploadedBy()),
                version.getCreatedAt())).toList();
    }

    @Transactional
    public DocumentVersionResponse addVersion(UUID workspaceId, UUID documentId, MultipartFile file) {
        DocumentEntity document = requireDocument(workspaceId, documentId);
        WorkspaceEntity workspace = workspaces.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace not found."));
        FileValidator.DetectedFile detected = fileValidator.validate(file, workspace.getMaxUploadSizeMb());
        int nextVersion = document.getCurrentVersion() + 1;
        String key = storageKey(workspaceId, documentId, nextVersion, detected.canonicalExtension());
        try (InputStream stream = file.getInputStream()) {
            storage.put(key, stream, file.getSize(), detected.contentType());
        } catch (IOException ex) {
            throw new StorageException("The uploaded file could not be read.", ex);
        } catch (RuntimeException ex) {
            storage.delete(key);
            throw ex;
        }
        UUID actorId = currentUser.require().id();
        DocumentVersionEntity version = versions.save(new DocumentVersionEntity(workspaceId, documentId,
                nextVersion, detected.originalName(), detected.contentType(), file.getSize(), key,
                storage.provider(), actorId));
        document.replaceFile(detected.originalName(), detected.contentType(), file.getSize());
        document.setValidationStatus(DocumentValidationStatus.NONE);
        document.markQueued(nextVersion);
        documents.save(document);
        processing.enqueue(workspaceId, documentId);
        audit.record(workspaceId, AuditAction.DOCUMENT_VERSION_ADDED, "DOCUMENT", documentId,
                "Version " + nextVersion + " uploaded");
        return new DocumentVersionResponse(version.getId(), version.getVersionNumber(), version.getFileName(),
                version.getContentType(), version.getSizeBytes(), version.getChecksum(),
                currentUser.require().fullName(), version.getCreatedAt());
    }

    @Transactional
    public StoredDocument loadContent(UUID workspaceId, UUID documentId, boolean download) {
        DocumentEntity document = requireDocument(workspaceId, documentId);
        DocumentVersionEntity version = versions
                .findByDocumentIdAndVersionNumber(documentId, document.getCurrentVersion())
                .orElseThrow(() -> new NotFoundException("The document file is unavailable."));
        if (!version.getStorageKey().startsWith("ws/" + workspaceId + "/")) {
            throw new ConflictException("The document does not belong to this workspace.");
        }
        StorageService.StoredObject object = storage.get(version.getStorageKey());
        audit.record(workspaceId, download ? AuditAction.DOCUMENT_DOWNLOADED : AuditAction.DOCUMENT_PREVIEWED,
                "DOCUMENT", documentId, document.getFileName());
        return new StoredDocument(object.stream(), object.size(), version.getContentType(), version.getFileName());
    }

    private void cleanup(List<String> keys) {
        for (String key : keys) {
            try {
                storage.delete(key);
            } catch (RuntimeException ignored) {
                // Best effort compensation for a failed multi-file upload.
            }
        }
    }

    private String storageKey(UUID workspaceId, UUID documentId, int version, String extension) {
        return "ws/" + workspaceId + "/documents/" + documentId + "/v" + version + "/"
                + UUID.randomUUID() + "." + extension;
    }

    private DocumentResponse toResponse(DocumentEntity document, Map<UUID, String> userNames,
            Map<UUID, String> templateNames) {
        return new DocumentResponse(
                document.getId(),
                document.getFileName(),
                document.getTitle(),
                document.getDescription(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getPages(),
                document.getCategory(),
                document.getStatus(),
                document.getProgress(),
                document.getValidationStatus(),
                document.getTemplateId(),
                document.getTemplateId() == null ? null : templateNames.get(document.getTemplateId()),
                document.getCurrentVersion(),
                document.getUploadedBy(),
                document.getUploadedBy() == null ? null : userNames.get(document.getUploadedBy()),
                document.getChecksum(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    private Map<UUID, String> uploaderNames(List<DocumentEntity> documentList) {
        List<UUID> ids = documentList.stream()
                .map(DocumentEntity::getUploadedBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(ids).stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getFullName));
    }

    private Map<UUID, String> uploaderNamesFromVersions(List<DocumentVersionEntity> versionList) {
        List<UUID> ids = versionList.stream()
                .map(DocumentVersionEntity::getUploadedBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(ids).stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getFullName));
    }

    private Map<UUID, String> templateNames(UUID workspaceId, List<DocumentEntity> documentList) {
        List<UUID> ids = documentList.stream()
                .map(DocumentEntity::getTemplateId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return templates.findAllByWorkspaceIdAndIdIn(workspaceId, ids).stream()
                .collect(Collectors.toMap(DocumentTemplateEntity::getId, DocumentTemplateEntity::getName));
    }

    private Sort sort(String sortBy, String direction) {
        String property = switch (sortBy == null ? "" : sortBy) {
            case "fileName" -> "fileName";
            case "sizeBytes" -> "sizeBytes";
            case "status" -> "status";
            case "updatedAt" -> "updatedAt";
            case "createdAt", "uploadedAt" -> "createdAt";
            default -> "createdAt";
        };
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(sortDirection, property);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
