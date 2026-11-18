package com.documind.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.documind.audit.AuditAction;
import com.documind.audit.AuditService;
import com.documind.common.BadRequestException;
import com.documind.document.DocumentCategory;
import com.documind.document.DocumentEntity;
import com.documind.document.DocumentRepository;
import com.documind.document.DocumentService;
import com.documind.extraction.dto.ExtractedFieldResponse;
import com.documind.extraction.dto.UpdateFieldRequest;
import com.documind.security.CurrentUserProvider;
import com.documind.template.DocumentTemplateRepository;
import com.documind.template.FieldDataType;
import com.documind.user.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExtractionServiceTest {

    @Mock
    private ExtractedFieldRepository fields;
    @Mock
    private DocumentRepository documents;
    @Mock
    private DocumentTemplateRepository templates;
    @Mock
    private UserRepository users;
    @Mock
    private DocumentService documentService;
    @Mock
    private FieldPreparationService preparation;
    @Mock
    private AuditService audit;
    @Mock
    private CurrentUserProvider currentUser;

    private ExtractionService service;
    private UUID workspaceId;
    private UUID documentId;
    private DocumentEntity document;

    @BeforeEach
    void setUp() {
        service = new ExtractionService(fields, documents, templates, users, documentService, preparation,
                audit, currentUser);
        workspaceId = UUID.randomUUID();
        documentId = UUID.randomUUID();
        document = new DocumentEntity(workspaceId, "invoice.pdf", "application/pdf", 2048,
                DocumentCategory.INVOICE, null, UUID.randomUUID());
    }

    @Test
    void rejectsValuesThatDoNotMatchTheFieldType() {
        ExtractedFieldEntity field = currencyField();
        when(fields.findByIdAndWorkspaceId(field.getId(), workspaceId)).thenReturn(Optional.of(field));
        when(documentService.requireDocument(workspaceId, documentId)).thenReturn(document);

        assertThatThrownBy(() -> service.updateValue(workspaceId, field.getId(),
                new UpdateFieldRequest("twelve euros")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("valid number");
    }

    @Test
    void storesValueAndMarksFieldEdited() {
        ExtractedFieldEntity field = currencyField();
        UUID actorId = UUID.randomUUID();
        when(fields.findByIdAndWorkspaceId(field.getId(), workspaceId)).thenReturn(Optional.of(field));
        when(documentService.requireDocument(workspaceId, documentId)).thenReturn(document);
        when(currentUser.id()).thenReturn(actorId);

        ExtractedFieldResponse response = service.updateValue(workspaceId, field.getId(),
                new UpdateFieldRequest("  1 250,50 "));

        assertThat(response.value()).isEqualTo("1 250,50");
        assertThat(response.status()).isEqualTo(ExtractionFieldStatus.EDITED);
        assertThat(response.editedBy()).isEqualTo(actorId);
        verify(fields).save(field);
        verify(audit).record(eq(workspaceId), eq(AuditAction.FIELD_UPDATED), eq("DOCUMENT"), eq(documentId),
                contains("Total amount"));
    }

    private ExtractedFieldEntity currencyField() {
        return new ExtractedFieldEntity(workspaceId, documentId, null, "total_amount", "Total amount",
                FieldDataType.CURRENCY, null);
    }
}
