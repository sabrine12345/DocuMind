package com.documind.document;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID>, JpaSpecificationExecutor<DocumentEntity> {

    Optional<DocumentEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    long countByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId);

    long countByWorkspaceIdAndStatusAndDeletedAtIsNull(UUID workspaceId, DocumentStatus status);

    long countByWorkspaceIdAndValidationStatusAndDeletedAtIsNull(UUID workspaceId, DocumentValidationStatus status);

    long countByWorkspaceIdAndTemplateIdAndDeletedAtIsNull(UUID workspaceId, UUID templateId);

    long countByWorkspaceIdAndCategoryAndDeletedAtIsNull(UUID workspaceId, DocumentCategory category);

    @Query("select coalesce(sum(d.sizeBytes), 0) from DocumentEntity d where d.workspaceId = :workspaceId and d.deletedAt is null")
    long sumSizeBytes(@Param("workspaceId") UUID workspaceId);

    List<DocumentEntity> findTop5ByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID workspaceId);

    @Query("select d.category, count(d) from DocumentEntity d where d.workspaceId = :workspaceId and d.deletedAt is null group by d.category")
    List<Object[]> countByCategory(@Param("workspaceId") UUID workspaceId);

    @Query("select d.validationStatus, count(d) from DocumentEntity d where d.workspaceId = :workspaceId and d.deletedAt is null group by d.validationStatus")
    List<Object[]> countByValidationStatus(@Param("workspaceId") UUID workspaceId);

    @Query("select d.createdAt, d.status from DocumentEntity d where d.workspaceId = :workspaceId and d.deletedAt is null and d.createdAt >= :since")
    List<Object[]> findCreationStatsSince(@Param("workspaceId") UUID workspaceId, @Param("since") Instant since);

    List<DocumentEntity> findAllByWorkspaceIdAndIdInAndDeletedAtIsNull(UUID workspaceId, Collection<UUID> ids);
}
