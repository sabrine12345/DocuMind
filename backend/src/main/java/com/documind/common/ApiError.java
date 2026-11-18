package com.documind.common;

import java.time.Instant;
import java.util.Map;

/**
 * Single error payload shape for every API failure.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String requestId,
        Map<String, String> fieldErrors
) {
}
