package com.documind.template;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplateEntity, UUID> {

    List<DocumentTemplateEntity> findAllByWorkspaceIdOrderByNameAsc(UUID workspaceId);

    Optional<DocumentTemplateEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByWorkspaceIdAndNameIgnoreCase(UUID workspaceId, String name);

    boolean existsByWorkspaceIdAndNameIgnoreCaseAndIdNot(UUID workspaceId, String name, UUID id);

    long countByWorkspaceId(UUID workspaceId);

    List<DocumentTemplateEntity> findAllByWorkspaceIdAndIdIn(UUID workspaceId, Collection<UUID> ids);
}
