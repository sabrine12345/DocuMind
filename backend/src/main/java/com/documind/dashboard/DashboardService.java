package com.documind.dashboard;

import com.documind.audit.AuditLogResponse;
import com.documind.audit.AuditService;
import com.documind.dashboard.DashboardStatsResponse.CategoryStat;
import com.documind.dashboard.DashboardStatsResponse.DailyUploadStat;
import com.documind.dashboard.DashboardStatsResponse.RecentDocument;
import com.documind.document.DocumentCategory;
import com.documind.document.DocumentEntity;
import com.documind.document.DocumentRepository;
import com.documind.document.DocumentStatus;
import com.documind.document.DocumentValidationStatus;
import com.documind.template.DocumentTemplateRepository;
import com.documind.workspace.WorkspaceMemberRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final int TREND_DAYS = 7;
    private static final int RECENT_LIMIT = 5;

    private final DocumentRepository documents;
    private final DocumentTemplateRepository templates;
    private final WorkspaceMemberRepository members;
    private final AuditService audit;

    public DashboardService(DocumentRepository documents, DocumentTemplateRepository templates,
            WorkspaceMemberRepository members, AuditService audit) {
        this.documents = documents;
        this.templates = templates;
        this.members = members;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public DashboardStatsResponse stats(UUID workspaceId) {
        long total = documents.countByWorkspaceIdAndDeletedAtIsNull(workspaceId);
        long completed = documents.countByWorkspaceIdAndStatusAndDeletedAtIsNull(workspaceId, DocumentStatus.COMPLETED);
        long queued = documents.countByWorkspaceIdAndStatusAndDeletedAtIsNull(workspaceId, DocumentStatus.QUEUED);
        long processing = documents.countByWorkspaceIdAndStatusAndDeletedAtIsNull(workspaceId, DocumentStatus.PROCESSING);
        long failed = documents.countByWorkspaceIdAndStatusAndDeletedAtIsNull(workspaceId, DocumentStatus.FAILED);
        long storageBytes = documents.sumSizeBytes(workspaceId);
        long templateCount = templates.countByWorkspaceId(workspaceId);
        long memberCount = members.countByWorkspaceId(workspaceId);
        long pending = documents.countByWorkspaceIdAndValidationStatusAndDeletedAtIsNull(workspaceId,
                DocumentValidationStatus.PENDING);
        long approved = documents.countByWorkspaceIdAndValidationStatusAndDeletedAtIsNull(workspaceId,
                DocumentValidationStatus.APPROVED);
        long rejected = documents.countByWorkspaceIdAndValidationStatusAndDeletedAtIsNull(workspaceId,
                DocumentValidationStatus.REJECTED);
        return new DashboardStatsResponse(
                total,
                completed,
                queued + processing,
                failed,
                storageBytes,
                templateCount,
                memberCount,
                pending,
                approved,
                rejected,
                categoryBreakdown(workspaceId),
                uploadsTrend(workspaceId),
                recentDocuments(workspaceId),
                audit.recent(workspaceId)
        );
    }

    private List<CategoryStat> categoryBreakdown(UUID workspaceId) {
        Map<DocumentCategory, Long> counts = new EnumMap<>(DocumentCategory.class);
        for (Object[] row : documents.countByCategory(workspaceId)) {
            counts.put((DocumentCategory) row[0], ((Number) row[1]).longValue());
        }
        List<CategoryStat> stats = new ArrayList<>();
        for (DocumentCategory category : DocumentCategory.values()) {
            stats.add(new CategoryStat(category, counts.getOrDefault(category, 0L)));
        }
        stats.sort((left, right) -> Long.compare(right.count(), left.count()));
        return stats;
    }

    private List<DailyUploadStat> uploadsTrend(UUID workspaceId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate firstDay = today.minusDays(TREND_DAYS - 1L);
        Instant since = firstDay.atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<LocalDate, Long> perDay = new HashMap<>();
        for (Object[] row : documents.findCreationStatsSince(workspaceId, since)) {
            LocalDate day = LocalDate.ofInstant((Instant) row[0], ZoneOffset.UTC);
            perDay.merge(day, 1L, Long::sum);
        }
        List<DailyUploadStat> trend = new ArrayList<>(TREND_DAYS);
        for (int index = 0; index < TREND_DAYS; index++) {
            LocalDate day = firstDay.plusDays(index);
            trend.add(new DailyUploadStat(day, perDay.getOrDefault(day, 0L)));
        }
        return trend;
    }

    private List<RecentDocument> recentDocuments(UUID workspaceId) {
        List<DocumentEntity> latest = documents
                .findTop5ByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(workspaceId);
        return latest.stream()
                .limit(RECENT_LIMIT)
                .map(document -> new RecentDocument(document.getId(), document.getFileName(),
                        document.getCategory(), document.getStatus(), document.getValidationStatus(),
                        document.getSizeBytes(), document.getCreatedAt()))
                .toList();
    }
}
