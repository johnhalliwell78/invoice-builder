package com.invoicebuilder.invoice.dto;

import com.invoicebuilder.invoice.Invoice;
import com.invoicebuilder.invoice.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        UUID customerId,
        String invoiceNumber,
        InvoiceStatus status,
        String currency,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal discountAmount,
        BigDecimal total,
        BigDecimal amountPaid,
        LocalDate issueDate,
        LocalDate dueDate,
        String notes,
        String terms,
        String template,
        String publicToken,
        OffsetDateTime sentAt,
        OffsetDateTime viewedAt,
        OffsetDateTime paidAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<LineItemResponse> lineItems
) {

    public static InvoiceResponse from(Invoice i) {
        return new InvoiceResponse(
                i.getId(),
                i.getCustomerId(),
                i.getInvoiceNumber(),
                i.getStatus(),
                i.getCurrency(),
                i.getSubtotal(),
                i.getTaxTotal(),
                i.getDiscountAmount(),
                i.getTotal(),
                i.getAmountPaid(),
                i.getIssueDate(),
                i.getDueDate(),
                i.getNotes(),
                i.getTerms(),
                i.getTemplate(),
                i.getPublicToken(),
                i.getSentAt(),
                i.getViewedAt(),
                i.getPaidAt(),
                i.getCreatedAt(),
                i.getUpdatedAt(),
                i.getLineItems().stream().map(LineItemResponse::from).toList()
        );
    }
}
