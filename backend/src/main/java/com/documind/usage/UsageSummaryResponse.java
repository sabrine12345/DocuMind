package com.documind.usage;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Real AI consumption aggregated from ai_usage_events. When the AI layer is
 * not enabled yet every value is zero and the lists are empty.
 */
public record UsageSummaryResponse(
        long totalTokens,
        long promptTokens,
        long completionTokens,
        BigDecimal totalCost,
        String currency,
        long eventCount,
        List<DailyUsage> daily,
        List<ModelUsage> models,
        List<RecentUsage> recent
) {

    public record DailyUsage(LocalDate date, long tokens, BigDecimal cost) {
    }

    public record ModelUsage(String provider, String model, long events, long tokens, BigDecimal cost) {
    }

    public record RecentUsage(
            UUID id,
            UUID documentId,
            String provider,
            String model,
            String operation,
            int totalTokens,
            BigDecimal cost,
            String currency,
            Instant occurredAt
    ) {
    }
}
