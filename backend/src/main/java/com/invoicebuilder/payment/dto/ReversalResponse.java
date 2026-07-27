package com.invoicebuilder.payment.dto;

import com.invoicebuilder.payment.PaymentReversal;
import com.invoicebuilder.payment.ReversalReason;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReversalResponse(
        UUID id,
        UUID paymentId,
        BigDecimal amount,
        ReversalReason reason,
        String note,
        OffsetDateTime createdAt
) {

    public static ReversalResponse from(PaymentReversal r) {
        return new ReversalResponse(r.getId(), r.getPaymentId(), r.getAmount(),
                r.getReason(), r.getNote(), r.getCreatedAt());
    }
}
