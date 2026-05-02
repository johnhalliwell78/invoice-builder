package com.invoicebuilder.invoice;

import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum InvoiceStatus {
    DRAFT,
    SENT,
    VIEWED,
    PAID,
    OVERDUE,
    CANCELLED;

    /**
     * Allowed status transitions, keyed by current status. PAID and CANCELLED
     * are terminal (absent from the map). VIEWED is reached only via the public
     * view endpoint; SENT_TO_OVERDUE happens via a scheduled job. Manual
     * actions (send / mark-paid / cancel) cover the rest.
     */
    private static final Map<InvoiceStatus, Set<InvoiceStatus>> ALLOWED = Map.of(
            DRAFT, EnumSet.of(SENT),
            SENT, EnumSet.of(VIEWED, PAID, CANCELLED, OVERDUE),
            VIEWED, EnumSet.of(PAID, CANCELLED, OVERDUE),
            OVERDUE, EnumSet.of(PAID, CANCELLED)
    );

    public boolean canTransitionTo(InvoiceStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public void requireTransition(InvoiceStatus target) {
        if (!canTransitionTo(target)) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Cannot transition invoice from %s to %s".formatted(this, target));
        }
    }

    public boolean isEditable() {
        return this == DRAFT;
    }

    public boolean isTerminal() {
        return this == PAID || this == CANCELLED;
    }
}
