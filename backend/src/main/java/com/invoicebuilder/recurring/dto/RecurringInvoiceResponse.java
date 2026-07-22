package com.invoicebuilder.recurring.dto;

import com.invoicebuilder.recurring.Frequency;
import com.invoicebuilder.recurring.RecurringInvoice;
import com.invoicebuilder.recurring.RecurringLineItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RecurringInvoiceResponse(
        UUID id,
        UUID customerId,
        String customerName,
        Frequency frequency,
        LocalDate nextRun,
        boolean active,
        boolean autoSend,
        String currency,
        String template,
        int dueDays,
        BigDecimal discountAmount,
        List<RecurringLineItem> lineItems,
        OffsetDateTime createdAt
) {

    public static RecurringInvoiceResponse from(RecurringInvoice r, String customerName) {
        return new RecurringInvoiceResponse(
                r.getId(),
                r.getCustomerId(),
                customerName,
                r.getFrequency(),
                r.getNextRun(),
                r.isActive(),
                r.isAutoSend(),
                r.getCurrency(),
                r.getTemplate(),
                r.getDueDays(),
                r.getDiscountAmount(),
                r.getLineItems(),
                r.getCreatedAt()
        );
    }
}
