package com.documind.extraction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractedFieldRepository extends JpaRepository<ExtractedFieldEntity, UUID> {

    Optional<ExtractedFieldEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<ExtractedFieldEntity> findAllByDocumentIdOrderByFieldLabelAsc(UUID documentId);

    boolean existsByDocumentId(UUID documentId);

    long countByDocumentId(UUID documentId);

    long countByDocumentIdAndStatus(UUID documentId, ExtractionFieldStatus status);

    void deleteAllByDocumentId(UUID documentId);
}
