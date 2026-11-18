package com.documind.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.documind.audit.AuditAction;
import com.documind.audit.AuditService;
import com.documind.common.BadRequestException;
import com.documind.document.DocumentCategory;
import com.documind.document.DocumentEntity;
import com.documind.document.DocumentRepository;
import com.documind.document.DocumentService;
import com.documind.document.DocumentValidationStatus;
import com.documind.extraction.ExtractedFieldRepository;
import com.documind.security.AuthenticatedUser;
import com.documind.security.CurrentUserProvider;
import com.documind.template.TemplateFieldRepository;
import com.documind.user.RoleName;
import com.documind.user.UserRepository;
import com.documind.validation.dto.ValidationDecisionRequest;
import com.documind.validation.dto.ValidationDetailResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ValidationServiceTest {

    @Mock
    private DocumentValidationRepository validations;
    @Mock
    private ValidationCommentRepository comments;
    @Mock
    private DocumentRepository documents;
    @Mock
    private UserRepository users;
    @Mock
    private ExtractedFieldRepository extractedFields;
    @Mock
    private TemplateFieldRepository templateFields;
    @Mock
    private DocumentService documentService;
    @Mock
    private AuditService audit;
    @Mock
    private CurrentUserProvider currentUser;

    private ValidationService service;
    private UUID workspaceId;
    private UUID documentId;
    private UUID actorId;
    private DocumentEntity document;

    @BeforeEach
    void setUp() {
        service = new ValidationService(validations, comments, documents, users, extractedFields,
                templateFields, documentService, audit, currentUser);
        workspaceId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        document = new DocumentEntity(workspaceId, "contract.pdf", "application/pdf", 4096,
                DocumentCategory.CONTRACT, null, UUID.randomUUID());
        document.markCompleted(3, "checksum");
        documentId = document.getId();
    }

    @Test
    void approveMarksTheDocumentAsApproved() {
        DocumentValidationEntity validation = pendingValidation();
        when(validations.findByIdAndWorkspaceId(validation.getId(), workspaceId))
                .thenReturn(Optional.of(validation));
        when(documents.findByIdAndWorkspaceId(documentId, workspaceId)).thenReturn(Optional.of(document));
        when(documents.findById(documentId)).thenReturn(Optional.of(document));
        when(currentUser.require()).thenReturn(new AuthenticatedUser(actorId, "validator@documind.test",
                "Vera Validator", RoleName.VALIDATOR));

        ValidationDetailResponse response = service.approve(workspaceId, validation.getId(),
                new ValidationDecisionRequest("All good"));

        assertThat(response.validation().status()).isEqualTo(ValidationStatus.APPROVED);
        assertThat(response.validation().decidedBy()).isEqualTo(actorId);
        assertThat(validation.getDecisionComment()).isEqualTo("All good");
        assertThat(document.getValidationStatus()).isEqualTo(DocumentValidationStatus.APPROVED);
        verify(audit).record(eq(workspaceId), eq(AuditAction.VALIDATION_APPROVED), eq("DOCUMENT"),
                eq(documentId), anyString());
    }

    @Test
    void rejectRequiresAComment() {
        DocumentValidationEntity validation = pendingValidation();
        when(validations.findByIdAndWorkspaceId(validation.getId(), workspaceId))
                .thenReturn(Optional.of(validation));

        assertThatThrownBy(() -> service.reject(workspaceId, validation.getId(),
                new ValidationDecisionRequest("   ")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("rejection comment");
        assertThat(validation.getStatus()).isEqualTo(ValidationStatus.PENDING);
        verify(validations, never()).save(validation);
    }

    private DocumentValidationEntity pendingValidation() {
        return new DocumentValidationEntity(workspaceId, documentId, 1, UUID.randomUUID());
    }
}
