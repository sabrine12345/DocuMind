package com.documind.template;

import com.documind.audit.AuditAction;
import com.documind.audit.AuditService;
import com.documind.common.BadRequestException;
import com.documind.common.ConflictException;
import com.documind.common.NotFoundException;
import com.documind.document.DocumentCategory;
import com.documind.security.CurrentUserProvider;
import com.documind.template.dto.TemplateFieldRequest;
import com.documind.template.dto.TemplateFieldResponse;
import com.documind.template.dto.TemplateRequest;
import com.documind.template.dto.TemplateResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateService {

    private final DocumentTemplateRepository templates;
    private final TemplateFieldRepository fields;
    private final AuditService audit;
    private final CurrentUserProvider currentUser;

    public TemplateService(DocumentTemplateRepository templates, TemplateFieldRepository fields,
            AuditService audit, CurrentUserProvider currentUser) {
        this.templates = templates;
        this.fields = fields;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> list(UUID workspaceId) {
        List<DocumentTemplateEntity> entities = templates.findAllByWorkspaceIdOrderByNameAsc(workspaceId);
        if (entities.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<TemplateFieldEntity>> fieldsByTemplate = fields
                .findAllForTemplates(entities.stream().map(DocumentTemplateEntity::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(TemplateFieldEntity::getTemplateId));
        return entities.stream()
                .map(template -> toResponse(template, fieldsByTemplate.getOrDefault(template.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse get(UUID workspaceId, UUID templateId) {
        DocumentTemplateEntity template = requireTemplate(workspaceId, templateId);
        return toResponse(template, fields.findAllForTemplate(templateId));
    }

    @Transactional(readOnly = true)
    public DocumentTemplateEntity requireTemplate(UUID workspaceId, UUID templateId) {
        return templates.findByIdAndWorkspaceId(templateId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Template not found."));
    }

    @Transactional(readOnly = true)
    public List<TemplateFieldEntity> fieldsOf(UUID workspaceId, UUID templateId) {
        requireTemplate(workspaceId, templateId);
        return fields.findAllForTemplate(templateId);
    }

    @Transactional
    public TemplateResponse create(UUID workspaceId, TemplateRequest request) {
        String name = request.name().trim();
        if (templates.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, name)) {
            throw new ConflictException("A template with this name already exists.");
        }
        validateFields(request.fields());
        DocumentTemplateEntity template = templates.save(new DocumentTemplateEntity(
                workspaceId, name, request.category(), normalize(request.description()),
                request.active(), currentUser.id()));
        List<TemplateFieldEntity> saved = replaceFields(workspaceId, template.getId(), request.fields());
        audit.record(workspaceId, AuditAction.TEMPLATE_CREATED, "TEMPLATE", template.getId(), name);
        return toResponse(template, saved);
    }

    @Transactional
    public TemplateResponse update(UUID workspaceId, UUID templateId, TemplateRequest request) {
        DocumentTemplateEntity template = requireTemplate(workspaceId, templateId);
        String name = request.name().trim();
        if (templates.existsByWorkspaceIdAndNameIgnoreCaseAndIdNot(workspaceId, name, templateId)) {
            throw new ConflictException("A template with this name already exists.");
        }
        validateFields(request.fields());
        template.setName(name);
        template.setCategory(request.category());
        template.setDescription(normalize(request.description()));
        template.setActive(request.active());
        templates.save(template);
        List<TemplateFieldEntity> saved = replaceFields(workspaceId, templateId, request.fields());
        audit.record(workspaceId, AuditAction.TEMPLATE_UPDATED, "TEMPLATE", templateId, name);
        return toResponse(template, saved);
    }

    @Transactional
    public void delete(UUID workspaceId, UUID templateId) {
        DocumentTemplateEntity template = requireTemplate(workspaceId, templateId);
        fields.deleteAllForTemplate(templateId);
        templates.delete(template);
        audit.record(workspaceId, AuditAction.TEMPLATE_DELETED, "TEMPLATE", templateId, template.getName());
    }

    /**
     * Seeds a new workspace with the standard document templates so the
     * extraction module is usable from day one.
     */
    @Transactional
    public void createDefaultTemplates(UUID workspaceId, UUID createdBy) {
        for (DefaultTemplate seed : DEFAULT_TEMPLATES) {
            DocumentTemplateEntity template = templates.save(new DocumentTemplateEntity(
                    workspaceId, seed.name(), seed.category(), seed.description(), true, createdBy));
            int order = 0;
            for (DefaultField field : seed.fields()) {
                fields.save(new TemplateFieldEntity(workspaceId, template.getId(),
                        field.name(), field.label(), field.dataType(), field.required(), order++));
            }
        }
    }

    private List<TemplateFieldEntity> replaceFields(UUID workspaceId, UUID templateId, List<TemplateFieldRequest> requests) {
        fields.deleteAllForTemplate(templateId);
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<TemplateFieldRequest> ordered = new ArrayList<>(requests);
        ordered.sort(Comparator.comparingInt(TemplateFieldRequest::displayOrder));
        List<TemplateFieldEntity> saved = new ArrayList<>();
        int index = 0;
        for (TemplateFieldRequest request : ordered) {
            saved.add(fields.save(new TemplateFieldEntity(workspaceId, templateId,
                    request.name().trim(), request.label().trim(), request.dataType(), request.required(), index++)));
        }
        return saved;
    }

    private void validateFields(List<TemplateFieldRequest> requests) {
        if (requests == null) {
            return;
        }
        Set<String> names = new HashSet<>();
        for (TemplateFieldRequest request : requests) {
            String normalized = request.name().trim().toLowerCase(Locale.ROOT);
            if (!names.add(normalized)) {
                throw new BadRequestException("Duplicate field name: " + request.name());
            }
        }
    }

    private TemplateResponse toResponse(DocumentTemplateEntity template, List<TemplateFieldEntity> templateFields) {
        List<TemplateFieldResponse> fieldResponses = templateFields.stream()
                .map(field -> new TemplateFieldResponse(field.getId(), field.getName(), field.getLabel(),
                        field.getDataType(), field.isRequired(), field.getDisplayOrder()))
                .toList();
        return new TemplateResponse(template.getId(), template.getName(), template.getCategory(),
                template.getDescription(), template.isActive(), fieldResponses.size(), fieldResponses,
                template.getCreatedAt(), template.getUpdatedAt());
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record DefaultField(String name, String label, FieldDataType dataType, boolean required) {
    }

    private record DefaultTemplate(String name, DocumentCategory category, String description,
            List<DefaultField> fields) {
    }

    private static final List<DefaultTemplate> DEFAULT_TEMPLATES = List.of(
            new DefaultTemplate("Invoice", DocumentCategory.INVOICE,
                    "Standard supplier invoice with amounts and payment terms.",
                    List.of(
                            new DefaultField("invoice_number", "Invoice number", FieldDataType.TEXT, true),
                            new DefaultField("invoice_date", "Invoice date", FieldDataType.DATE, true),
                            new DefaultField("due_date", "Due date", FieldDataType.DATE, false),
                            new DefaultField("vendor_name", "Vendor name", FieldDataType.TEXT, true),
                            new DefaultField("total_amount", "Total amount", FieldDataType.CURRENCY, true),
                            new DefaultField("tax_amount", "Tax amount", FieldDataType.CURRENCY, false),
                            new DefaultField("currency", "Currency", FieldDataType.TEXT, false))),
            new DefaultTemplate("Purchase Order", DocumentCategory.PURCHASE_ORDER,
                    "Purchase order issued to a supplier.",
                    List.of(
                            new DefaultField("po_number", "PO number", FieldDataType.TEXT, true),
                            new DefaultField("order_date", "Order date", FieldDataType.DATE, true),
                            new DefaultField("supplier_name", "Supplier name", FieldDataType.TEXT, true),
                            new DefaultField("delivery_date", "Delivery date", FieldDataType.DATE, false),
                            new DefaultField("total_amount", "Total amount", FieldDataType.CURRENCY, true))),
            new DefaultTemplate("Contract", DocumentCategory.CONTRACT,
                    "Commercial contract with parties and validity period.",
                    List.of(
                            new DefaultField("contract_number", "Contract number", FieldDataType.TEXT, false),
                            new DefaultField("contract_type", "Contract type", FieldDataType.TEXT, false),
                            new DefaultField("party_name", "Counterparty", FieldDataType.TEXT, true),
                            new DefaultField("start_date", "Start date", FieldDataType.DATE, true),
                            new DefaultField("end_date", "End date", FieldDataType.DATE, false),
                            new DefaultField("contract_value", "Contract value", FieldDataType.CURRENCY, false))),
            new DefaultTemplate("Bank Statement", DocumentCategory.BANK_STATEMENT,
                    "Bank account statement with balances for a period.",
                    List.of(
                            new DefaultField("account_holder", "Account holder", FieldDataType.TEXT, true),
                            new DefaultField("account_number", "Account number", FieldDataType.TEXT, false),
                            new DefaultField("period_start", "Period start", FieldDataType.DATE, true),
                            new DefaultField("period_end", "Period end", FieldDataType.DATE, true),
                            new DefaultField("opening_balance", "Opening balance", FieldDataType.CURRENCY, false),
                            new DefaultField("closing_balance", "Closing balance", FieldDataType.CURRENCY, false))),
            new DefaultTemplate("Receipt", DocumentCategory.RECEIPT,
                    "Proof of payment or expense receipt.",
                    List.of(
                            new DefaultField("merchant_name", "Merchant", FieldDataType.TEXT, true),
                            new DefaultField("receipt_date", "Receipt date", FieldDataType.DATE, true),
                            new DefaultField("total_amount", "Total amount", FieldDataType.CURRENCY, true),
                            new DefaultField("payment_method", "Payment method", FieldDataType.TEXT, false))),
            new DefaultTemplate("Supplier Document", DocumentCategory.SUPPLIER_DOCUMENT,
                    "Supplier master data document such as a certificate or registration form.",
                    List.of(
                            new DefaultField("supplier_name", "Supplier name", FieldDataType.TEXT, true),
                            new DefaultField("supplier_id", "Supplier identifier", FieldDataType.TEXT, false),
                            new DefaultField("document_type", "Document type", FieldDataType.TEXT, false),
                            new DefaultField("issue_date", "Issue date", FieldDataType.DATE, false),
                            new DefaultField("expiry_date", "Expiry date", FieldDataType.DATE, false)))
    );
}
