package com.documind.validation;

import com.documind.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One validation request for a document version. A rejected or approved
 * request is immutable; a new submission creates a new row so the decision
 * history is preserved.
 */
@Entity
@Table(name = "document_validations")
public class DocumentValidationEntity extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "document_version", nullable = false)
    private int documentVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ValidationStatus status = ValidationStatus.PENDING;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_comment", length = 2000)
    private String decisionComment;

    @Column(name = "created_by")
    private UUID createdBy;

    protected DocumentValidationEntity() {
    }

    public DocumentValidationEntity(UUID workspaceId, UUID documentId, int documentVersion, UUID submittedBy) {
        this.workspaceId = workspaceId;
        this.documentId = documentId;
        this.documentVersion = documentVersion;
        this.status = ValidationStatus.PENDING;
        this.submittedBy = submittedBy;
        this.submittedAt = Instant.now();
        this.createdBy = submittedBy;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getDocumentVersion() {
        return documentVersion;
    }

    public ValidationStatus getStatus() {
        return status;
    }

    public UUID getSubmittedBy() {
        return submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionComment() {
        return decisionComment;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void approve(UUID decidedBy, String comment) {
        this.status = ValidationStatus.APPROVED;
        this.decidedBy = decidedBy;
        this.decidedAt = Instant.now();
        this.decisionComment = comment;
    }

    public void reject(UUID decidedBy, String comment) {
        this.status = ValidationStatus.REJECTED;
        this.decidedBy = decidedBy;
        this.decidedAt = Instant.now();
        this.decisionComment = comment;
    }
}
