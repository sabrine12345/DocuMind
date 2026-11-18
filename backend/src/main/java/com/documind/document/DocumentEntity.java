package com.documind.document;

import com.documind.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class DocumentEntity extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(length = 255)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private int pages = 1;

    @Column(length = 64)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DocumentCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.QUEUED;

    @Column(nullable = false)
    private int progress;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 20)
    private DocumentValidationStatus validationStatus = DocumentValidationStatus.NONE;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "current_version", nullable = false)
    private int currentVersion = 1;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    protected DocumentEntity() {
    }

    public DocumentEntity(UUID workspaceId, String fileName, String contentType, long sizeBytes,
            DocumentCategory category, UUID templateId, UUID uploadedBy) {
        this.workspaceId = workspaceId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.category = category;
        this.templateId = templateId;
        this.uploadedBy = uploadedBy;
        this.createdBy = uploadedBy;
        this.status = DocumentStatus.QUEUED;
        this.progress = 0;
        this.pages = 1;
        this.currentVersion = 1;
        this.validationStatus = DocumentValidationStatus.NONE;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public int getPages() {
        return pages;
    }

    public String getChecksum() {
        return checksum;
    }

    public DocumentCategory getCategory() {
        return category;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public int getProgress() {
        return progress;
    }

    public DocumentValidationStatus getValidationStatus() {
        return validationStatus;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void applyMetadata(String title, String description, DocumentCategory category, UUID templateId) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.templateId = templateId;
    }

    public void assignTemplate(UUID templateId) {
        this.templateId = templateId;
    }

    public void markQueued(int version) {
        this.status = DocumentStatus.QUEUED;
        this.progress = 0;
        this.currentVersion = version;
    }

    public void markProcessing(int progress) {
        this.status = DocumentStatus.PROCESSING;
        this.progress = Math.max(0, Math.min(progress, 99));
    }

    public void updateProgress(int progress) {
        this.progress = Math.max(0, Math.min(progress, 99));
    }

    public void markCompleted(int pages, String checksum) {
        this.status = DocumentStatus.COMPLETED;
        this.progress = 100;
        if (pages > 0) {
            this.pages = pages;
        }
        if (checksum != null) {
            this.checksum = checksum;
        }
    }

    public void markFailed(String reason) {
        this.status = DocumentStatus.FAILED;
        if (progress >= 100) {
            this.progress = 99;
        }
    }

    public void replaceFile(String fileName, String contentType, long sizeBytes) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    public void setValidationStatus(DocumentValidationStatus validationStatus) {
        this.validationStatus = validationStatus;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }
}
