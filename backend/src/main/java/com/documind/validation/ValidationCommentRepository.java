package com.documind.validation;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ValidationCommentRepository extends JpaRepository<ValidationCommentEntity, UUID> {

    List<ValidationCommentEntity> findAllByValidationIdOrderByCreatedAtAsc(UUID validationId);

    long countByValidationId(UUID validationId);

    @Query("select c.validationId, count(c) from ValidationCommentEntity c where c.validationId in :validationIds group by c.validationId")
    List<Object[]> countByValidationIds(@Param("validationIds") Collection<UUID> validationIds);
}
