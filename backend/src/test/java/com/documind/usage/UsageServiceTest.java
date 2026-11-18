package com.documind.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UsageServiceTest {

    @Mock
    private AiUsageEventRepository repository;

    @InjectMocks
    private UsageService service;

    private final UUID workspaceId = UUID.randomUUID();

    @Test
    void returnsZeroesAndAFullEmptySeriesWhenNoUsageWasRecorded() {
        when(repository.findAllByWorkspaceIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(eq(workspaceId), any()))
                .thenReturn(List.of());

        UsageSummaryResponse summary = service.summary(workspaceId, 7);

        assertThat(summary.totalTokens()).isZero();
        assertThat(summary.totalCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.eventCount()).isZero();
        assertThat(summary.currency()).isEqualTo("USD");
        assertThat(summary.models()).isEmpty();
        assertThat(summary.recent()).isEmpty();
        assertThat(summary.daily()).hasSize(7);
        assertThat(summary.daily().get(6).date()).isEqualTo(LocalDate.now(ZoneOffset.UTC));
        assertThat(summary.daily()).allSatisfy(day -> {
            assertThat(day.tokens()).isZero();
            assertThat(day.cost()).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }

    @Test
    void aggregatesTokensCostsPerDayAndPerModel() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        AiUsageEventEntity first = new AiUsageEventEntity(workspaceId, UUID.randomUUID(), "DEEPSEEK",
                "deepseek-chat", "CHAT", 100, 50, new BigDecimal("0.000420"), "USD",
                today.atStartOfDay(ZoneOffset.UTC).plusHours(10).toInstant(), null);
        AiUsageEventEntity second = new AiUsageEventEntity(workspaceId, null, "DEEPSEEK",
                "deepseek-chat", "EXTRACTION", 200, 100, new BigDecimal("0.000840"), "USD",
                today.atStartOfDay(ZoneOffset.UTC).plusHours(12).toInstant(), null);
        AiUsageEventEntity third = new AiUsageEventEntity(workspaceId, null, "DEEPSEEK",
                "deepseek-reasoner", "SUMMARY", 50, 25, new BigDecimal("0.000200"), "USD",
                today.minusDays(1).atStartOfDay(ZoneOffset.UTC).plusHours(9).toInstant(), null);
        when(repository.findAllByWorkspaceIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(eq(workspaceId), any()))
                .thenReturn(List.of(second, first, third));

        UsageSummaryResponse summary = service.summary(workspaceId, 7);

        assertThat(summary.totalTokens()).isEqualTo(525);
        assertThat(summary.promptTokens()).isEqualTo(350);
        assertThat(summary.completionTokens()).isEqualTo(175);
        assertThat(summary.totalCost()).isEqualByComparingTo(new BigDecimal("0.001460"));
        assertThat(summary.eventCount()).isEqualTo(3);
        assertThat(summary.daily()).hasSize(7);
        assertThat(summary.daily().get(6).tokens()).isEqualTo(450);
        assertThat(summary.daily().get(5).tokens()).isEqualTo(75);
        assertThat(summary.models()).hasSize(2);
        assertThat(summary.models().get(0).model()).isEqualTo("deepseek-chat");
        assertThat(summary.models().get(0).events()).isEqualTo(2);
        assertThat(summary.models().get(0).tokens()).isEqualTo(450);
        assertThat(summary.models().get(1).model()).isEqualTo("deepseek-reasoner");
        assertThat(summary.recent()).hasSize(3);
        assertThat(summary.recent().get(0).occurredAt()).isAfter(Instant.EPOCH);
    }

    @Test
    void clampsTheRequestedWindowBetweenOneAndNinetyDays() {
        when(repository.findAllByWorkspaceIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(eq(workspaceId), any()))
                .thenReturn(List.of());

        assertThat(service.summary(workspaceId, 0).daily()).hasSize(1);
        assertThat(service.summary(workspaceId, 500).daily()).hasSize(90);
    }
}
