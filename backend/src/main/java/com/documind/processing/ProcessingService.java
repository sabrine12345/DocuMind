package com.documind.processing;

import com.documind.audit.AuditAction;
import com.documind.audit.AuditService;
import com.documind.common.ConflictException;
import com.documind.common.NotFoundException;
import com.documind.common.PageResponse;
import com.documind.document.DocumentEntity;
import com.documind.document.DocumentRepository;
import com.documind.security.CurrentUserProvider;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessingService {

    private final ProcessingJobRepository jobs;
    private final DocumentRepository documents;
    private final ProcessingPublisher publisher;
    private final ProcessingProperties properties;
    private final AuditService audit;
    private final CurrentUserProvider currentUser;

    public ProcessingService(ProcessingJobRepository jobs, DocumentRepository documents,
            ProcessingPublisher publisher, ProcessingProperties properties, AuditService audit,
            CurrentUserProvider currentUser) {
        this.jobs = jobs;
        this.documents = documents;
        this.publisher = publisher;
        this.properties = properties;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    /**
     * Creates a processing job and hands it over to the worker pipeline. The
     * caller is responsible for the document being already persisted.
     */
    @Transactional
    public ProcessingJobEntity enqueue(UUID workspaceId, UUID documentId) {
        int attempt = (int) jobs.countByDocumentId(documentId) + 1;
        UUID actorId = currentUser.find().map(user -> user.id()).orElse(null);
        ProcessingJobEntity job = jobs.save(new ProcessingJobEntity(workspaceId, documentId, attempt, actorId));
        publisher.publish(new ProcessingMessage(job.getId(), workspaceId, documentId, attempt));
        return job;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProcessingJobResponse> list(UUID workspaceId, ProcessingJobStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<ProcessingJobEntity> result = status == null
                ? jobs.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageable)
                : jobs.findAllByWorkspaceIdAndStatusOrderByCreatedAtDesc(workspaceId, status, pageable);
        Map<UUID, DocumentEntity> documentsById = loadDocuments(result.getContent());
        return PageResponse.of(result, job -> toResponse(job, documentsById.get(job.getDocumentId())));
    }

    @Transactional(readOnly = true)
    public ProcessingJobResponse get(UUID workspaceId, UUID jobId) {
        ProcessingJobEntity job = jobs.findByIdAndWorkspaceId(jobId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Processing job not found."));
        DocumentEntity document = documents.findById(job.getDocumentId()).orElse(null);
        return toResponse(job, document);
    }

    @Transactional(readOnly = true)
    public List<ProcessingJobResponse> history(UUID workspaceId, UUID documentId) {
        return jobs.findAllByDocumentIdOrderByCreatedAtDesc(documentId).stream()
                .filter(job -> job.getWorkspaceId().equals(workspaceId))
                .map(job -> toResponse(job, documents.findById(job.getDocumentId()).orElse(null)))
                .toList();
    }

    @Transactional
    public ProcessingJobResponse retry(UUID workspaceId, UUID jobId) {
        ProcessingJobEntity job = jobs.findByIdAndWorkspaceId(jobId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Processing job not found."));
        if (job.getStatus() != ProcessingJobStatus.FAILED) {
            throw new ConflictException("Only failed processing jobs can be retried.");
        }
        DocumentEntity document = documents.findByIdAndWorkspaceId(job.getDocumentId(), workspaceId)
                .orElseThrow(() -> new NotFoundException("Document not found."));
        long attempts = jobs.countByDocumentId(document.getId());
        if (attempts >= properties.getMaxAttempts()) {
            throw new ConflictException("The maximum number of processing attempts ("
                    + properties.getMaxAttempts() + ") has been reached.");
        }
        document.markQueued(document.getCurrentVersion());
        UUID actorId = currentUser.find().map(user -> user.id()).orElse(null);
        ProcessingJobEntity next = jobs.save(new ProcessingJobEntity(workspaceId, document.getId(),
                (int) attempts + 1, actorId));
        publisher.publish(new ProcessingMessage(next.getId(), workspaceId, document.getId(), next.getAttempt()));
        audit.record(workspaceId, AuditAction.PROCESSING_RETRIED, "DOCUMENT", document.getId(),
                "Retry attempt " + next.getAttempt() + " for " + document.getFileName());
        return toResponse(next, document);
    }

    private Map<UUID, DocumentEntity> loadDocuments(List<ProcessingJobEntity> jobList) {
        List<UUID> ids = jobList.stream().map(ProcessingJobEntity::getDocumentId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return documents.findAllById(ids).stream()
                .collect(Collectors.toMap(DocumentEntity::getId, Function.identity()));
    }

    private ProcessingJobResponse toResponse(ProcessingJobEntity job, DocumentEntity document) {
        return ProcessingJobResponse.from(job,
                document == null ? null : document.getFileName(),
                document == null ? null : document.getCategory());
    }
}
