package com.documind.dashboard;

import com.documind.audit.AuditLogResponse;
import com.documind.document.DocumentCategory;
import com.documind.document.DocumentStatus;
import com.documind.document.DocumentValidationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Real workspace statistics computed from the database. Nothing is mocked.
 */
public record DashboardStatsResponse(
        long totalDocuments,
        long completedDocuments,
        long processingDocuments,
        long failedDocuments,
        long storageBytes,
        long templateCount,
        long memberCount,
        long pendingValidations,
        long approvedDocuments,
        long rejectedDocuments,
        List<CategoryStat> categoryBreakdown,
        List<DailyUploadStat> uploadsLast7Days,
        List<RecentDocument> recentDocuments,
        List<AuditLogResponse> recentActivity
) {

    public record CategoryStat(DocumentCategory category, long count) {
    }

    public record DailyUploadStat(LocalDate date, long uploaded) {
    }

    public record RecentDocument(
            UUID id,
            String fileName,
            DocumentCategory category,
            DocumentStatus status,
            DocumentValidationStatus validationStatus,
            long sizeBytes,
            Instant uploadedAt
    ) {
    }
}
