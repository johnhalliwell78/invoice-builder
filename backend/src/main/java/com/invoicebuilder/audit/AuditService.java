package com.invoicebuilder.audit;

import com.invoicebuilder.auth.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

/**
 * Records audit-trail entries. The tenant is passed explicitly (so scheduled
 * jobs without a request/tenant context can still audit); the acting user, IP,
 * and user-agent are captured from the security and request contexts when
 * present. Best-effort — a failure to audit never breaks the business action.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(UUID tenantId, String entityType, UUID entityId,
                       AuditAction action, Map<String, Object> changes) {
        try {
            AuditLog entry = new AuditLog();
            entry.setTenantId(tenantId);
            entry.setUserId(currentUserId());
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setAction(action);
            entry.setChanges(changes);
            captureRequestMeta(entry);
            repository.save(entry);
        } catch (RuntimeException e) {
            log.warn("Failed to record audit entry for {} {}", entityType, entityId, e);
        }
    }

    @Transactional(readOnly = true)
    public java.util.List<AuditLog> forEntity(UUID tenantId, String entityType, UUID entityId) {
        return repository.findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
                tenantId, entityType, entityId);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<AuditLog> forTenant(
            UUID tenantId, org.springframework.data.domain.Pageable pageable) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
    }

    private static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserPrincipal up ? up.userId() : null;
    }

    private static void captureRequestMeta(AuditLog entry) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            entry.setIpAddress(attrs.getRequest().getRemoteAddr());
            String agent = attrs.getRequest().getHeader("User-Agent");
            if (agent != null) {
                entry.setUserAgent(agent.length() > 500 ? agent.substring(0, 500) : agent);
            }
        }
    }
}
