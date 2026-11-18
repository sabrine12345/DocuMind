package com.documind.processing;

import com.documind.audit.AuditAction;
import com.documind.audit.AuditService;
import com.documind.document.DocumentEntity;
import com.documind.document.DocumentRepository;
import com.documind.document.DocumentVersionEntity;
import com.documind.document.DocumentVersionRepository;
import com.documind.extraction.FieldPreparationService;
import com.documind.storage.StorageService;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real document preparation worker. It validates that the stored file is
 * readable, computes a SHA-256 checksum and resolves the page count of PDFs.
 * No OCR or AI is involved: the goal is to feed reliable document metadata
 * into the platform before intelligence services are added.
 */
@Component
public class ProcessingWorker {

    private static final Logger log = LoggerFactory.getLogger(ProcessingWorker.class);
    public static final String WORKER_NAME = "document-preparation-worker";

    private final ProcessingJobRepository jobs;
    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final StorageService storage;
    private final FieldPreparationService fieldPreparation;
    private final AuditService audit;
    private final TransactionTemplate transaction;

    public ProcessingWorker(ProcessingJobRepository jobs, DocumentRepository documents,
            DocumentVersionRepository versions, StorageService storage,
            FieldPreparationService fieldPreparation, AuditService audit,
            TransactionTemplate transaction) {
        this.jobs = jobs;
        this.documents = documents;
        this.versions = versions;
        this.storage = storage;
        this.fieldPreparation = fieldPreparation;
        this.audit = audit;
        this.transaction = transaction;
    }

    public void process(UUID jobId) {
        JobContext context = transaction.execute(status -> begin(jobId));
        if (context == null) {
            return;
        }
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile("documind-", ".source");
            StorageService.StoredObject object = storage.get(context.storageKey());
            try (InputStream stream = object.stream()) {
                Files.copy(stream, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            }
            advance(jobId, 30);

            String checksum = sha256(temporaryFile);
            advance(jobId, 55);

            int pages = pageCount(temporaryFile, context.contentType());
            advance(jobId, 80);

            transaction.executeWithoutResult(status -> complete(jobId, checksum, pages));
            log.info("Processing job {} completed (document={}, pages={})", jobId, context.documentId(), pages);
        } catch (Exception ex) {
            String message = rootMessage(ex);
            log.warn("Processing job {} failed: {}", jobId, message);
            transaction.executeWithoutResult(status -> fail(jobId, message));
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (Exception ignored) {
                    // Temporary file cleanup is best effort.
                }
            }
        }
    }

    private JobContext begin(UUID jobId) {
        ProcessingJobEntity job = jobs.findForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus() != ProcessingJobStatus.QUEUED) {
            // Already processed or claimed by another worker: idempotent no-op.
            return null;
        }
        job.start(WORKER_NAME);
        DocumentEntity document = documents.findById(job.getDocumentId()).orElse(null);
        if (document == null) {
            job.fail("The document no longer exists.");
            return null;
        }
        document.markProcessing(10);
        DocumentVersionEntity version = versions
                .findByDocumentIdAndVersionNumber(document.getId(), document.getCurrentVersion())
                .orElse(null);
        if (version == null) {
            job.fail("The document file version is missing.");
            document.markFailed("The document file version is missing.");
            return null;
        }
        return new JobContext(document.getId(), version.getStorageKey(), version.getContentType());
    }

    private void advance(UUID jobId, int progress) {
        transaction.executeWithoutResult(status -> {
            ProcessingJobEntity job = jobs.findById(jobId).orElse(null);
            if (job == null) {
                return;
            }
            job.updateProgress(progress);
            documents.findById(job.getDocumentId()).ifPresent(document -> document.updateProgress(progress));
        });
    }

    private void complete(UUID jobId, String checksum, int pages) {
        ProcessingJobEntity job = jobs.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }
        DocumentEntity document = documents.findById(job.getDocumentId()).orElse(null);
        if (document == null) {
            job.fail("The document no longer exists.");
            return;
        }
        document.markCompleted(pages, checksum);
        versions.findByDocumentIdAndVersionNumber(document.getId(), document.getCurrentVersion())
                .ifPresent(version -> version.setChecksum(checksum));
        job.complete(WORKER_NAME);
        try {
            // Extraction fields are prepared from the template once the document is ready.
            fieldPreparation.prepare(job.getWorkspaceId(), document.getId());
        } catch (RuntimeException ex) {
            log.warn("Field preparation failed for document {}: {}", document.getId(), ex.getMessage());
        }
        audit.record(job.getWorkspaceId(), AuditAction.PROCESSING_COMPLETED, "DOCUMENT", document.getId(),
                "Prepared " + document.getFileName() + " (" + pages + " page(s))");
    }

    private void fail(UUID jobId, String reason) {
        ProcessingJobEntity job = jobs.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }
        job.fail(reason);
        documents.findById(job.getDocumentId()).ifPresent(document -> document.markFailed(reason));
        audit.record(job.getWorkspaceId(), AuditAction.PROCESSING_FAILED, "DOCUMENT", job.getDocumentId(), reason);
    }

    private String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream stream = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private int pageCount(Path file, String contentType) throws Exception {
        if (contentType != null && contentType.equalsIgnoreCase("application/pdf")) {
            try (PDDocument document = Loader.loadPDF(file.toFile())) {
                return Math.max(document.getNumberOfPages(), 1);
            }
        }
        return 1;
    }

    private String rootMessage(Exception ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        if (current instanceof org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            return "The PDF is password protected and cannot be prepared.";
        }
        return message;
    }

    private record JobContext(UUID documentId, String storageKey, String contentType) {
    }
}
