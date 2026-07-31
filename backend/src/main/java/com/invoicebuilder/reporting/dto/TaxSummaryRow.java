package com.invoicebuilder.reporting.dto;

import java.math.BigDecimal;

/** One tax bracket's totals for a period, in one currency. */
public record TaxSummaryRow(
        String currency,
        BigDecimal taxRate,
        BigDecimal net,
        BigDecimal tax,
        BigDecimal gross
) {
}
