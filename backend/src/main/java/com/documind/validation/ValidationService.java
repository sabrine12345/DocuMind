package com.documind.validation;

import com.documind.audit.AuditAction;
import com.documind.audit.AuditService;
import com.documind.common.BadRequestException;
import com.documind.common.ConflictException;
import com.documind.common.NotFoundException;
import com.documind.common.PageResponse;
import com.documind.document.DocumentEntity;
import com.documind.document.DocumentRepository;
import com.documind.document.DocumentService;
import com.documind.document.DocumentStatus;
import com.documind.document.DocumentValidationStatus;
import com.documind.extraction.ExtractedFieldEntity;
import com.documind.extraction.ExtractedFieldRepository;
import com.documind.security.AuthenticatedUser;
import com.documind.security.CurrentUserProvider;
import com.documind.template.TemplateFieldEntity;
import com.documind.template.TemplateFieldRepository;
import com.documind.user.UserEntity;
import com.documind.user.UserRepository;
import com.documind.validation.dto.AddCommentRequest;
import com.documind.validation.dto.CreateValidationRequest;
import com.documind.validation.dto.ValidationCommentResponse;
import com.documind.validation.dto.ValidationDecisionRequest;
import com.documind.validation.dto.ValidationDetailResponse;
import com.documind.validation.dto.ValidationResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ValidationService {

    private final DocumentValidationRepository validations;
    private final ValidationCommentRepository comments;
    private final DocumentRepository documents;
    private final UserRepository users;
    private final ExtractedFieldRepository extractedFields;
    private final TemplateFieldRepository templateFields;
    private final DocumentService documentService;
    private final AuditService audit;
    private final CurrentUserProvider currentUser;

    public ValidationService(DocumentValidationRepository validations, ValidationCommentRepository comments,
            DocumentRepository documents, UserRepository users, ExtractedFieldRepository extractedFields,
            TemplateFieldRepository templateFields, DocumentService documentService, AuditService audit,
            CurrentUserProvider currentUser) {
        this.validations = validations;
        this.comments = comments;
        this.documents = documents;
        this.users = users;
        this.extractedFields = extractedFields;
        this.templateFields = templateFields;
        this.documentService = documentService;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Transactional
    public ValidationDetailResponse submit(UUID workspaceId, UUID documentId, CreateValidationRequest request) {
        DocumentEntity document = documentService.requireDocument(workspaceId, documentId);
        if (document.getStatus() != DocumentStatus.COMPLETED) {
            throw new ConflictException("Only documents that finished processing can be submitted for validation.");
        }
        if (document.getValidationStatus() == DocumentValidationStatus.PENDING
                || validations.existsByDocumentIdAndStatus(documentId, ValidationStatus.PENDING)) {
            throw new ConflictException("This document is already pending validation.");
        }
        ensureRequiredFieldsFilled(document);
        AuthenticatedUser actor = currentUser.require();
        DocumentValidationEntity validation = validations.save(new DocumentValidationEntity(
                workspaceId, documentId, document.getCurrentVersion(), actor.id()));
        document.setValidationStatus(DocumentValidationStatus.PENDING);
        documents.save(document);
        if (request != null && hasText(request.comment())) {
            comments.save(new ValidationCommentEntity(workspaceId, validation.getId(), actor.id(),
                    actor.fullName(), request.comment().trim()));
        }
        audit.record(workspaceId, AuditAction.VALIDATION_SUBMITTED, "DOCUMENT", documentId,
                "Version " + document.getCurrentVersion() + " of " + document.getFileName()
                        + " submitted for validation");
        return toDetail(validation);
    }

    @Transactional(readOnly = true)
    public PageResponse<ValidationResponse> list(UUID workspaceId, ValidationStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<DocumentValidationEntity> result = status == null
                ? validations.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageable)
                : validations.findAllByWorkspaceIdAndStatusOrderByCreatedAtDesc(workspaceId, status, pageable);
        Map<UUID, DocumentEntity> documentsById = documentsById(result.getContent());
        Map<UUID, String> names = userNames(result.getContent());
        Map<UUID, Long> commentCounts = commentCounts(result.getContent());
        return PageResponse.of(result, validation -> toResponse(validation,
                documentsById.get(validation.getDocumentId()), names,
                commentCounts.getOrDefault(validation.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public ValidationDetailResponse get(UUID workspaceId, UUID validationId) {
        return toDetail(require(workspaceId, validationId));
    }

    @Transactional(readOnly = true)
    public List<ValidationResponse> forDocument(UUID workspaceId, UUID documentId) {
        documentService.requireDocument(workspaceId, documentId);
        List<DocumentValidationEntity> history = validations
                .findAllByWorkspaceIdAndDocumentIdOrderByCreatedAtDesc(workspaceId, documentId);
        Map<UUID, DocumentEntity> documentsById = documentsById(history);
        Map<UUID, String> names = userNames(history);
        Map<UUID, Long> commentCounts = commentCounts(history);
        return history.stream()
                .map(validation -> toResponse(validation, documentsById.get(validation.getDocumentId()), names,
                        commentCounts.getOrDefault(validation.getId(), 0L)))
                .toList();
    }

    @Transactional
    public ValidationDetailResponse approve(UUID workspaceId, UUID validationId, ValidationDecisionRequest request) {
        DocumentValidationEntity validation = require(workspaceId, validationId);
        if (validation.getStatus() != ValidationStatus.PENDING) {
            throw new ConflictException("This validation has already been decided.");
        }
        DocumentEntity document = requireCurrentVersion(validation);
        AuthenticatedUser actor = currentUser.require();
        validation.approve(actor.id(), normalize(request == null ? null : request.comment()));
        validations.save(validation);
        document.setValidationStatus(DocumentValidationStatus.APPROVED);
        documents.save(document);
        audit.record(workspaceId, AuditAction.VALIDATION_APPROVED, "DOCUMENT", document.getId(),
                "Version " + validation.getDocumentVersion() + " of " + document.getFileName() + " approved");
        return toDetail(validation);
    }

    @Transactional
    public ValidationDetailResponse reject(UUID workspaceId, UUID validationId, ValidationDecisionRequest request) {
        DocumentValidationEntity validation = require(workspaceId, validationId);
        if (validation.getStatus() != ValidationStatus.PENDING) {
            throw new ConflictException("This validation has already been decided.");
        }
        String comment = normalize(request == null ? null : request.comment());
        if (comment == null) {
            throw new BadRequestException("A rejection comment is required.");
        }
        DocumentEntity document = requireCurrentVersion(validation);
        AuthenticatedUser actor = currentUser.require();
        validation.reject(actor.id(), comment);
        validations.save(validation);
        document.setValidationStatus(DocumentValidationStatus.REJECTED);
        documents.save(document);
        audit.record(workspaceId, AuditAction.VALIDATION_REJECTED, "DOCUMENT", document.getId(),
                "Version " + validation.getDocumentVersion() + " of " + document.getFileName()
                        + " rejected: " + comment);
        return toDetail(validation);
    }

    @Transactional
    public ValidationCommentResponse addComment(UUID workspaceId, UUID validationId, AddCommentRequest request) {
        DocumentValidationEntity validation = require(workspaceId, validationId);
        String message = normalize(request.message());
        if (message == null) {
            throw new BadRequestException("A comment message is required.");
        }
        AuthenticatedUser actor = currentUser.require();
        ValidationCommentEntity comment = comments.save(new ValidationCommentEntity(workspaceId,
                validation.getId(), actor.id(), actor.fullName(), message));
        audit.record(workspaceId, AuditAction.VALIDATION_COMMENTED, "DOCUMENT", validation.getDocumentId(),
                "Comment added to validation " + validation.getId());
        return ValidationCommentResponse.from(comment);
    }

    @Transactional(readOnly = true)
    public List<ValidationCommentResponse> listComments(UUID workspaceId, UUID validationId) {
        require(workspaceId, validationId);
        return comments.findAllByValidationIdOrderByCreatedAtAsc(validationId).stream()
                .map(ValidationCommentResponse::from)
                .toList();
    }

    private DocumentValidationEntity require(UUID workspaceId, UUID validationId) {
        return validations.findByIdAndWorkspaceId(validationId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Validation not found."));
    }

    private DocumentEntity requireCurrentVersion(DocumentValidationEntity validation) {
        DocumentEntity document = documents
                .findByIdAndWorkspaceId(validation.getDocumentId(), validation.getWorkspaceId())
                .orElseThrow(() -> new NotFoundException("Document not found."));
        if (document.isDeleted()) {
            throw new NotFoundException("Document not found.");
        }
        if (document.getCurrentVersion() != validation.getDocumentVersion()) {
            throw new ConflictException("This validation targets version " + validation.getDocumentVersion()
                    + " but the document is now at version " + document.getCurrentVersion() + ".");
        }
        return document;
    }

    /**
     * A document can only be submitted when every required template field has
     * a value. The platform never fills fields on behalf of the user.
     */
    private void ensureRequiredFieldsFilled(DocumentEntity document) {
        if (document.getTemplateId() == null) {
            return;
        }
        List<TemplateFieldEntity> definitions = templateFields.findAllForTemplate(document.getTemplateId());
        if (definitions.isEmpty()) {
            return;
        }
        Map<String, ExtractedFieldEntity> byName = extractedFields
                .findAllByDocumentIdOrderByFieldLabelAsc(document.getId()).stream()
                .collect(Collectors.toMap(
                        field -> field.getFieldName().toLowerCase(Locale.ROOT),
                        Function.identity(),
                        (left, right) -> left));
        List<String> missing = definitions.stream()
                .filter(TemplateFieldEntity::isRequired)
                .filter(definition -> {
                    ExtractedFieldEntity field = byName.get(definition.getName().toLowerCase(Locale.ROOT));
                    return field == null || field.getValue() == null || field.getValue().isBlank();
                })
                .map(TemplateFieldEntity::getLabel)
                .toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException("Complete the required extraction fields before submitting: "
                    + String.join(", ", missing) + ".");
        }
    }

    private ValidationDetailResponse toDetail(DocumentValidationEntity validation) {
        DocumentEntity document = documents.findById(validation.getDocumentId()).orElse(null);
        List<ValidationCommentEntity> thread = comments
                .findAllByValidationIdOrderByCreatedAtAsc(validation.getId());
        ValidationResponse response = toResponse(validation, document, userNames(List.of(validation)),
                thread.size());
        return new ValidationDetailResponse(response,
                thread.stream().map(ValidationCommentResponse::from).toList());
    }

    private Map<UUID, DocumentEntity> documentsById(List<DocumentValidationEntity> validationList) {
        List<UUID> ids = validationList.stream()
                .map(DocumentValidationEntity::getDocumentId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return documents.findAllById(ids).stream()
                .collect(Collectors.toMap(DocumentEntity::getId, Function.identity()));
    }

    private Map<UUID, String> userNames(List<DocumentValidationEntity> validationList) {
        List<UUID> ids = validationList.stream()
                .flatMap(validation -> Stream.of(validation.getSubmittedBy(), validation.getDecidedBy()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(ids).stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getFullName));
    }

    private Map<UUID, Long> commentCounts(List<DocumentValidationEntity> validationList) {
        List<UUID> ids = validationList.stream().map(DocumentValidationEntity::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return comments.countByValidationIds(ids).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> ((Number) row[1]).longValue()));
    }

    private ValidationResponse toResponse(DocumentValidationEntity validation, DocumentEntity document,
            Map<UUID, String> names, long commentCount) {
        return new ValidationResponse(
                validation.getId(),
                validation.getDocumentId(),
                document == null ? null : document.getFileName(),
                document == null ? null : document.getCategory(),
                validation.getDocumentVersion(),
                validation.getStatus(),
                validation.getSubmittedBy(),
                validation.getSubmittedBy() == null ? null : names.get(validation.getSubmittedBy()),
                validation.getSubmittedAt(),
                validation.getDecidedBy(),
                validation.getDecidedBy() == null ? null : names.get(validation.getDecidedBy()),
                validation.getDecidedAt(),
                validation.getDecisionComment(),
                commentCount,
                validation.getCreatedAt(),
                validation.getUpdatedAt()
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
