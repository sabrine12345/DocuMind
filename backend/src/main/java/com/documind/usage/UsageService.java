package com.documind.usage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates AI usage events for the authenticated workspace. Nothing is
 * synthesized: an empty workspace yields zeros and empty series.
 */
@Service
public class UsageService {

    private static final int MAX_RECENT_EVENTS = 20;

    private final AiUsageEventRepository repository;

    public UsageService(AiUsageEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public UsageSummaryResponse summary(UUID workspaceId, int days) {
        int window = Math.min(Math.max(days, 1), 90);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate firstDay = today.minusDays(window - 1L);
        List<AiUsageEventEntity> events = repository
                .findAllByWorkspaceIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
                        workspaceId, firstDay.atStartOfDay(ZoneOffset.UTC).toInstant());

        long promptTokens = 0;
        long completionTokens = 0;
        long totalTokens = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        Map<LocalDate, long[]> tokensByDay = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> costByDay = new LinkedHashMap<>();
        Map<String, ModelAccumulator> byModel = new LinkedHashMap<>();

        for (AiUsageEventEntity event : events) {
            promptTokens += event.getPromptTokens();
            completionTokens += event.getCompletionTokens();
            totalTokens += event.getTotalTokens();
            totalCost = totalCost.add(event.getCostAmount());

            LocalDate day = event.getOccurredAt().atZone(ZoneOffset.UTC).toLocalDate();
            tokensByDay.computeIfAbsent(day, key -> new long[1])[0] += event.getTotalTokens();
            costByDay.merge(day, event.getCostAmount(), BigDecimal::add);

            String key = event.getProvider() + "|" + event.getModel();
            byModel.computeIfAbsent(key, k -> new ModelAccumulator(event.getProvider(), event.getModel()))
                    .add(event.getTotalTokens(), event.getCostAmount());
        }

        List<UsageSummaryResponse.DailyUsage> daily = new ArrayList<>(window);
        for (int i = 0; i < window; i++) {
            LocalDate day = firstDay.plusDays(i);
            long[] tokens = tokensByDay.getOrDefault(day, new long[1]);
            daily.add(new UsageSummaryResponse.DailyUsage(day, tokens[0],
                    costByDay.getOrDefault(day, BigDecimal.ZERO)));
        }

        List<UsageSummaryResponse.ModelUsage> models = byModel.values().stream()
                .map(ModelAccumulator::toResponse)
                .sorted(Comparator.comparing(UsageSummaryResponse.ModelUsage::cost).reversed()
                        .thenComparing(Comparator.comparingLong(UsageSummaryResponse.ModelUsage::tokens).reversed()))
                .toList();

        List<UsageSummaryResponse.RecentUsage> recent = events.stream()
                .limit(MAX_RECENT_EVENTS)
                .map(event -> new UsageSummaryResponse.RecentUsage(
                        event.getId(),
                        event.getDocumentId(),
                        event.getProvider(),
                        event.getModel(),
                        event.getOperation(),
                        event.getTotalTokens(),
                        event.getCostAmount(),
                        event.getCurrency(),
                        event.getOccurredAt()))
                .toList();

        String currency = events.isEmpty() ? "USD" : events.get(0).getCurrency();
        return new UsageSummaryResponse(totalTokens, promptTokens, completionTokens, totalCost, currency,
                events.size(), daily, models, recent);
    }

    private static final class ModelAccumulator {
        private final String provider;
        private final String model;
        private long events;
        private long tokens;
        private BigDecimal cost = BigDecimal.ZERO;

        private ModelAccumulator(String provider, String model) {
            this.provider = provider;
            this.model = model;
        }

        private void add(long tokens, BigDecimal cost) {
            this.events++;
            this.tokens += tokens;
            this.cost = this.cost.add(cost);
        }

        private UsageSummaryResponse.ModelUsage toResponse() {
            return new UsageSummaryResponse.ModelUsage(provider, model, events, tokens, cost);
        }
    }
}
