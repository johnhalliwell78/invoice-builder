package com.invoicebuilder.invoice.dto;

import com.invoicebuilder.common.Address;
import com.invoicebuilder.customer.Customer;
import com.invoicebuilder.invoice.DocType;
import com.invoicebuilder.invoice.Invoice;
import com.invoicebuilder.invoice.InvoiceStatus;
import com.invoicebuilder.tenant.Tenant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Customer-facing read-only view of an invoice. Excludes any tenant id /
 * user id surfaces; only renders what an external recipient should see.
 */
public record PublicInvoiceResponse(
        Issuer issuer,
        Recipient recipient,
        String invoiceNumber,
        DocType docType,
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
        List<LineItemResponse> lineItems
) {

    public record Issuer(String name, Address address, String taxId) {
    }

    public record Recipient(String name, Address address) {
    }

    public static PublicInvoiceResponse of(Invoice invoice, Tenant tenant, Customer customer) {
        return new PublicInvoiceResponse(
                new Issuer(tenant.getName(), tenant.getAddress(), tenant.getTaxId()),
                new Recipient(customer.getName(), customer.getAddress()),
                invoice.getInvoiceNumber(),
                invoice.getDocType(),
                invoice.getStatus(),
                invoice.getCurrency(),
                invoice.getSubtotal(),
                invoice.getTaxTotal(),
                invoice.getDiscountAmount(),
                invoice.getTotal(),
                invoice.getAmountPaid(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getNotes(),
                invoice.getTerms(),
                invoice.getLineItems().stream().map(LineItemResponse::from).toList()
        );
    }
}
