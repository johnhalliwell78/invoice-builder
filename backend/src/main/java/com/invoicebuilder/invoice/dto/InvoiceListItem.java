package com.invoicebuilder.invoice.dto;

import com.invoicebuilder.invoice.DocType;
import com.invoicebuilder.invoice.Invoice;
import com.invoicebuilder.invoice.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Slim invoice projection for list views — no line items, no notes/terms.
 */
public record InvoiceListItem(
        UUID id,
        UUID customerId,
        String customerName,
        String invoiceNumber,
        DocType docType,
        InvoiceStatus status,
        String currency,
        BigDecimal total,
        BigDecimal amountPaid,
        LocalDate issueDate,
        LocalDate dueDate
) {

    public static InvoiceListItem from(Invoice i, String customerName) {
        return new InvoiceListItem(
                i.getId(),
                i.getCustomerId(),
                customerName,
                i.getInvoiceNumber(),
                i.getDocType(),
                i.getStatus(),
                i.getCurrency(),
                i.getTotal(),
                i.getAmountPaid(),
                i.getIssueDate(),
                i.getDueDate()
        );
    }
}
