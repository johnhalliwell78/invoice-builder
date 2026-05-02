package com.invoicebuilder.tenant;

import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;

import java.util.Optional;
import java.util.UUID;

/**
 * ThreadLocal holder for the tenant id of the current request.
 * Populated by the JWT authentication filter and cleared on request completion.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static Optional<UUID> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static UUID require() {
        UUID id = CURRENT.get();
        if (id == null) {
            throw new AppException(ErrorCode.AUTHENTICATION_REQUIRED, "No tenant in context");
        }
        return id;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
