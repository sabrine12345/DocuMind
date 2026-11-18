package com.documind.validation.dto;

import com.documind.validation.ValidationCommentEntity;
import java.time.Instant;
import java.util.UUID;

public record ValidationCommentResponse(
        UUID id,
        UUID authorId,
        String authorName,
        String message,
        Instant createdAt
) {
    public static ValidationCommentResponse from(ValidationCommentEntity comment) {
        return new ValidationCommentResponse(comment.getId(), comment.getAuthorId(), comment.getAuthorName(),
                comment.getMessage(), comment.getCreatedAt());
    }
}
