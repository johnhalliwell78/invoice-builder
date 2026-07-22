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
    CANCELLED,
    APPROVED,
    DECLINED;

    /**
     * Allowed transitions for real invoices. PAID and CANCELLED are terminal
     * (absent from the map). VIEWED is reached only via the public view
     * endpoint; SENT→OVERDUE happens via a scheduled job. Manual actions
     * (send / mark-paid / cancel) cover the rest.
     */
    private static final Map<InvoiceStatus, Set<InvoiceStatus>> INVOICE_ALLOWED = Map.of(
            DRAFT, EnumSet.of(SENT),
            SENT, EnumSet.of(VIEWED, PAID, CANCELLED, OVERDUE),
            VIEWED, EnumSet.of(PAID, CANCELLED, OVERDUE),
            OVERDUE, EnumSet.of(PAID, CANCELLED)
    );

    /**
     * Allowed transitions for estimates: DRAFT→SENT→(VIEWED)→APPROVED|DECLINED,
     * cancellable while open. APPROVED and DECLINED are terminal; estimates
     * never become PAID or OVERDUE.
     */
    private static final Map<InvoiceStatus, Set<InvoiceStatus>> ESTIMATE_ALLOWED = Map.of(
            DRAFT, EnumSet.of(SENT),
            SENT, EnumSet.of(VIEWED, APPROVED, DECLINED, CANCELLED),
            VIEWED, EnumSet.of(APPROVED, DECLINED, CANCELLED)
    );

    public boolean canTransitionTo(DocType docType, InvoiceStatus target) {
        Map<InvoiceStatus, Set<InvoiceStatus>> allowed =
                docType == DocType.ESTIMATE ? ESTIMATE_ALLOWED : INVOICE_ALLOWED;
        return allowed.getOrDefault(this, Set.of()).contains(target);
    }

    public void requireTransition(DocType docType, InvoiceStatus target) {
        if (!canTransitionTo(docType, target)) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Cannot transition %s from %s to %s".formatted(
                            docType.name().toLowerCase(), this, target));
        }
    }

    public boolean isEditable() {
        return this == DRAFT;
    }

    public boolean isTerminal() {
        return this == PAID || this == CANCELLED || this == APPROVED || this == DECLINED;
    }
}
