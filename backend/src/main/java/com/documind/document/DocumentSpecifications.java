package com.documind.document;

import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Tenant scoped query fragments for the document listing endpoint.
 */
public final class DocumentSpecifications {

    private DocumentSpecifications() {
    }

    public static Specification<DocumentEntity> inWorkspace(UUID workspaceId) {
        return (root, query, cb) -> cb.equal(root.get("workspaceId"), workspaceId);
    }

    public static Specification<DocumentEntity> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<DocumentEntity> fileNameContains(String term) {
        String pattern = "%" + term.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("fileName")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("title"), "")), pattern)
        );
    }

    public static Specification<DocumentEntity> hasStatus(DocumentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<DocumentEntity> hasCategory(DocumentCategory category) {
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    public static Specification<DocumentEntity> hasTemplate(UUID templateId) {
        return (root, query, cb) -> cb.equal(root.get("templateId"), templateId);
    }

    public static Specification<DocumentEntity> hasValidationStatus(DocumentValidationStatus status) {
        return (root, query, cb) -> cb.equal(root.get("validationStatus"), status);
    }
}
