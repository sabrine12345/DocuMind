package com.documind.processing;

import com.documind.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processing_jobs")
public class ProcessingJobEntity extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProcessingJobStatus status = ProcessingJobStatus.QUEUED;

    @Column(nullable = false)
    private int progress;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(length = 80)
    private String worker;

    @Column(name = "created_by")
    private UUID createdBy;

    protected ProcessingJobEntity() {
    }

    public ProcessingJobEntity(UUID workspaceId, UUID documentId, int attempt, UUID createdBy) {
        this.workspaceId = workspaceId;
        this.documentId = documentId;
        this.attempt = attempt;
        this.createdBy = createdBy;
        this.status = ProcessingJobStatus.QUEUED;
        this.progress = 0;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public ProcessingJobStatus getStatus() {
        return status;
    }

    public int getProgress() {
        return progress;
    }

    public int getAttempt() {
        return attempt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getWorker() {
        return worker;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void start(String workerName) {
        this.status = ProcessingJobStatus.PROCESSING;
        this.startedAt = Instant.now();
        this.worker = workerName;
        this.progress = 5;
        this.errorMessage = null;
    }

    public void updateProgress(int value) {
        this.progress = Math.max(this.progress, Math.min(value, 99));
    }

    public void complete(String workerName) {
        this.status = ProcessingJobStatus.COMPLETED;
        this.progress = 100;
        this.finishedAt = Instant.now();
        this.worker = workerName;
        this.errorMessage = null;
    }

    public void fail(String reason) {
        this.status = ProcessingJobStatus.FAILED;
        this.finishedAt = Instant.now();
        this.errorMessage = truncate(reason, 2000);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "Unknown processing error.";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
