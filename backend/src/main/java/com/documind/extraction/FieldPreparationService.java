package com.documind.extraction;

import com.documind.audit.AuditAction;
import com.documind.audit.AuditService;
import com.documind.document.DocumentEntity;
import com.documind.document.DocumentRepository;
import com.documind.template.DocumentTemplateEntity;
import com.documind.template.DocumentTemplateRepository;
import com.documind.template.TemplateFieldEntity;
import com.documind.template.TemplateFieldRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates empty extraction fields from the document template.
 *
 * It runs automatically when processing completes and can be replayed safely:
 * existing fields and their values are never overwritten or deleted. This
 * service intentionally depends on repositories only so the processing worker
 * can call it without creating a bean cycle.
 */
@Service
public class FieldPreparationService {

    private final ExtractedFieldRepository fields;
    private final DocumentRepository documents;
    private final DocumentTemplateRepository templates;
    private final TemplateFieldRepository templateFields;
    private final AuditService audit;

    public FieldPreparationService(ExtractedFieldRepository fields, DocumentRepository documents,
            DocumentTemplateRepository templates, TemplateFieldRepository templateFields, AuditService audit) {
        this.fields = fields;
        this.documents = documents;
        this.templates = templates;
        this.templateFields = templateFields;
        this.audit = audit;
    }

    @Transactional
    public List<ExtractedFieldEntity> prepare(UUID workspaceId, UUID documentId) {
        DocumentEntity document = documents.findByIdAndWorkspaceId(documentId, workspaceId).orElse(null);
        if (document == null || document.isDeleted() || document.getTemplateId() == null) {
            return List.of();
        }
        DocumentTemplateEntity template = templates
                .findByIdAndWorkspaceId(document.getTemplateId(), workspaceId)
                .orElse(null);
        if (template == null) {
            return List.of();
        }
        List<TemplateFieldEntity> definitions = templateFields.findAllForTemplate(template.getId());
        if (definitions.isEmpty()) {
            return List.of();
        }
        Set<String> existing = new HashSet<>();
        for (ExtractedFieldEntity field : fields.findAllByDocumentIdOrderByFieldLabelAsc(documentId)) {
            existing.add(field.getFieldName().toLowerCase(Locale.ROOT));
        }
        List<ExtractedFieldEntity> created = new ArrayList<>();
        for (TemplateFieldEntity definition : definitions) {
            if (existing.contains(definition.getName().toLowerCase(Locale.ROOT))) {
                continue;
            }
            created.add(new ExtractedFieldEntity(workspaceId, documentId, definition.getId(),
                    definition.getName(), definition.getLabel(), definition.getDataType(),
                    document.getUploadedBy()));
        }
        if (!created.isEmpty()) {
            fields.saveAll(created);
            audit.record(workspaceId, AuditAction.FIELDS_PREPARED, "DOCUMENT", documentId,
                    created.size() + " field(s) prepared from template '" + template.getName() + "'");
        }
        return fields.findAllByDocumentIdOrderByFieldLabelAsc(documentId);
    }
}
