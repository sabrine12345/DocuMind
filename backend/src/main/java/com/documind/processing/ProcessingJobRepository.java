package com.documind.processing;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJobEntity, UUID> {

    Optional<ProcessingJobEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Page<ProcessingJobEntity> findAllByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);

    Page<ProcessingJobEntity> findAllByWorkspaceIdAndStatusOrderByCreatedAtDesc(UUID workspaceId,
            ProcessingJobStatus status, Pageable pageable);

    List<ProcessingJobEntity> findAllByDocumentIdOrderByCreatedAtDesc(UUID documentId);

    List<ProcessingJobEntity> findTop20ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(ProcessingJobStatus status, Instant before);

    long countByDocumentId(UUID documentId);

    long countByWorkspaceIdAndStatus(UUID workspaceId, ProcessingJobStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ProcessingJobEntity j where j.id = :id")
    Optional<ProcessingJobEntity> findForUpdate(@Param("id") UUID id);
}
