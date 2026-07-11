package com.invoicebuilder.dashboard.dto;

import com.invoicebuilder.invoice.InvoiceStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DashboardResponse(
        List<CurrencyTile> tiles,
        Map<InvoiceStatus, Long> statusCounts,
        List<MonthAmount> revenueByMonth,
        List<MonthCount> customersByMonth,
        List<RecentInvoice> recentInvoices
) {

    /** Monetary aggregates are never summed across currencies — one tile per currency. */
    public record CurrencyTile(
            String currency,
            BigDecimal outstanding,
            BigDecimal overdue,
            BigDecimal paidThisMonth
    ) {
    }

    public record MonthAmount(String month, String currency, BigDecimal total) {
    }

    public record MonthCount(String month, long count) {
    }

    public record RecentInvoice(
            UUID id,
            String invoiceNumber,
            InvoiceStatus status,
            BigDecimal total,
            String currency,
            String customerName,
            OffsetDateTime updatedAt
    ) {
    }
}
