package com.invoicebuilder.audit.dto;

import com.invoicebuilder.audit.AuditAction;
import com.invoicebuilder.audit.AuditLog;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        Long id,
        UUID userId,
        String entityType,
        UUID entityId,
        AuditAction action,
        Map<String, Object> changes,
        OffsetDateTime createdAt
) {

    public static AuditLogResponse from(AuditLog entry) {
        return new AuditLogResponse(
                entry.getId(),
                entry.getUserId(),
                entry.getEntityType(),
                entry.getEntityId(),
                entry.getAction(),
                entry.getChanges(),
                entry.getCreatedAt()
        );
    }
}
