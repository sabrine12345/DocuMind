package com.documind.usage;

import com.documind.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One billable AI operation (chat answer, extraction, embedding...).
 * Rows are produced by the AI integration layer only; the platform never
 * fabricates usage data.
 */
@Entity
@Table(name = "ai_usage_events")
public class AiUsageEventEntity extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(nullable = false, length = 60)
    private String provider;

    @Column(nullable = false, length = 80)
    private String model;

    @Column(nullable = false, length = 60)
    private String operation;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    @Column(name = "total_tokens", nullable = false)
    private int totalTokens;

    @Column(name = "cost_amount", nullable = false, precision = 12, scale = 6)
    private BigDecimal costAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 8)
    private String currency = "USD";

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "created_by")
    private UUID createdBy;

    protected AiUsageEventEntity() {
        // JPA
    }

    /**
     * Creation entry point for the future AI integration service and tests.
     */
    public AiUsageEventEntity(UUID workspaceId, UUID documentId, String provider, String model, String operation,
            int promptTokens, int completionTokens, BigDecimal costAmount, String currency,
            Instant occurredAt, UUID createdBy) {
        this.workspaceId = workspaceId;
        this.documentId = documentId;
        this.provider = provider;
        this.model = model;
        this.operation = operation;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = promptTokens + completionTokens;
        this.costAmount = costAmount;
        this.currency = currency;
        this.occurredAt = occurredAt;
        this.createdBy = createdBy;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getOperation() {
        return operation;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public BigDecimal getCostAmount() {
        return costAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
