package com.documind.workspace;

import com.documind.common.BaseEntity;
import com.documind.storage.StorageProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "workspaces")
public class WorkspaceEntity extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 140)
    private String slug;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false, length = 20)
    private StorageProvider storageProvider = StorageProvider.LOCAL;

    @Column(name = "max_upload_size_mb", nullable = false)
    private int maxUploadSizeMb = 20;

    @Column(name = "notify_on_upload", nullable = false)
    private boolean notifyOnUpload = true;

    @Column(name = "notify_on_validation", nullable = false)
    private boolean notifyOnValidation = true;

    @Column(name = "created_by")
    private UUID createdBy;

    protected WorkspaceEntity() {
    }

    public WorkspaceEntity(String name, String slug, UUID ownerId, StorageProvider storageProvider, UUID createdBy) {
        this.name = name;
        this.slug = slug;
        this.ownerId = ownerId;
        this.storageProvider = storageProvider;
        this.createdBy = createdBy;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public StorageProvider getStorageProvider() {
        return storageProvider;
    }

    public int getMaxUploadSizeMb() {
        return maxUploadSizeMb;
    }

    public boolean isNotifyOnUpload() {
        return notifyOnUpload;
    }

    public boolean isNotifyOnValidation() {
        return notifyOnValidation;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public void setMaxUploadSizeMb(int maxUploadSizeMb) {
        this.maxUploadSizeMb = maxUploadSizeMb;
    }

    public void setNotifyOnUpload(boolean notifyOnUpload) {
        this.notifyOnUpload = notifyOnUpload;
    }

    public void setNotifyOnValidation(boolean notifyOnValidation) {
        this.notifyOnValidation = notifyOnValidation;
    }

    public void setStorageProvider(StorageProvider storageProvider) {
        this.storageProvider = storageProvider;
    }
}
