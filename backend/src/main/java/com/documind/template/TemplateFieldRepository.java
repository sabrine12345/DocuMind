package com.documind.template;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TemplateFieldRepository extends JpaRepository<TemplateFieldEntity, UUID> {

    @Query("select f from TemplateFieldEntity f where f.templateId = :templateId order by f.displayOrder asc, f.label asc")
    List<TemplateFieldEntity> findAllForTemplate(@Param("templateId") UUID templateId);

    @Query("select f from TemplateFieldEntity f where f.templateId in :templateIds order by f.displayOrder asc, f.label asc")
    List<TemplateFieldEntity> findAllForTemplates(@Param("templateIds") Collection<UUID> templateIds);

    @Modifying
    @Query("delete from TemplateFieldEntity f where f.templateId = :templateId")
    void deleteAllForTemplate(@Param("templateId") UUID templateId);
}
