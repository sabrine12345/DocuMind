package com.documind.document;

import com.documind.common.BaseEntity;
import com.documind.storage.StorageProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "document_versions")
public class DocumentVersionEntity extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(length = 64)
    private String checksum;

    /** Internal object key, never exposed through the API. */
    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false, length = 20)
    private StorageProvider storageProvider;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    protected DocumentVersionEntity() {
    }

    public DocumentVersionEntity(UUID workspaceId, UUID documentId, int versionNumber, String fileName,
            String contentType, long sizeBytes, String storageKey, StorageProvider storageProvider, UUID uploadedBy) {
        this.workspaceId = workspaceId;
        this.documentId = documentId;
        this.versionNumber = versionNumber;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.storageProvider = storageProvider;
        this.uploadedBy = uploadedBy;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getChecksum() {
        return checksum;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public StorageProvider getStorageProvider() {
        return storageProvider;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }
}
