package com.documind.audit;

import com.documind.common.PageResponse;
import com.documind.security.AuthenticatedUser;
import com.documind.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final CurrentUserProvider currentUser;

    public AuditService(AuditLogRepository repository, CurrentUserProvider currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    /**
     * Records a business event. Never fails the calling transaction because a
     * missing audit row must not break a legitimate operation.
     */
    @Transactional
    public void record(UUID workspaceId, String action, String entityType, UUID entityId, String details) {
        AuthenticatedUser actor = currentUser.find().orElse(null);
        repository.save(new AuditLogEntity(
                workspaceId,
                actor == null ? null : actor.id(),
                actor == null ? "system" : actor.email(),
                action,
                entityType,
                entityId,
                truncate(details, 2000),
                clientIp()
        ));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(UUID workspaceId, int page, int size) {
        return PageResponse.of(
                repository.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId,
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))),
                AuditLogResponse::from
        );
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> recent(UUID workspaceId) {
        return repository.findTop8ByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
                .map(AuditLogResponse::from)
                .toList();
    }

    private String clientIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return truncate(forwarded.split(",")[0].trim(), 64);
            }
            return truncate(request.getRemoteAddr(), 64);
        }
        return null;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
