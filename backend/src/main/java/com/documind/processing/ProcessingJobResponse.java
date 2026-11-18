package com.documind.processing;

import com.documind.document.DocumentCategory;
import java.time.Instant;
import java.util.UUID;

public record ProcessingJobResponse(
        UUID id,
        UUID documentId,
        String fileName,
        DocumentCategory category,
        ProcessingJobStatus status,
        int progress,
        int attempt,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage,
        String worker,
        Instant createdAt
) {
    public static ProcessingJobResponse from(ProcessingJobEntity job, String fileName, DocumentCategory category) {
        return new ProcessingJobResponse(
                job.getId(),
                job.getDocumentId(),
                fileName,
                category,
                job.getStatus(),
                job.getProgress(),
                job.getAttempt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getErrorMessage(),
                job.getWorker(),
                job.getCreatedAt()
        );
    }
}
