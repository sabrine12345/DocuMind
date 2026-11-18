package com.documind.usage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiUsageEventRepository extends JpaRepository<AiUsageEventEntity, UUID> {

    List<AiUsageEventEntity> findAllByWorkspaceIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
            UUID workspaceId, Instant since);
}
