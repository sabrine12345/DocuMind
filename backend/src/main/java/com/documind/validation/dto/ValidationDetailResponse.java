package com.documind.validation.dto;

import java.util.List;

public record ValidationDetailResponse(ValidationResponse validation, List<ValidationCommentResponse> comments) {
}
