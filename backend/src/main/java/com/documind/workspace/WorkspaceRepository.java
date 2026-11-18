package com.documind.workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, UUID> {
    Optional<WorkspaceEntity> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<WorkspaceEntity> findAllByOrderByCreatedAtAsc();
}
