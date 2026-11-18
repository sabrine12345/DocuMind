package com.documind.extraction;

import com.documind.audit.AuditAction;
import com.documind.audit.AuditService;
import com.documind.common.BadRequestException;
import com.documind.common.ConflictException;
import com.documind.common.NotFoundException;
import com.documind.document.DocumentEntity;
import com.documind.document.DocumentRepository;
import com.documind.document.DocumentService;
import com.documind.document.DocumentValidationStatus;
import com.documind.extraction.dto.ExtractedFieldResponse;
import com.documind.extraction.dto.FieldValidationRequest;
import com.documind.extraction.dto.UpdateFieldRequest;
import com.documind.security.CurrentUserProvider;
import com.documind.template.DocumentTemplateEntity;
import com.documind.template.DocumentTemplateRepository;
import com.documind.user.UserEntity;
import com.documind.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExtractionService {

    private final ExtractedFieldRepository fields;
    private final DocumentRepository documents;
    private final DocumentTemplateRepository templates;
    private final UserRepository users;
    private final DocumentService documentService;
    private final FieldPreparationService preparation;
    private final AuditService audit;
    private final CurrentUserProvider currentUser;

    public ExtractionService(ExtractedFieldRepository fields, DocumentRepository documents,
            DocumentTemplateRepository templates, UserRepository users, DocumentService documentService,
            FieldPreparationService preparation, AuditService audit, CurrentUserProvider currentUser) {
        this.fields = fields;
        this.documents = documents;
        this.templates = templates;
        this.users = users;
        this.documentService = documentService;
        this.preparation = preparation;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<ExtractedFieldResponse> list(UUID workspaceId, UUID documentId) {
        documentService.requireDocument(workspaceId, documentId);
        return toResponses(fields.findAllByDocumentIdOrderByFieldLabelAsc(documentId));
    }

    /**
     * Prepares the field structure from the document template. Existing fields
     * and values are preserved, so this endpoint is safe to call repeatedly.
     */
    @Transactional
    public List<ExtractedFieldResponse> prepare(UUID workspaceId, UUID documentId, UUID templateId) {
        DocumentEntity document = documentService.requireDocument(workspaceId, documentId);
        if (document.getValidationStatus() == DocumentValidationStatus.PENDING) {
            throw new ConflictException("Extraction fields cannot change while a validation is pending.");
        }
        if (templateId != null && !templateId.equals(document.getTemplateId())) {
            DocumentTemplateEntity template = templates.findByIdAndWorkspaceId(templateId, workspaceId)
                    .orElseThrow(() -> new NotFoundException("Template not found."));
            if (fields.existsByDocumentId(documentId)) {
                throw new ConflictException("The template cannot be changed once extraction fields exist.");
            }
            document.assignTemplate(template.getId());
            documents.save(document);
        }
        if (document.getTemplateId() == null) {
            throw new BadRequestException("Select a template before preparing extraction fields.");
        }
        return toResponses(preparation.prepare(workspaceId, documentId));
    }

    @Transactional
    public ExtractedFieldResponse updateValue(UUID workspaceId, UUID fieldId, UpdateFieldRequest request) {
        ExtractedFieldEntity field = requireField(workspaceId, fieldId);
        documentService.requireDocument(workspaceId, field.getDocumentId());
        String value = normalize(request.value());
        validateFormat(field, value);
        field.updateValue(value, currentUser.id());
        fields.save(field);
        audit.record(workspaceId, AuditAction.FIELD_UPDATED, "DOCUMENT", field.getDocumentId(),
                field.getFieldLabel() + " updated");
        return toResponse(field, editorNames(List.of(field)));
    }

    @Transactional
    public ExtractedFieldResponse validate(UUID workspaceId, UUID fieldId, FieldValidationRequest request) {
        ExtractedFieldEntity field = requireField(workspaceId, fieldId);
        documentService.requireDocument(workspaceId, field.getDocumentId());
        boolean validated = request == null || request.validated() == null || request.validated();
        UUID actorId = currentUser.id();
        if (validated) {
            if (!hasText(field.getValue())) {
                throw new BadRequestException("Provide a value for " + field.getFieldLabel()
                        + " before validating it.");
            }
            field.validateValue(actorId);
        } else {
            field.reopen(actorId);
        }
        fields.save(field);
        audit.record(workspaceId, AuditAction.FIELD_VALIDATED, "DOCUMENT", field.getDocumentId(),
                field.getFieldLabel() + (validated ? " validated" : " reopened"));
        return toResponse(field, editorNames(List.of(field)));
    }

    /**
     * Validates every field that currently holds a value. Fields without a
     * value are left untouched.
     */
    @Transactional
    public List<ExtractedFieldResponse> validateAll(UUID workspaceId, UUID documentId) {
        documentService.requireDocument(workspaceId, documentId);
        List<ExtractedFieldEntity> documentFields = fields.findAllByDocumentIdOrderByFieldLabelAsc(documentId);
        List<ExtractedFieldEntity> filled = documentFields.stream()
                .filter(field -> hasText(field.getValue()))
                .toList();
        if (filled.isEmpty()) {
            throw new BadRequestException("No field value is available to validate.");
        }
        UUID actorId = currentUser.id();
        filled.forEach(field -> field.validateValue(actorId));
        fields.saveAll(filled);
        audit.record(workspaceId, AuditAction.FIELD_VALIDATED, "DOCUMENT", documentId,
                filled.size() + " field(s) validated");
        return toResponses(documentFields);
    }

    private ExtractedFieldEntity requireField(UUID workspaceId, UUID fieldId) {
        return fields.findByIdAndWorkspaceId(fieldId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Extraction field not found."));
    }

    /**
     * Rejects values that do not match the field data type so the validation
     * module always works on clean data.
     */
    private void validateFormat(ExtractedFieldEntity field, String value) {
        if (value == null) {
            return;
        }
        switch (field.getDataType()) {
            case NUMBER, CURRENCY -> parseDecimal(field, value);
            case DATE -> {
                try {
                    LocalDate.parse(value);
                } catch (DateTimeParseException ex) {
                    throw new BadRequestException(field.getFieldLabel()
                            + " must be a valid ISO date (yyyy-MM-dd).");
                }
            }
            case BOOLEAN -> {
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                    throw new BadRequestException(field.getFieldLabel() + " must be true or false.");
                }
            }
            default -> {
                // Free text: no format constraint.
            }
        }
    }

    private void parseDecimal(ExtractedFieldEntity field, String value) {
        String normalized = value.replace(" ", "").replace("\u00A0", "").replace(",", ".");
        try {
            new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            throw new BadRequestException(field.getFieldLabel() + " must be a valid number.");
        }
    }

    private List<ExtractedFieldResponse> toResponses(List<ExtractedFieldEntity> fieldList) {
        Map<UUID, String> names = editorNames(fieldList);
        return fieldList.stream().map(field -> toResponse(field, names)).toList();
    }

    private Map<UUID, String> editorNames(List<ExtractedFieldEntity> fieldList) {
        List<UUID> ids = fieldList.stream()
                .map(ExtractedFieldEntity::getEditedBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(ids).stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getFullName));
    }

    private ExtractedFieldResponse toResponse(ExtractedFieldEntity field, Map<UUID, String> names) {
        return new ExtractedFieldResponse(
                field.getId(),
                field.getDocumentId(),
                field.getTemplateFieldId(),
                field.getFieldName(),
                field.getFieldLabel(),
                field.getDataType(),
                field.getValue(),
                field.getOriginalValue(),
                field.getConfidence(),
                field.getSourcePage(),
                field.getStatus(),
                field.getEditedBy(),
                field.getEditedBy() == null ? null : names.get(field.getEditedBy()),
                field.getEditedAt()
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
