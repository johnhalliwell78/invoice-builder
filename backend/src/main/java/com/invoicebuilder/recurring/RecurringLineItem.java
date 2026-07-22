package com.invoicebuilder.recurring;

import java.math.BigDecimal;

/** JSONB snapshot of one line item, decoupled from the invoice entity. */
public record RecurringLineItem(
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal taxRate,
        BigDecimal discountPercent
) {
}
