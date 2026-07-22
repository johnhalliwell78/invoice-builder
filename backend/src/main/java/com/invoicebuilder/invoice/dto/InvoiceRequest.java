package com.invoicebuilder.invoice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.invoicebuilder.invoice.DocType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceRequest(
        @NotNull UUID customerId,

        @Size(min = 3, max = 3) String currency,

        @NotNull LocalDate issueDate,

        @NotNull LocalDate dueDate,

        @NotEmpty(message = "Invoice must have at least one line item")
        @Valid
        List<LineItemRequest> lineItems,

        @DecimalMin("0.00")
        @Digits(integer = 13, fraction = 2)
        BigDecimal discountAmount,

        @Size(max = 4000) String notes,

        @Size(max = 4000) String terms,

        @Size(max = 50) String template,

        /** Null means INVOICE; set to ESTIMATE to create a quote. */
        DocType docType
) {
}
