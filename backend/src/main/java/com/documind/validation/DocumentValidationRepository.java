package com.documind.validation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentValidationRepository extends JpaRepository<DocumentValidationEntity, UUID> {

    Page<DocumentValidationEntity> findAllByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);

    Page<DocumentValidationEntity> findAllByWorkspaceIdAndStatusOrderByCreatedAtDesc(UUID workspaceId,
            ValidationStatus status, Pageable pageable);

    Optional<DocumentValidationEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<DocumentValidationEntity> findAllByWorkspaceIdAndDocumentIdOrderByCreatedAtDesc(UUID workspaceId,
            UUID documentId);

    boolean existsByDocumentIdAndStatus(UUID documentId, ValidationStatus status);

    long countByWorkspaceIdAndStatus(UUID workspaceId, ValidationStatus status);

    @Query("select v.status, count(v) from DocumentValidationEntity v where v.workspaceId = :workspaceId group by v.status")
    List<Object[]> countByStatus(@Param("workspaceId") UUID workspaceId);
}
