package com.invoicebuilder.payment.dto;

import com.invoicebuilder.payment.Payment;
import com.invoicebuilder.payment.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        BigDecimal amount,
        PaymentMethod method,
        LocalDate paidOn,
        String note,
        OffsetDateTime createdAt
) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getAmount(),
                p.getMethod(),
                p.getPaidOn(),
                p.getNote(),
                p.getCreatedAt()
        );
    }
}
