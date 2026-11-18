package com.documind.validation;

import com.documind.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Discussion thread attached to a validation request.
 */
@Entity
@Table(name = "validation_comments")
public class ValidationCommentEntity extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "validation_id", nullable = false)
    private UUID validationId;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "author_name", nullable = false, length = 120)
    private String authorName;

    @Column(nullable = false, length = 2000)
    private String message;

    protected ValidationCommentEntity() {
    }

    public ValidationCommentEntity(UUID workspaceId, UUID validationId, UUID authorId,
            String authorName, String message) {
        this.workspaceId = workspaceId;
        this.validationId = validationId;
        this.authorId = authorId;
        this.authorName = authorName;
        this.message = message;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getValidationId() {
        return validationId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getMessage() {
        return message;
    }
}
