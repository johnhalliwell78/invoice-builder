package com.invoicebuilder.invoice.dto;

import com.invoicebuilder.invoice.InvoiceLineItem;

import java.math.BigDecimal;
import java.util.UUID;

public record LineItemResponse(
        UUID id,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal taxRate,
        BigDecimal discountPercent,
        BigDecimal amount,
        int sortOrder
) {

    public static LineItemResponse from(InvoiceLineItem item) {
        return new LineItemResponse(
                item.getId(),
                item.getDescription(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTaxRate(),
                item.getDiscountPercent(),
                item.getAmount(),
                item.getSortOrder()
        );
    }
}
